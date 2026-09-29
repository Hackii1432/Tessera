import {spawn} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const [java, jar] = process.argv.slice(2);
if (!java || !jar) throw Error('Usage: node metadata.mjs <Java 25 executable> <server.jar>');
async function run(work, mve = false) {
  const child = spawn(process.execPath, [path.join(here, 'run.mjs'), java, jar], {cwd: repo, windowsHide: true,
    env: {...process.env, NATIVE_RESTORE_MODE: 'native-restore-metadata', NATIVE_RESTORE_METADATA_RESTART: work ? '1' : '0', NATIVE_RESTORE_METADATA_MVE: mve ? '1' : '0',
      ...(work ? {NATIVE_RESTORE_REUSE_WORK: work} : {})}, stdio: ['ignore', 'pipe', 'pipe']});
  let log = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { log += data; process.stdout.write(data); });
  const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
  if (code !== 0) throw Error('Metadata fixture failed, restart=' + Boolean(work));
  const match = log.match(/NATIVE_RESTORE_WORK (.+?) PORT \d+ JAR /);
  if (!match) throw Error('Fixture path missing');
  return path.resolve(match[1]);
}
const work = await run();
await run(work);
if (process.env.NATIVE_RESTORE_MVE_JAR && process.env.NATIVE_RESTORE_LUCKPERMS_JAR) {
  await run(work, true);
  await run(work, true); // Verify the actual persisted lifetime ledger in a fresh MVE process.
}
writeFileSync(path.join(repo, 'build/native-restore-metadata-result.json'), JSON.stringify({work, initial: path.join(work, 'native-restore-metadata.json'), restart: path.join(work, 'native-restore-metadata-restart.json')}, null, 2));
console.log('NATIVE_RESTORE_METADATA_RESTART_PASS', work);
