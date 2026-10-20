import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, inputJar] = process.argv.slice(2);
const baseline = process.argv.includes('--expect-bug');
if (!java || !inputJar) throw Error('Usage: node run.mjs <java25> <server.jar> [--expect-bug]');
const jar = path.resolve(inputJar);
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `native-navigation-${baseline ? 'baseline-' : ''}${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
const fixtureJar = path.join(work, 'server.jar');
copyFileSync(jar, fixtureJar);
if (createHash('sha256').update(readFileSync(fixtureJar)).digest('hex') !== sha256) throw Error('Fixture JAR hash mismatch');
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated navigation regression; no production saves.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const reservation = net.createServer();
await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve); });
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\ngamemode=creative\ndifficulty=peaceful\nnetwork-compression-threshold=256\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
writeFileSync(path.join(work, 'config/paper-global.yml'), '_version: 31\nthreaded-regions:\n  threads: 4\nspark:\n  enabled: false\n');
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
const vanilla = path.join(repo, 'folia-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar');
if (!existsSync(vanilla) || createHash('sha256').update(readFileSync(vanilla)).digest('hex') !== 'd052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64') throw Error('Verified Mojang 26.3 bundler unavailable');
mkdirSync(path.join(work, 'cache'));
copyFileSync(vanilla, path.join(work, 'cache/mojang_26.3.jar'));
console.log('NAV_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-jar', fixtureJar, '--nogui'],
  {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'native-navigation', TESSERA_NAV_EXPECT_BUG: String(baseline)}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, clientFailure = null, timedOut = false;
const observations = [];
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!clientsStarted && log.includes('NAV_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all(['NavAlpha', 'NavBeta'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('NAV_PASS') && !log.includes('NAV_BASELINE_BUG_REPRODUCED')) observations.push(event); },
      {observeKeepAlive: true})))
      .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 240000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 285000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
const checks = existsSync(path.join(work, 'navigation-checks.txt')) ? readFileSync(path.join(work, 'navigation-checks.txt'), 'utf8') : '';
const joins = observations.filter(e => e.type === 'login').length;
const kicks = observations.filter(e => e.type === 'kicked').length;
const disconnects = observations.filter(e => e.type === 'end').length;
const bugReproduced = log.includes('NAV_BASELINE_BUG_REPRODUCED') && log.includes('Cannot asynchronously load chunks') && log.includes('Entity threw exception');
const fixed = log.includes('NAV_PASS') && [
  'reported unloaded stale path is cleared without discarding horse',
  'first parallel foreign loaded path: cleared before any ground read',
  'second parallel foreign loaded path: cleared before any ground read',
  'foreign waypoint after native advancement: cleared before any ground read',
  'owned unloaded path: cleared before any ground read',
  'native recomputation cache omits foreign loaded chunk',
  'mounted horse transfers across native regions',
  'horse transfers to another dimension',
  'valid local navigation retains Vanilla ground read and live path',
  'both riders remain connected after natural entity ticks',
].every(check => checks.includes(check));
const passed = code === 0 && !timedOut && !clientFailure && joins === 2 && kicks === 0 && disconnects === 0
  && !log.includes('NAV_FAIL') && (baseline ? bugReproduced : fixed && !/Entity threw exception|Thread failed main thread check|failed to tick:|Uncaught exception|Terminal has been closed/.test(log));
writeFileSync(path.join(work, 'runner.log'), log);
writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, fixtureJar, sha256, work, port, code, passed, baseline, bugReproduced,
  timedOut, clientFailure, joins, kicks, disconnects, checks, observations}, null, 2));
console.log('NAV_RESULT', passed, path.join(work, 'result.json'));
if (!passed) process.exitCode = 1;
