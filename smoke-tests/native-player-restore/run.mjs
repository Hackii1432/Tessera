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
const mode = process.env.NATIVE_RESTORE_MODE ?? 'native-restore-transaction';
// Reserve an OS-selected loopback port; never stop another server to obtain it.
const listener = net.createServer();
await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
const port = listener.address().port;
await new Promise(resolve => listener.close(resolve));
const work = path.resolve(process.env.NATIVE_RESTORE_REUSE_WORK ?? path.join(repo, 'build', `native-restore-smoke-${Date.now()}`));
if (process.env.NATIVE_RESTORE_REUSE_WORK && (!work.startsWith(path.join(repo, 'build') + path.sep) || !existsSync(path.join(work, '.tessera-disposable-fixture')))) throw Error('Recovery may reuse only an existing owned disposable build fixture');
mkdirSync(path.join(work, 'plugins'), {recursive: true});
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Owned native restore fixture; no existing worlds copied.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
const mve = process.env.NATIVE_RESTORE_METADATA_MVE === '1';
if (mve) {
  if (mode !== 'native-restore-metadata' || process.env.NATIVE_RESTORE_METADATA_RESTART !== '1') throw Error('MVE fixture requires no-login metadata restart');
  for (const [key, filename] of [['NATIVE_RESTORE_MVE_JAR', 'mve.jar'], ['NATIVE_RESTORE_LUCKPERMS_JAR', 'LuckPerms.jar']]) {
    if (!process.env[key] || !existsSync(process.env[key])) throw Error('Missing local test dependency: ' + key);
    copyFileSync(process.env[key], path.join(work, 'plugins', filename));
  }
  const config = path.join(work, 'plugins/mosaik_vanilla_enhancements');
  mkdirSync(config, {recursive: true});
  if (!existsSync(path.join(work, 'permissions.yml'))) writeFileSync(path.join(work, 'permissions.yml'), '{}\n');
  // No production configuration copied, no HTTP listener or external integrations.
  writeFileSync(path.join(config, 'config.yml'), 'player-stats-api:\n  enabled: false\n  accumulation-mode: LIFETIME\n  refresh-interval-seconds: 60\ndiscord:\n  enabled: false\n');
}
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
  cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: mode}, stdio: ['pipe', 'pipe', 'pipe']
});
let log = '', started = false, passed = false, failed = false, stopping = false;
const events = [];
const handledSignals = new Set();
let expectedDisconnect = false, holdAck = false;
function client(name) {
  return connectPlayer(repo, port, name, event => {
    events.push(event);
    const intentional = expectedDisconnect && (name === 'RestoreTwo' || mode === 'native-restore-metadata' && name === 'RestorePreview');
    if (!stopping && ['kicked', 'end'].includes(event.type) && !intentional) fail(Error('Unexpected client disconnect: ' + name));
  }, {shouldAck: () => !(holdAck && name === 'RestoreOne')});
}
// Plugins may replace /stop (for example with a scheduled-stop command).
// Use the native command so a successful fixture also verifies clean shutdown.
function stop() { if (!stopping) { stopping = true; child.stdin.write('minecraft:stop\n'); } }
function fail(error) { if (!failed) console.error(error); failed = true; stop(); }
const deadline = setTimeout(() => fail(Error('Native restore fixture deadline exceeded')), 360000);
const forceStop = setTimeout(() => { if (child.exitCode === null) { failed = true; child.kill(); } }, 405000);
function output(data) {
  const text = data.toString(); log += text; process.stdout.write(text);
  if (!started && log.includes('NATIVE_RESTORE_READY') && log.includes('For help, type "help"')) {
    started = true;
    const names = mode === 'native-restore-metadata'
      ? (process.env.NATIVE_RESTORE_METADATA_RESTART === '1' ? [] : ['RestoreOne', 'RestoreTwo', 'RestorePreview'])
      : ['RestoreOne', 'RestoreTwo'];
    Promise.all(names.map(client)).catch(fail);
  }
  if (mode === 'native-restore-metadata') {
    for (const signal of ['OFFLINE', 'REJOIN', 'PREPARE_DISCONNECT']) {
      if (!handledSignals.has(signal) && log.includes('NATIVE_RESTORE_METADATA_' + signal)) {
        handledSignals.add(signal);
        if (signal === 'OFFLINE' || signal === 'PREPARE_DISCONNECT') expectedDisconnect = true;
        if (signal === 'REJOIN') client('RestoreTwo').catch(fail);
        writeFileSync(path.join(work, 'native-restore-evidence', 'metadata-runner-' + signal), 'acknowledged');
      }
    }
    if (!passed && log.includes('NATIVE_RESTORE_METADATA_PASS')) {
      passed = true;
      const restart = process.env.NATIVE_RESTORE_METADATA_RESTART === '1';
      const clients = ['RestoreOne', 'RestoreTwo', 'RestorePreview'].map(name => ({name,
        logins: events.filter(e => e.username === name && e.type === 'login').length,
        kicks: events.filter(e => e.username === name && e.type === 'kicked').length,
        disconnects: events.filter(e => e.username === name && e.type === 'end').length}));
      const expected = restart ? [[0, 0, 0], [0, 0, 0], [0, 0, 0]] : [[1, 0, 0], [2, 2, 2], [1, 1, 1]];
      if (clients.some((c, i) => JSON.stringify([c.logins, c.kicks, c.disconnects]) !== JSON.stringify(expected[i]))) fail(Error('Metadata connection counters differ from controlled offline/Prepare fixtures: ' + JSON.stringify(clients)));
      const suffix = mve ? '-mve' : restart ? '-restart' : '';
      const dependencies = mve ? Object.fromEntries(['NATIVE_RESTORE_MVE_JAR', 'NATIVE_RESTORE_LUCKPERMS_JAR'].map(key => [key, {path: process.env[key], sha256: createHash('sha256').update(readFileSync(process.env[key])).digest('hex')}])) : {};
      writeFileSync(path.join(work, `native-restore-metadata${suffix}.json`), JSON.stringify({jar, jarSha256, dependencies, passed: !failed, restart, clients, events,
        scope: readFileSync(path.join(work, `native-restore-evidence/metadata${suffix}-checks.txt`), 'utf8')}, null, 2));
      stop();
    }
  }
  if (mode === 'native-restore-races') {
    for (const signal of ['DISCONNECT', 'LOGIN', 'ACK_HOLD', 'ACK_RELEASE']) {
      if (!handledSignals.has(signal) && log.includes('NATIVE_RESTORE_RACE_' + signal)) {
        handledSignals.add(signal);
        if (signal === 'DISCONNECT') expectedDisconnect = true;
        if (signal === 'LOGIN') client('RestoreTwo').catch(fail);
        if (signal === 'ACK_HOLD') holdAck = true;
        if (signal === 'ACK_RELEASE') holdAck = false;
        writeFileSync(path.join(work, 'native-restore-evidence', 'runner-' + signal), 'acknowledged');
      }
    }
    if (!passed && log.includes('NATIVE_RESTORE_RACES_PASS')) {
      passed = true;
      const counts = name => ({name,
        logins: events.filter(e => e.username === name && e.type === 'login').length,
        kicks: events.filter(e => e.username === name && e.type === 'kicked').length,
        disconnects: events.filter(e => e.username === name && e.type === 'end').length});
      const one = counts('RestoreOne'), two = counts('RestoreTwo');
      if (one.logins !== 1 || one.kicks || one.disconnects || two.logins !== 2 || two.kicks !== 1 || two.disconnects !== 1) fail(Error('Race connection counters differ from exactly one intentionally injected disconnect'));
      if (!events.some(e => e.type === 'teleport' && e.acknowledged === false)) fail(Error('No acknowledgement was withheld'));
      writeFileSync(path.join(work, 'native-restore-races.json'), JSON.stringify({jar, jarSha256, passed: !failed, clients: [one, two], intentionalDisconnect: true, events,
        scope: readFileSync(path.join(work, 'native-restore-evidence/race-checks.txt'), 'utf8')}, null, 2));
      stop();
    }
  }
  if (mode === 'native-restore-recovery') {
    if (!handledSignals.has('LOGIN_HOLD') && log.includes('NATIVE_RESTORE_RACE_LOGIN_HOLD')) {
      handledSignals.add('LOGIN_HOLD');
      connectPlayer(repo, port, 'WaitingLogin', event => {
        events.push(event);
        if (event.type === 'configuration_held') writeFileSync(path.join(work, 'native-restore-evidence/runner-LOGIN_HOLD'), 'configuration paused');
      }, {shouldFinishConfiguration: () => false}).catch(error => { if (!stopping) fail(error); });
    }
    if (!handledSignals.has('ACK_HOLD') && log.includes('NATIVE_RESTORE_RACE_ACK_HOLD')) {
      handledSignals.add('ACK_HOLD'); holdAck = true;
      writeFileSync(path.join(work, 'native-restore-evidence/runner-ACK_HOLD'), 'acknowledged');
    }
    if (!passed && (log.includes('NATIVE_RESTORE_RECOVERY_STOP') || log.includes('NATIVE_RESTORE_RECOVERY_PASS'))) {
      passed = true;
      for (const name of ['RestoreOne', 'RestoreTwo']) {
        if (events.filter(e => e.username === name && e.type === 'login').length !== 1 || events.some(e => e.username === name && ['kicked', 'end'].includes(e.type))) fail(Error('Recovery phase had an unexpected reconnect/disconnect before controlled stop'));
      }
      const phase = process.env.NATIVE_RESTORE_RECOVERY_STAGE;
      const restart = process.env.NATIVE_RESTORE_RECOVERY_RESTART === '1';
      writeFileSync(path.join(work, `recovery-${phase}-${restart ? 'restart' : 'stop'}.json`), JSON.stringify({jar, jarSha256, phase, restart, passed: !failed, events}, null, 2));
      stop();
    }
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
      nativeComponentChecksPassed: !failed, publicTransactionExercised: mode === 'native-restore-transaction', publicTransactionAccepted: mode === 'native-restore-transaction' && !failed, contractVersion: 1,
      scope: readFileSync(path.join(work, 'native-restore-evidence/checks.txt'), 'utf8'), events}, null, 2));
    stop();
  }
  if (log.includes('NATIVE_RESTORE_COMPONENTS_FAIL')) fail(Error('Native component assertion failed'));
}
child.stdout.on('data', output); child.stderr.on('data', output);
child.on('error', fail);
child.on('exit', code => {
  clearTimeout(deadline); clearTimeout(forceStop);
  const suffix = mode === 'native-restore-recovery' ? `-${process.env.NATIVE_RESTORE_RECOVERY_STAGE}-${process.env.NATIVE_RESTORE_RECOVERY_RESTART === '1' ? 'restart' : 'stop'}`
    : mve ? '-metadata-mve' : mode === 'native-restore-metadata' && process.env.NATIVE_RESTORE_METADATA_RESTART === '1' ? '-metadata-restart' : '';
  writeFileSync(path.join(work, `runner${suffix}.log`), log);
  if (code !== 0 || !passed || failed) process.exitCode = 1;
  console.log('NATIVE_RESTORE_EXIT', code, 'componentsPassed', passed && !failed, 'publicTransactionAccepted', mode === 'native-restore-transaction' && passed && !failed);
});
