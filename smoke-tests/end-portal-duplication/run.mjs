import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {mkdirSync, writeFileSync, copyFileSync, readFileSync, existsSync, readdirSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, inputJar] = process.argv.slice(2);
if (!java || !inputJar) throw Error('Usage: node run.mjs <java25> <server.jar>');
const jar = path.resolve(inputJar);
const mode = process.env.TESSERA_SMOKE_MODE ?? 'end-portal-duplication';
if (!['end-portal-duplication', 'end-portal-flight'].includes(mode)) throw Error('Unsupported mode');
const flight = mode === 'end-portal-flight';
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
for (const enabled of flight ? [true] : [false, true]) {
  const work = path.join(repo, 'build', `${flight ? 'end-portal-flight' : 'end-portal-dupe'}-${enabled}-${Date.now()}`);
  mkdirSync(path.join(work, 'plugins'), {recursive: true});
  mkdirSync(path.join(work, 'config'));
  writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated sand dupe fixture; no production data.\n');
  writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
  const listener = net.createServer();
  await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
  const port = listener.address().port;
  await new Promise(resolve => listener.close(resolve));
  const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [], layers: [{block: 'minecraft:bedrock', height: 1}]});
  writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\n`);
  writeFileSync(path.join(work, 'config/paper-global.yml'), `_version: 31\nunsupported-settings:\n  allow-unsafe-end-portal-teleportation: ${enabled}\n`);
  copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
  for (const candidate of readdirSync(path.join(repo, 'build'), {withFileTypes: true}).filter(d => d.isDirectory())) {
    const cache = path.join(repo, 'build', candidate.name, 'cache');
    if (!existsSync(cache)) continue;
    const jars = readdirSync(cache).filter(n => n.endsWith('.jar'));
    if (!jars.length) continue;
    mkdirSync(path.join(work, 'cache'));
    for (const file of jars) copyFileSync(path.join(cache, file), path.join(work, 'cache', file));
    break;
  }
  console.log('END_PORTAL_DUPLICATION_FIXTURE', work, enabled, sha256);
  const server = spawn(java, ['-Xms512M', '-Xmx2G', '-jar', jar, '--nogui'], {cwd: work, windowsHide: true,
    env: {...process.env, TESSERA_SMOKE_MODE: mode, TESSERA_DUPE_ENABLED: String(enabled)}, stdio: ['pipe', 'pipe', 'pipe']});
  let log = '', timedOut = false;
  for (const stream of [server.stdout, server.stderr]) stream.on('data', chunk => { log += chunk; process.stdout.write(chunk); });
  const timeout = setTimeout(() => { timedOut = true; server.stdin.write('minecraft:stop\n'); }, 240000);
  const kill = setTimeout(() => { if (server.exitCode === null) server.kill(); }, 285000);
  const code = await new Promise((resolve, reject) => { server.once('exit', resolve); server.once('error', reject); });
  clearTimeout(timeout); clearTimeout(kill);
  const checksPath = path.join(work, `${mode}-checks.txt`);
  const checks = existsSync(checksPath) ? readFileSync(checksPath, 'utf8') : null;
  const completedCases = new Set(checks?.split('\n').map(line => line.split(':')[0]) ?? []);
  const expectedCases = flight ? ['sand', 'red_sand', 'gravel', 'white_concrete_powder', 'anvil'].flatMap(material => ['east', 'west', 'south', 'north'].map(direction => `${material}-${direction}`)) : [...Array.from({length: 20}, (_, i) => `landing-${i}`),
    'airborne', 'veto', 'placement-veto', 'redirect', 'retire', 'enter-veto', 'continuation-retire', 'drop', 'drop-veto',
    ...Array.from({length: 5}, (_, i) => `return-${i}`),
    ...['entry', 'return'].flatMap(direction => [0, 1].flatMap(region => ['landing', 'airborne', 'drop'].map(mode => `egg-${mode}-${direction}-${region}`)))];
  const passed = code === 0 && !timedOut && log.includes(flight ? 'END_PORTAL_FLIGHT_PASS cases=20' : `END_PORTAL_DUPLICATION_PASS enabled=${enabled} cases=${expectedCases.length}`)
    && completedCases.size === expectedCases.length && expectedCases.every(id => completedCases.has(id))
    && !/END_PORTAL_(DUPLICATION|FLIGHT)_FAIL|failed to tick:|Thread failed main thread check|ConcurrentModificationException/.test(log);
  writeFileSync(path.join(work, 'runner.log'), log);
  writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, sha256, mode, enabled, work, port, passed, code, timedOut, checks}, null, 2));
  console.log('END_PORTAL_DUPLICATION_RESULT', passed, path.join(work, 'result.json'));
  if (!passed) throw Error('End portal fixture failed: ' + work);
}
