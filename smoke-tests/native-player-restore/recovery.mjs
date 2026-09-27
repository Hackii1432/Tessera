// Stop/restart only disposable fixtures. This is an offline caller recovery
// simulation, not an implementation or modification of MCC's product journal.
import {spawn} from 'node:child_process';
import {readFileSync, writeFileSync, renameSync, cpSync, existsSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const java = process.argv[2], jar = process.argv[3];
if (!java) throw Error('Java 25 executable is required');
async function run(stage, work, restart) {
  let output = '';
  const args = [path.join(here, 'run.mjs'), java, ...(jar ? [jar] : [])];
  const child = spawn(process.execPath, args, {cwd: repo, windowsHide: true, env: {...process.env,
    NATIVE_RESTORE_MODE: 'native-restore-recovery', NATIVE_RESTORE_RECOVERY_STAGE: stage,
    NATIVE_RESTORE_RECOVERY_RESTART: restart ? '1' : '0', ...(work ? {NATIVE_RESTORE_REUSE_WORK: work} : {})}, stdio: ['ignore', 'pipe', 'pipe']});
  for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { output += data.toString(); process.stdout.write(data); });
  const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
  if (code !== 0) throw Error('Recovery phase failed: ' + stage + ' restart=' + restart);
  const match = output.match(/NATIVE_RESTORE_WORK (.+?) PORT \d+ JAR /);
  if (!match) throw Error('Fixture did not report its owned directory');
  return path.resolve(match[1]);
}
const results = [];
for (const stage of (process.env.NATIVE_RESTORE_RECOVERY_ONLY ? [process.env.NATIVE_RESTORE_RECOVERY_ONLY] : ['prepared', 'applied', 'inflight', 'committed', 'login'])) {
  const work = await run(stage, undefined, false);
  const evidence = path.join(work, 'native-restore-evidence');
  const active = path.resolve(readFileSync(path.join(evidence, 'recovery-active-path.txt'), 'utf8').trim());
  const rollback = path.join(evidence, 'recovery-rollback');
  if (!active.startsWith(work + path.sep) || path.basename(active) !== 'players' || !existsSync(path.join(work, '.tessera-disposable-fixture'))) throw Error('Unsafe fixture store path');
  if (stage === 'inflight') {
    const drained = readFileSync(path.join(evidence, 'shutdown-apply-result.txt'), 'utf8');
    if (!drained.includes('SERVER_STOPPING') && !drained.includes('TRANSFER_FAILED')) throw Error('In-flight apply was not explicitly drained as failure: ' + drained);
  }
  if (stage === 'login') {
    const drained = readFileSync(path.join(evidence, 'shutdown-prepare-result.txt'), 'utf8');
    if (!drained.includes('SERVER_STOPPING')) throw Error('Admitted-login prepare did not drain as a shutdown failure: ' + drained);
    if (existsSync(rollback)) throw Error('Incomplete backup must not be advertised for waiting login');
  }
  if (stage !== 'committed' && stage !== 'login') {
    // Preserve the original tree and restore the fresh backup selected by the
    // caller. No native transaction is allowed to replay over this decision.
    renameSync(active, active + '.retained-before-caller-recovery');
    cpSync(rollback, active, {recursive: true, force: false, errorOnExist: true});
  }
  await run(stage, work, true);
  results.push({stage, work, checks: readFileSync(path.join(evidence, 'recovery-restart-checks.txt'), 'utf8')});
  writeFileSync(path.join(repo, 'build/native-restore-recovery-results.json'), JSON.stringify(results, null, 2));
}
console.log('NATIVE_RESTORE_RECOVERY_MATRIX_PASS', JSON.stringify(results));
