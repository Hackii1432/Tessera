import {spawn} from 'node:child_process';
import {mkdirSync, writeFileSync, readFileSync, readdirSync, existsSync, copyFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {gunzipSync} from 'node:zlib';
import path from 'node:path';
import {connectPlayer} from './client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const java = process.argv[2];
if (!java) throw Error('Usage: node smoke-tests/runtime-snapshot/run.mjs <absolute Java 25 executable>');
const work = path.join(repo, 'build', 'snapshot-smoke-' + Date.now());
const port = Number(process.env.SNAPSHOT_SMOKE_PORT || 25584);
const flat = JSON.stringify({biome: 'minecraft:plains', lakes: false, features: false, structure_overrides: [],
  layers: [{block: 'minecraft:bedrock', height: 1}, {block: 'minecraft:dirt', height: 2}, {block: 'minecraft:grass_block', height: 1}]});
mkdirSync(path.join(work, 'plugins'), {recursive: true});
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\n`);
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
// Reuse the locally available vanilla bootstrap cache, if present.
for (const prior of ['selector-smoke', 'dragon-region-smoke']) {
  const cache = path.join(repo, 'build', prior, 'cache');
  if (existsSync(cache)) {
    mkdirSync(path.join(work, 'cache'), {recursive: true});
    for (const file of readdirSync(cache).filter(n => n.endsWith('.jar'))) copyFileSync(path.join(cache, file), path.join(work, 'cache', file));
    break;
  }
}
console.log('SMOKE_WORKDIR', work);
const devJar = process.env.SNAPSHOT_SMOKE_DEV_JAR;
const launch = devJar ? ['-cp', [devJar, ...readdirSync(process.env.SNAPSHOT_SMOKE_LIBRARIES, {recursive: true})
  .filter(n => n.endsWith('.jar')).map(n => path.join(process.env.SNAPSHOT_SMOKE_LIBRARIES, n))].join(path.delimiter), 'org.bukkit.craftbukkit.Main']
  : ['-jar', path.join(repo, 'build/libs/tessera-server-26.2.build.017-stable.jar')];
const server = spawn(java, ['-Xms512M', '-Xmx2G', ...launch, '--nogui'], {
  cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'snapshot-online'}, stdio: ['pipe', 'pipe', 'pipe']
});
let log = '', oneStarted = false, manyStarted = false;
const timer = setTimeout(() => { server.stdin.write('stop\n'); process.exitCode = 1; }, 240000);
function output(chunk) {
  const text = chunk.toString(); log += text; process.stdout.write(text);
  if (!oneStarted && log.includes('SNAPSHOT_READY_ONE')) {
    oneStarted = true; connectPlayer(repo, port, 'SnapshotOne').catch(fail);
  }
  if (!manyStarted && log.includes('SNAPSHOT_READY_MANY')) {
    manyStarted = true; Promise.all(['SnapshotTwo', 'SnapshotThree'].map(name => connectPlayer(repo, port, name))).catch(fail);
  }
}
function fail(error) { console.error(error); server.stdin.write('stop\n'); process.exitCode = 1; }
server.stdout.on('data', output); server.stderr.on('data', output);
server.on('exit', code => {
  clearTimeout(timer); writeFileSync(path.join(work, 'runner.log'), log);
  try {
    if (code !== 0 || readFileSync(path.join(work, 'snapshot-smoke/result.txt'), 'utf8') !== 'PASS') throw Error('Server smoke failed');
    if (/Thread ownership|ConcurrentModificationException|Cannot snapshot player data off|Cannot execute snapshot player save off|Watchdog.*stopping server/.test(log)) {
      throw Error('Thread-safety failure in server log');
    }
    // Read real compressed NBT, not just file presence/timestamps.
    for (const [label, expected] of [['one', 73], ['repeat', 147]]) {
      const dir = path.join(work, 'snapshot-smoke', label, 'players/data');
      const file = readdirSync(dir).find(n => n.endsWith('.dat'));
      const tag = nbt(gunzipSync(readFileSync(path.join(dir, file))));
      if (tag.XpTotal !== expected) throw Error(`${label}: expected XpTotal=${expected}, got ${tag.XpTotal}`);
      if (!tag.Inventory.some(item => item.id === 'minecraft:diamond' && item.count === 7)) throw Error('Inventory was not current');
      console.log('NBT_VERIFIED', label, 'XpTotal=' + tag.XpTotal, 'Pos=' + JSON.stringify(tag.Pos), 'diamond=7');
    }
    const manyDir = path.join(work, 'snapshot-smoke/many/players/data');
    const many = readdirSync(manyDir).filter(n => n.endsWith('.dat')).map(file => nbt(gunzipSync(readFileSync(path.join(manyDir, file)))));
    if (many.length !== 3 || new Set(many.map(p => p.Dimension)).size !== 2
      || !many.some(p => p.Pos[0] >= 4096)) throw Error('Multiworld/region player data mismatch');
    console.log('NBT_VERIFIED many', many.map(p => ({dimension: p.Dimension, pos: p.Pos})));
    console.log('SNAPSHOT_END_TO_END_PASS', work);
  } catch (error) { console.error(error); process.exitCode = 1; }
});

function nbt(buf) {
  let pos = 0;
  const number = (method, size) => { const value = buf[method](pos); pos += size; return value; };
  const byte = () => number('readUInt8', 1);
  const int = () => number('readInt32BE', 4);
  const string = () => { const len = number('readUInt16BE', 2); const value = buf.toString('utf8', pos, pos + len); pos += len; return value; };
  function payload(type) {
    switch (type) {
      case 1: return number('readInt8', 1);
      case 2: return number('readInt16BE', 2);
      case 3: return int();
      case 4: return number('readBigInt64BE', 8).toString();
      case 5: return number('readFloatBE', 4);
      case 6: return number('readDoubleBE', 8);
      case 7: { const len = int(); const value = buf.subarray(pos, pos + len); pos += len; return value; }
      case 8: return string();
      case 9: { const subtype = byte(), len = int(); return Array.from({length: len}, () => payload(subtype)); }
      case 10: { const result = {}; for (let t; (t = byte()) !== 0;) { const name = string(); result[name] = payload(t); } return result; }
      case 11: return Array.from({length: int()}, int);
      case 12: return Array.from({length: int()}, () => number('readBigInt64BE', 8).toString());
      default: throw Error('Unknown NBT type ' + type);
    }
  }
  const root = byte(); string(); return payload(root);
}
