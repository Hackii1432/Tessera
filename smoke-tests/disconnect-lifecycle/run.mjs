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
const jar = path.resolve(inputJar), sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `disconnect-lifecycle-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Loopback-only native disconnect acceptance; no production data.\n');
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

console.log('DISCONNECT_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-Dpaper.playerconnection.keepalive=3',
  '--enable-native-access=ALL-UNNAMED', '-jar', jar, '--nogui'],
{cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'disconnect-lifecycle'}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, configStarted = false, gameTimeout = false, configTimeout = false;
let normalHandle, normalClosed = false, configRejected = false, clientFailure = null, timedOut = false;
const observations = [];
function fail(error) {
  clientFailure ??= String(error);
  stop();
}
function stop() {
  if (!child.stdin.destroyed && !child.stdin.writableEnded && child.exitCode === null) child.stdin.write('stop\n');
}
child.stdin.on('error', error => { clientFailure ??= String(error); });
const observe = event => { observations.push({...event, observedAt: new Date().toISOString()}); };
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!clientsStarted && log.includes('DISCONNECT_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all([
      connectPlayer(repo, port, 'DisconnectOne', observe, {observeKeepAlive: true}),
      connectPlayer(repo, port, 'DisconnectTwo', observe, {observeKeepAlive: true, shouldAnswerKeepAlive: () => !gameTimeout}),
      connectPlayer(repo, port, 'NormalQuit', observe, {observeKeepAlive: true, onPlay: handle => { normalHandle = handle; }})
    ]).catch(fail);
  }
  if (log.includes('DISCONNECT_ARM_GAME_TIMEOUT')) gameTimeout = true;
  if (!configStarted && log.includes('DISCONNECT_READY_CONFIG')) {
    configStarted = true;
    connectPlayer(repo, port, 'ConfigDrop', observe, {observeKeepAlive: true, shouldSelectKnownPacks: () => false, shouldFinishConfiguration: () => false,
      shouldAnswerKeepAlive: () => !configTimeout}).then(() => fail(Error('Configuration fixture unexpectedly entered play')))
      .catch(error => {
        if (observations.some(event => event.username === 'ConfigDrop' && event.type === 'kicked')) configRejected = true;
        else fail(error);
      });
  }
  if (log.includes('DISCONNECT_ARM_CONFIG_TIMEOUT')) configTimeout = true;
  if (!normalClosed && log.includes('DISCONNECT_NORMAL_QUIT')) {
    normalClosed = true;
    if (!normalHandle) fail(Error('Normal client control unavailable'));
    else normalHandle.disconnect();
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; stop(); }, 180000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 210000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
writeFileSync(path.join(work, 'server-run.log'), log);
const checks = existsSync(path.join(work, 'disconnect-checks.txt')) ? readFileSync(path.join(work, 'disconnect-checks.txt'), 'utf8') : '';
const counters = ['DisconnectOne', 'DisconnectTwo', 'NormalQuit', 'ConfigDrop'].map(username => ({username,
  logins: observations.filter(event => event.username === username && event.type === 'login').length,
  kicks: observations.filter(event => event.username === username && event.type === 'kicked').length,
  disconnects: observations.filter(event => event.username === username && event.type === 'end').length,
  keepalives: observations.filter(event => event.username === username && event.type === 'keepalive').length}));
const timeoutLogs = [...log.matchAll(/(?:\]: |INFO\]: )(\w+) was kicked due to keepalive timeout!/g)].map(match => match[1]);
const expected = ['two distinct native region owners', 'cancelled kick kept connection active and readable',
  'one cancelled and one accepted kick event, no timeout retries', 'accepted reason preserved through close',
  'exactly one native timeout event', 'global configuration timeout written exactly once',
  'configuration connection removed by global cleanup',
  'normal client close did not trigger any kick event', 'all native gameplay connections removed'];
const passed = code === 0 && !timedOut && clientFailure === null && configRejected && log.includes('DISCONNECT_PASS')
  && counters.every(client => client.logins === 1 && client.disconnects === 1 && client.kicks === (client.username === 'NormalQuit' ? 0 : 1))
  && counters.find(client => client.username === 'NormalQuit').keepalives >= 3
  && timeoutLogs.length === 2 && timeoutLogs.includes('DisconnectTwo') && timeoutLogs.includes('ConfigDrop')
  && expected.every(check => checks.includes(check)) && /NormalQuit lost connection: Disconnected/.test(log)
  && !/DISCONNECT_FAIL|has not responded in|failed to tick:|Thread failed main thread check|NetworkException|Terminal has been closed/.test(log);
const result = {passed, jar, sha256, work, port, code, timedOut, clientFailure, configRejected,
  testKeepaliveSeconds: 3, heldDisconnectMilliseconds: 2200, counters, timeoutLogs, checks, observations};
writeFileSync(path.join(work, 'result.json'), JSON.stringify(result, null, 2));
console.log('DISCONNECT_RESULT', passed, path.join(work, 'result.json'));
if (!passed) throw Error('Native disconnect acceptance failed: ' + work);
