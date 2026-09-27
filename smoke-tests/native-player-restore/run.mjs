import {spawn} from 'node:child_process';
import net from 'node:net';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {mkdirSync, writeFileSync, copyFileSync, readFileSync, existsSync, readdirSync} from 'node:fs';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const java = process.argv[2];
const jar = path.resolve(process.argv[3] ?? path.join(repo, 'build/libs/tessera-server-26.3.build.010-alpha.jar'));
if (!java || !existsSync(jar)) throw Error('Usage: node run.mjs <Java 25 executable> <server.jar>');
const jarSha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
// Reserve an OS-selected loopback port; never stop another server to obtain it.
const listener = net.createServer();
await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
const port = listener.address().port;
await new Promise(resolve => listener.close(resolve));
const work = path.join(repo, 'build', `native-restore-smoke-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Owned native restore fixture; no existing worlds copied.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
for (const candidate of readdirSync(path.join(repo, 'build'), {withFileTypes: true}).filter(d => d.isDirectory())) {
  const cache = path.join(repo, 'build', candidate.name, 'cache');
  if (path.join(repo, 'build', candidate.name) === work || !existsSync(cache)) continue;
  const jars = readdirSync(cache).filter(n => n.endsWith('.jar'));
  if (!jars.length) continue;
  mkdirSync(path.join(work, 'cache'), {recursive: true});
  for (const name of jars) copyFileSync(path.join(cache, name), path.join(work, 'cache', name));
  break;
}
console.log('NATIVE_RESTORE_WORK', work, 'PORT', port, 'JAR', jar);
const child = spawn(java, ['-Xms512M', '-Xmx2G', '-jar', jar, '--nogui'], {
  cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'native-restore'}, stdio: ['pipe', 'pipe', 'pipe']
});
let log = '', started = false, passed = false, failed = false, stopping = false;
const events = [];
function stop() { if (!stopping) { stopping = true; child.stdin.write('stop\n'); } }
function fail(error) { if (!failed) console.error(error); failed = true; stop(); }
const deadline = setTimeout(() => fail(Error('Native restore fixture deadline exceeded')), 360000);
const forceStop = setTimeout(() => { if (child.exitCode === null) { failed = true; child.kill(); } }, 405000);
function output(data) {
  const text = data.toString(); log += text; process.stdout.write(text);
  if (!started && log.includes('NATIVE_RESTORE_READY') && log.includes('For help, type "help"')) {
    started = true;
    Promise.all(['RestoreOne', 'RestoreTwo'].map(name => connectPlayer(repo, port, name, event => {
      events.push(event);
      if (!stopping && ['kicked', 'end'].includes(event.type)) fail(Error('Unexpected client disconnect: ' + name));
    }))).catch(fail);
  }
  if (!passed && log.includes('NATIVE_RESTORE_COMPONENTS_PASS')) {
    passed = true;
    const clients = ['RestoreOne', 'RestoreTwo'].map(username => ({username,
      logins: events.filter(e => e.username === username && e.type === 'login').length,
      kicks: events.filter(e => e.username === username && e.type === 'kicked').length,
      disconnects: events.filter(e => e.username === username && e.type === 'end').length,
      teleports: events.filter(e => e.username === username && e.type === 'teleport').length
      ,statsResets: events.filter(e => e.username === username && e.type === 'stats' && e.zeros > 0).length
      ,advancementResets: events.filter(e => e.username === username && e.type === 'advancements' && e.reset).length
    }));
    if (clients.some(c => c.logins !== 1 || c.kicks !== 0 || c.disconnects !== 0 || c.teleports < 3 || c.statsResets < 2 || c.advancementResets < 2)) fail(Error('Connection/client reset counter assertion failed'));
    writeFileSync(path.join(work, 'native-restore-result.json'), JSON.stringify({jar, jarSha256, port, clients,
      nativeComponentChecksPassed: !failed, publicTransactionAccepted: false, contractVersion: 0,
      scope: readFileSync(path.join(work, 'native-restore-evidence/checks.txt'), 'utf8'), events}, null, 2));
    stop();
  }
  if (log.includes('NATIVE_RESTORE_COMPONENTS_FAIL')) fail(Error('Native component assertion failed'));
}
child.stdout.on('data', output); child.stderr.on('data', output);
child.on('error', fail);
child.on('exit', code => {
  clearTimeout(deadline); clearTimeout(forceStop);
  writeFileSync(path.join(work, 'runner.log'), log);
  if (code !== 0 || !passed || failed) process.exitCode = 1;
  console.log('NATIVE_RESTORE_EXIT', code, 'componentsPassed', passed && !failed, 'publicTransactionAccepted', false);
});
