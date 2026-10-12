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
const work = path.join(repo, 'build', `paper-october-update-${Date.now()}`);
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
// Reuse the verified Mojang bundler from this 26.3 build, if no native fixture cache exists.
const vanilla = path.join(repo, 'folia-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar');
if (!existsSync(path.join(work, 'cache/mojang_26.3.jar')) && existsSync(vanilla)
    && createHash('sha256').update(readFileSync(vanilla)).digest('hex') === 'd052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64') {
  mkdirSync(path.join(work, 'cache'), {recursive: true});
  copyFileSync(vanilla, path.join(work, 'cache/mojang_26.3.jar'));
}
console.log('UPDATE_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-jar', jar, '--nogui'],
  {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'paper-october-update'}, stdio: ['pipe', 'pipe', 'pipe']});
let log = '', clientsStarted = false, clientFailure = null, timedOut = false;
const observations = [];
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!clientsStarted && log.includes('UPDATE_READY_CLIENTS') && log.includes('For help, type "help"')) {
    clientsStarted = true;
    Promise.all(['UpdateAlpha', 'UpdateBeta'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('UPDATE_PASS')) observations.push(event); }, {observeAPI: true})))
      .catch(error => { clientFailure = String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 360000);
const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 405000);
const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
clearTimeout(timeout); clearTimeout(kill);
const checksPath = path.join(work, 'update-checks.txt');
const checks = existsSync(checksPath) ? readFileSync(checksPath, 'utf8') : null;
const joins = observations.filter(event => event.type === 'login').length;
const kicks = observations.filter(event => event.type === 'kicked').length;
const disconnects = observations.filter(event => event.type === 'end').length;
const packetCounts = Object.fromEntries(['UpdateAlpha', 'UpdateBeta'].map(name => [name, {
  recipes: observations.filter(event => event.username === name && event.type === 'recipes').length,
  recipeResets: observations.filter(event => event.username === name && event.type === 'recipe_book' && event.reset).length,
  postEffects: observations.filter(event => event.username === name && event.type === 'post_effects').length
}]));
const passed = code === 0 && !timedOut && clientFailure === null && log.includes('UPDATE_PASS')
  && joins === 2 && kicks === 0 && disconnects === 0
  && ['two distinct native player regions', 'deterministic complete block palettes and biomes in region 0', 'deterministic complete block palettes and biomes in region 128', 'cancelled SulfurCube explosion restored damageability', 'ignite callback retirement left fuse unchanged', 'explosion callback retirement stopped further ticking', 'explosion callback transfer stopped old-owner ticking', 'transferred cube cancellation on destination owner', 'batch recipes published', 'batch remove during tick freeze', 'duplicate batch left no partial recipe', 'native owner resource work ran during freeze', 'recipe refresh followed a dimension transfer', 'clients remained connected throughout native update'].every(prefix => checks?.includes(prefix))
  && Object.values(packetCounts).every(count => count.recipes >= 3 && count.recipeResets >= 3 && count.postEffects >= 2)
  && !/UPDATE_FAIL|failed to tick:|Thread failed main thread check|ConcurrentModificationException/.test(log);
writeFileSync(path.join(work, 'runner.log'), log);
writeFileSync(path.join(work, 'result.json'), JSON.stringify({jar, sha256, work, port, code, passed, timedOut, clientFailure, joins, kicks, disconnects, checks, packetCounts, observations}, null, 2));
console.log('UPDATE_RESULT', passed, path.join(work, 'result.json'));
if (!passed) process.exitCode = 1;


