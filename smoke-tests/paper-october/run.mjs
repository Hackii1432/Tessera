import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, inputJar] = process.argv.slice(2);
if (!java || !inputJar) throw Error('Usage: node run.mjs <java25> <server.jar>');
const jar = path.resolve(inputJar);
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `paper-october-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated native Paper regression tests. No production saves.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const reservation = net.createServer();
await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve); });
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nenforce-whitelist=false\ngamemode=creative\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
writeFileSync(path.join(work, 'config/paper-global.yml'), '_version: 31\nthreaded-regions:\n  threads: 4\n');
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
for (const entry of readdirSync(path.join(repo, 'build'), {withFileTypes: true})) {
  if (!entry.isDirectory() || entry.name === path.basename(work)) continue;
  const cache = path.join(repo, 'build', entry.name, 'cache');
  if (!existsSync(cache)) continue;
  const files = readdirSync(cache).filter(file => file.endsWith('.jar'));
  if (!files.length) continue;
  mkdirSync(path.join(work, 'cache'));
  for (const file of files) copyFileSync(path.join(cache, file), path.join(work, 'cache', file));
  break;
}
console.log('OCTOBER_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-jar', jar, '--nogui'],
  {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'paper-october'}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, clientFailure = null, timedOut = false;
const observations = [];
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!clientsStarted && log.includes('OCTOBER_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all(['OctoberAlpha', 'OctoberBeta'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('OCTOBER_PASS')) observations.push(event); })))
      .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 300000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 345000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
const checksPath = path.join(work, 'october-checks.txt');
const checks = existsSync(checksPath) ? readFileSync(checksPath, 'utf8') : null;
const joins = observations.filter(event => event.type === 'login').length;
const kicks = observations.filter(event => event.type === 'kicked').length;
const disconnects = observations.filter(event => event.type === 'end').length;
const passed = code === 0 && !timedOut && clientFailure === null && log.includes('OCTOBER_PASS')
  && joins === 2 && kicks === 0 && disconnects === 0
  && ['shelf-reload:', 'parallel-regions:', 'block-snapshots:', 'death-attribution:', 'passenger-spawn:', 'clients:'].every(prefix => checks?.includes(prefix))
  && !/OCTOBER_FAIL|failed to tick:|Thread failed main thread check|ConcurrentModificationException/.test(log);
writeFileSync(path.join(work, 'runner.log'), log);
writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, sha256, work, port, code, passed, timedOut, clientFailure, joins, kicks, disconnects, checks, observations}, null, 2));
console.log('OCTOBER_RESULT', passed, path.join(work, 'result.json'));
if (!passed) process.exitCode = 1;
