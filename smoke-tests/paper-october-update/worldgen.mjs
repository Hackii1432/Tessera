// Real startup recovery/failure paths on a copy of an owned disposable fixture.
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {copyFileSync, cpSync, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, jarArgument, sourceArgument] = process.argv.slice(2);
if (!java || !jarArgument || !sourceArgument) throw Error('Usage: node worldgen.mjs <java25> <server.jar> <owned clean fixture>');
const jar = path.resolve(jarArgument), source = path.resolve(sourceArgument);
if (!source.startsWith(path.join(repo, 'build') + path.sep) || !existsSync(path.join(source, '.tessera-disposable-fixture'))) throw Error('Source must be an owned disposable build fixture');
function checkTree(folder) {
  if (lstatSync(folder).isSymbolicLink()) throw Error('Fixture contains a symlink');
  for (const entry of readdirSync(folder, {withFileTypes: true})) {
    const item = path.join(folder, entry.name);
    if (entry.isSymbolicLink()) throw Error('Fixture contains a symlink');
    if (entry.isDirectory()) checkTree(item);
  }
}
checkTree(path.join(source, 'world'));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const sha256 = hash(readFileSync(jar));
const work = path.join(repo, 'build', `worldgen-startup-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'cache'));
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Owned worldgen startup regression; source and production data never mutated.\n');
cpSync(path.join(source, 'world'), path.join(work, 'world'), {recursive: true});
copyFileSync(jar, path.join(work, 'server.jar'));
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
copyFileSync(path.join(source, 'cache/mojang_26.3.jar'), path.join(work, 'cache/mojang_26.3.jar'));
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
writeFileSync(path.join(work, 'config/paper-global.yml'), '_version: 31\nthreaded-regions:\n  threads: 4\nspark:\n  enabled: false\n');
const listener = net.createServer();
await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
const port = listener.address().port;
await new Promise(resolve => listener.close(resolve));
let properties = readFileSync(path.join(source, 'server.properties'), 'utf8');
properties = properties.replace(/^server-port=.*$/m, `server-port=${port}`);
if (!/^server-ip=127\.0\.0\.1$/m.test(properties) || !/^online-mode=false$/m.test(properties)) throw Error('Only offline loopback fixture settings are allowed');
writeFileSync(path.join(work, 'server.properties'), properties);
const settings = path.join(work, 'world/dimensions/minecraft/overworld/data/minecraft/world_gen_settings.dat');
if (!existsSync(settings)) throw Error('Clean fixture has no dimension-scoped worldgen settings');
renameSync(settings, settings + '.before-missing-test');
console.log('WORLDGEN_FIXTURE', work, 'SHA256', sha256);
async function run(name, expectStart) {
  const started = Date.now();
  const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx2G', '-jar', 'server.jar', '--nogui'],
    {cwd: work, windowsHide: true, env: {...process.env, TESSERA_SMOKE_MODE: 'console-shutdown'}, stdio: ['pipe', 'pipe', 'pipe']});
  let log = '', ready = false, timedOut = false;
  const accept = chunk => {
    log += chunk.toString();
    if (!ready && log.includes('CONSOLE_SHUTDOWN_READY')) { ready = true; child.stdin.write('stop\n'); }
  };
  child.stdout.on('data', accept); child.stderr.on('data', accept);
  const timeout = setTimeout(() => { timedOut = true; child.stdin.write('stop\n'); }, 70000);
  const kill = setTimeout(() => { if (child.exitCode === null) child.kill(); }, 85000);
  const code = await new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); });
  clearTimeout(timeout); clearTimeout(kill);
  writeFileSync(path.join(work, `${name}.log`), log);
  const passed = !timedOut && (expectStart
    ? code === 0 && ready && log.includes('World gen settings file is missing! Regenerating settings from server.properties:') && log.includes('Saved all worlds') && existsSync(settings)
    : !ready && log.includes('Unable to read or access the world gen settings file') && !log.includes('World gen settings file is missing! Regenerating'));
  const result = {name, passed, code, ready, timedOut, elapsedMs: Date.now() - started};
  console.log('WORLDGEN_PHASE', JSON.stringify(result));
  return result;
}
const missing = await run('missing-settings', true);
if (!missing.passed) throw Error('Missing settings did not recover');
copyFileSync(settings, settings + '.after-recovery');
const corrupt = Buffer.from('Deliberately corrupt owned worldgen fixture, not a gzip stream.');
writeFileSync(settings, corrupt);
const corruptResult = await run('corrupt-settings', false);
const corruptFilePreserved = hash(readFileSync(settings)) === hash(corrupt);
const passed = missing.passed && corruptResult.passed && corruptFilePreserved;
writeFileSync(path.join(work, 'result.json'), JSON.stringify({passed, jar, sha256, source, work, port, missing,
  corrupt: corruptResult, corruptFilePreserved}, null, 2));
console.log('WORLDGEN_RESULT', passed, path.join(work, 'result.json'));
if (!passed) process.exitCode = 1;
