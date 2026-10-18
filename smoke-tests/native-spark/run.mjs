import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, inputJar] = process.argv.slice(2);
const disabled = process.argv.includes('--disabled');
const external = process.argv.includes('--external-probe');
const early = process.argv.includes('--early');
if (!java || !inputJar) throw Error('Usage: node run.mjs <java25> <server.jar>');
const jar = path.resolve(inputJar);
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `native-spark-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
const fixtureJar = path.join(work, 'server.jar');
copyFileSync(jar, fixtureJar);
if (createHash('sha256').update(readFileSync(fixtureJar)).digest('hex') !== sha256) throw Error('Fixture JAR differs from requested artifact');
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated loopback spark tests. No production saves.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const reservation = net.createServer();
await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve); });
const port = reservation.address().port;
await new Promise(resolve => reservation.close(resolve));
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\ngamemode=creative\nnetwork-compression-threshold=256\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
writeFileSync(path.join(work, 'config/paper-global.yml'), `_version: 31\nthreaded-regions:\n  threads: 4\nspark:\n  enabled: ${!disabled}\n  enable-immediately: ${early}\n`);
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
if (external) copyFileSync(path.join(repo, 'test-plugin/build/libs/spark-preference-probe.jar'), path.join(work, 'plugins/spark-probe.jar'));
const vanilla = path.join(repo, 'folia-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar');
if (!existsSync(vanilla) || createHash('sha256').update(readFileSync(vanilla)).digest('hex') !== 'd052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64') throw Error('Verified Mojang 26.3 bundler unavailable');
mkdirSync(path.join(work, 'cache'));
copyFileSync(vanilla, path.join(work, 'cache/mojang_26.3.jar'));
console.log('SPARK_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', ...(early ? ['-Dpaper.preferSparkPlugin=false'] : []), '-Xms512M', '-Xmx3G', '-jar', fixtureJar, '--nogui'],
  {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'native-spark', TESSERA_SPARK_EXPECT_DISABLED: String(disabled), TESSERA_SPARK_EXTERNAL_PROBE: String(external)}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, clientFailure = null, timedOut = false, frozen = false, networkCommandSent = false;
const observations = [];
const clients = new Map();
function output(chunk) {
  const part = chunk.toString();
  log += part; process.stdout.write(chunk);
  if (part.includes('SPARK_FROZEN_PHASE')) frozen = true;
  if (part.includes('SPARK_UNFROZEN_PHASE')) frozen = false;
  if (frozen && !networkCommandSent && clients.has('SparkAlpha')) {
    networkCommandSent = true;
    clients.get('SparkAlpha').command('spark profiler info');
  }
  if (!clientsStarted && log.includes('SPARK_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all(['SparkAlpha', 'SparkBeta'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('SPARK_PASS')) observations.push({...event, frozen}); },
      {observeChat: true, observeKeepAlive: true, onPlay: controls => clients.set(name, controls)})))
      .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 240000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 285000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
const checks = existsSync(path.join(work, 'spark-checks.txt')) ? readFileSync(path.join(work, 'spark-checks.txt'), 'utf8') : '';
const joins = observations.filter(event => event.type === 'login').length;
const kicks = observations.filter(event => event.type === 'kicked').length;
const disconnects = observations.filter(event => event.type === 'end').length;
const frozenReplies = observations.filter(event => event.type === 'system_chat' && event.username === 'SparkAlpha' && event.frozen && event.raw.toLowerCase().includes('profiler')).length;
const frozenKeepalives = Object.fromEntries(['SparkAlpha', 'SparkBeta'].map(name => [name,
  observations.filter(event => event.type === 'keepalive' && event.state === 'play' && event.username === name && event.frozen).length]));
const profiles = existsSync(path.join(work, 'plugins/spark')) ? readdirSync(path.join(work, 'plugins/spark')).filter(file => file.endsWith('.sparkprofile')) : [];
const enabledPassed = log.includes('SPARK_PASS')
  && joins === 2 && kicks === 0 && disconnects === 0 && frozenReplies >= 1 && profiles.length === 2
  && Object.values(frozenKeepalives).every(count => count >= 1)
  && networkCommandSent && !observations.some(event => event.type === 'system_chat' && event.raw.includes('command.unknown'))
  && ['clients on distinct native regions', 'all-thread profile contains Netty samples', 'all-thread profile contains multiple tick workers',
    'default recording selects dynamic tick workers only', 'dimension transfer completed during recording and freeze',
    'saved protobuf contains actual stack samples', 'player identity retained in recording metadata'].every(check => checks.includes(check))
;
const disabledPassed = log.includes('SPARK_DISABLED_PASS') && log.includes('The spark profiler is currently disabled.') && profiles.length === 0;
const externalPassed = log.includes('SPARK_DISABLED_PASS') && log.includes('SPARK_EXTERNAL_PROBE_COMMAND') && profiles.length === 0;
const passed = code === 0 && !timedOut && !clientFailure && (external ? externalPassed : disabled ? disabledPassed : enabledPassed)
  && !/SPARK_FAIL|failed to tick:|Thread failed main thread check|ConcurrentModificationException|An exception was encountered|Uncaught exception|Exception occurred while getting world info|Terminal has been closed/.test(log);
writeFileSync(path.join(work, 'runner.log'), log);
writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, fixtureJar, sha256, work, port, code, passed, disabled, external, early, timedOut, clientFailure,
  joins, kicks, disconnects, frozenReplies, frozenKeepalives, networkCommandSent, profiles,
  profileHashes: Object.fromEntries(profiles.map(file => [file, createHash('sha256').update(readFileSync(path.join(work, 'plugins/spark', file))).digest('hex')])),
  checks, observations}, null, 2));
console.log('SPARK_RESULT', passed, path.join(work, 'result.json'));
if (!passed) process.exitCode = 1;
