import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {mkdirSync, writeFileSync, copyFileSync, readFileSync, existsSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const java = process.argv[2];
const jar = path.resolve(process.argv[3] ?? path.join(repo, 'build/libs/tessera-server-26.3.build.011-beta.jar'));
if (!java || !existsSync(jar)) throw Error('Usage: node run.mjs <java25> <server.jar>');
const mode = process.env.TESSERA_SMOKE_MODE ?? 'paper-beta';
if (!['paper-beta', 'full'].includes(mode)) throw Error('Unsupported smoke mode');
const work = path.join(repo, 'build', `paper-beta-smoke-${mode}-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Owned disposable test worlds; no production saves.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const reservation = net.createServer();
await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve); });
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\n`);
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
console.log('PAPER_BETA_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const server = spawn(java, ['-Xms512M', '-Xmx2G', '-jar', jar, '--nogui'], {
  cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: mode}, stdio: ['pipe', 'pipe', 'pipe']
});
let log = '', timedOut = false;
function output(chunk) { log += chunk.toString(); process.stdout.write(chunk); }
server.stdout.on('data', output);
server.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; server.stdin.write('stop\n'); }, 300000);
const kill = setTimeout(() => { if (server.exitCode === null) server.kill(); }, 345000);
server.on('error', error => { console.error(error); process.exitCode = 1; });
server.on('exit', code => {
  clearTimeout(timeout); clearTimeout(kill);
  const source = mode === 'full' && existsSync(path.join(work, 'tessera-smoke-result.json'))
    ? JSON.parse(readFileSync(path.join(work, 'tessera-smoke-result.json'), 'utf8')) : null;
  const passed = code === 0 && !timedOut
    && (mode === 'full' ? source?.status === 'PASS' : log.includes('PAPER_BETA_PASS'))
    && !/PAPER_BETA_FAIL|failed to tick:|Thread failed main thread check|Cannot .* off-(?:main|region)|ConcurrentModificationException|Watchdog.*stopping server|Unexpected non-transient entity chunk|NBT for entity chunk is unexpectedly/.test(log);
  writeFileSync(path.join(work, 'runner.log'), log);
  writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, sha256, mode, work, port, passed, code, timedOut, source}, null, 2));
  console.log('PAPER_BETA_RESULT', passed, path.join(work, 'result.json'));
  if (!passed) process.exitCode = 1;
});
