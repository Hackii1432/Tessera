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
const work = path.join(repo, 'build', `native-pregeneration-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated native pregeneration acceptance worlds. No production data.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const listener = net.createServer();
await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
const port = listener.address().port;
await new Promise(resolve => listener.close(resolve));
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nenforce-whitelist=false\ngamemode=creative\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
writeFileSync(path.join(work, 'config/paper-global.yml'), '_version: 31\nthreaded-regions:\n  threads: 4\n');
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
// Reuse only launcher download caches, never worlds/player stores from other fixtures.
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
console.log('PREGEN_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const results = [];
for (const mode of ['native-pregeneration', 'native-pregeneration-recovery']) {
  const recovery = mode.endsWith('-recovery');
  const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-jar', jar, '--nogui'],
    {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: mode}, stdio: ['pipe', 'pipe', 'pipe']});
  let log = '', clientsStarted = false, clientFailure = null, timedOut = false;
  const observations = [];
  const passMarker = recovery ? 'PREGEN_RECOVERY_PASS' : 'PREGEN_PASS';
  function output(chunk) {
    log += chunk.toString(); process.stdout.write(chunk);
    if (!clientsStarted && log.includes('PREGEN_READY_CLIENTS') && log.includes('For help, type "help"')) {
      clientsStarted = true;
      Promise.all(['PregenAlpha', 'PregenBeta'].map(name => connectPlayer(repo, port, name,
        event => { if (!log.includes(passMarker)) observations.push(event); })))
        .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
    }
  }
  child.stdout.on('data', output); child.stderr.on('data', output);
  const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 360000);
  const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 420000);
  const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
  clearTimeout(timeout); clearTimeout(kill);
  const checksFile = path.join(work, recovery ? 'pregen-recovery-checks.txt' : 'pregen-checks.txt');
  const checks = existsSync(checksFile) ? readFileSync(checksFile, 'utf8') : null;
  const checkpoints = existsSync(path.join(work, '.tessera/pregeneration'))
    ? readdirSync(path.join(work, '.tessera/pregeneration')).filter(file => file.endsWith('.json'))
      .map(file => JSON.parse(readFileSync(path.join(work, '.tessera/pregeneration', file), 'utf8'))) : [];
  const joins = observations.filter(event => event.type === 'login').length;
  const kicks = observations.filter(event => event.type === 'kicked').length;
  const expected = recovery ? ['shutdown-active-drain:', 'restart-paused:', 'restart-resume-revalidate:', 'saved-full:', 'clients:']
    : ['command-permission-selection:', 'freeze-parallel-dimensions:', 'freeze-ticket-retirement:', 'existing-full-skip:', 'mode-pause-resume-cancel:',
      'custom-world-generator:', 'expected-save-failure:', 'save-failure-resume:', 'expected-checkpoint-failure:',
      'checkpoint-failure-resume:', 'runtime-unload-with-active-job:',
      'snapshot-with-active-job:', 'restart-checkpoint-prepared:', 'shutdown-with-active-job:', 'clients:'];
  const expectedFailure = checks?.match(/expected-save-failure: id=(\d+)/)?.[1];
  const expectedCheckpointFailure = checks?.match(/expected-checkpoint-failure: id=(\d+)/)?.[1];
  const jobFailures = [...log.matchAll(/Pregeneration job #(\d+) failed[^\r\n]*/g)];
  const faultsVerified = recovery ? jobFailures.length === 0
    : jobFailures.length === 2
      && jobFailures.some(failure => failure[1] === expectedFailure && failure[0].includes('saving'))
      && jobFailures.some(failure => failure[1] === expectedCheckpointFailure && failure[0].includes('checkpoint'));
  const passed = code === 0 && !timedOut && clientFailure === null && log.includes(passMarker)
    && joins === 2 && kicks === 0 && expected.every(prefix => checks?.includes(prefix))
    && faultsVerified && !/PREGEN_FAIL|failed to tick:|Thread failed main thread check|ConcurrentModificationException/.test(log);
  const result = {mode, passed, jar, sha256, work, port, code, timedOut, clientFailure, joins, kicks, checks, checkpoints};
  results.push(result);
  writeFileSync(path.join(work, `${mode}.log`), log);
  writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, sha256, work, results}, null, 2));
  console.log('PREGEN_RESULT', mode, passed, path.join(work, 'result.json'));
  if (!passed) throw Error('Native pregeneration acceptance failed: ' + work);
}
