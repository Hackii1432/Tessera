import {spawn} from 'node:child_process';
import {createHash, randomBytes} from 'node:crypto';
import {copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, inputJar] = process.argv.slice(2);
if (!java || !inputJar) throw Error('Usage: node run.mjs <java25> <server.jar>');
const jar = path.resolve(inputJar), sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `whitelist-command-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Isolated whitelist HTTP/dispatcher regression. No production stores.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
async function freePort() {
  const listener = net.createServer();
  await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
  const port = listener.address().port;
  await new Promise(resolve => listener.close(resolve));
  return port;
}
const port = await freePort(), rconPort = await freePort(), password = randomBytes(24).toString('hex');
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nenforce-whitelist=false\ngamemode=creative\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nbroadcast-rcon-to-ops=false\n`);
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
async function rcon(command) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({host: '127.0.0.1', port: rconPort});
    let buffer = Buffer.alloc(0), authenticated = false;
    const send = (id, type, text) => {
      const body = Buffer.from(text, 'utf8'), packet = Buffer.alloc(body.length + 14);
      packet.writeInt32LE(body.length + 10, 0); packet.writeInt32LE(id, 4); packet.writeInt32LE(type, 8);
      body.copy(packet, 12); socket.write(packet);
    };
    socket.setTimeout(20000, () => socket.destroy(Error('RCON did not return the final whitelist response')));
    socket.once('error', reject);
    socket.once('connect', () => send(17, 3, password));
    socket.on('data', chunk => {
      buffer = Buffer.concat([buffer, chunk]);
      while (buffer.length >= 4) {
        const length = buffer.readInt32LE(0);
        if (length < 10 || length > 1048576) { socket.destroy(Error('Invalid RCON frame')); return; }
        if (buffer.length < length + 4) return;
        const frame = buffer.subarray(0, length + 4); buffer = buffer.subarray(length + 4);
        const id = frame.readInt32LE(4), type = frame.readInt32LE(8), response = frame.subarray(12, length + 2).toString('utf8');
        if (!authenticated && type === 2) {
          if (id === -1) { socket.destroy(Error('RCON authentication failed')); return; }
          authenticated = true; send(18, 2, command);
        } else if (authenticated && id === 18 && type === 0) {
          resolve(response); socket.end();
        }
      }
    });
  });
}

console.log('WHITELIST_FIXTURE', work, 'PORT', port, 'RCON_PORT', rconPort, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '--enable-native-access=ALL-UNNAMED', '-jar', jar, '--nogui'],
  {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'whitelist-command'}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, rconStarted = false, clientFailure = null, timedOut = false;
const observations = [], rconChecks = [];
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!clientsStarted && log.includes('WHITELIST_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all(['WhitelistOne', 'WhitelistTwo'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('WHITELIST_PASS')) observations.push(event); })))
      .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
  if (!rconStarted && log.includes('WHITELIST_READY_RCON')) {
    rconStarted = true;
    child.stdin.write('minecraft:whitelist add ConsolePlayer\n');
    (async () => {
      for (const [command, pattern] of [
        ['minecraft:whitelist add RconPlayer', /Added rconplayer/],
        ['whitelist add rconplayer', /already whitelisted/],
        ['whitelist remove RCONPLAYER', /Removed rconplayer/],
        ['execute run whitelist add RconPlayer', /Added rconplayer/],
        ['whitelist add missingplayer', /does not exist/],
        ['whitelist add RconFailure', /profile service unavailable/],
        ['whitelist list', /rconplayer/],
        ['whitelist remove RconPlayer', /Removed rconplayer/]
      ]) {
        const response = await rcon(command);
        if (!pattern.test(response)) throw Error('RCON response mismatch: ' + command + ' => ' + response);
        rconChecks.push({command, response});
      }
      writeFileSync(path.join(work, 'rcon-verified'), 'Eight real socket responses passed.\n');
    })().catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 240000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 285000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
writeFileSync(path.join(work, 'server-run.log'), log);
const checks = existsSync(path.join(work, 'whitelist-checks.txt')) ? readFileSync(path.join(work, 'whitelist-checks.txt'), 'utf8') : '';
const httpRequests = existsSync(path.join(work, 'whitelist-http-requests.txt'))
  ? readFileSync(path.join(work, 'whitelist-http-requests.txt'), 'utf8').trim().split(/\r?\n/) : [];
const joins = observations.filter(event => event.type === 'login').length;
const kicks = observations.filter(event => event.type === 'kicked').length;
const disconnects = observations.filter(event => event.type === 'end').length;
const expected = ['freeze:', 'case:', 'alias:', 'missing:', 'service:', 'slow:', 'timeout:', 'veto:', 'player:', 'permission:', 'disconnect:', 'rcon:', 'console:', 'clients:', 'http:'];
const passed = code === 0 && !timedOut && clientFailure === null && log.includes('WHITELIST_PASS')
  && joins === 2 && kicks === 1 && disconnects === 1 && rconChecks.length === 8
  && expected.every(prefix => checks.includes(prefix)) && httpRequests.length >= 10
  && !/WHITELIST_FAIL|has not responded in|failed to tick:|Thread failed main thread check|Command exception:|Terminal has been closed/.test(log);
const result = {passed, jar, sha256, work, port, rconPort, code, timedOut, clientFailure, joins, kicks, disconnects,
  intentionalDisconnects: 1, checks, rconChecks, httpRequests, observations};
writeFileSync(path.join(work, 'result.json'), JSON.stringify(result, null, 2));
console.log('WHITELIST_RESULT', passed, path.join(work, 'result.json'));
if (!passed) throw Error('Native whitelist acceptance failed: ' + work);
