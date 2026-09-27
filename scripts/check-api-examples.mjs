// Compile the complete Java examples verbatim from docs/api; no server is started.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [javac, classpath] = process.argv.slice(2);
assert.ok(javac && classpath, 'Usage: node scripts/check-api-examples.mjs <Java-25-javac> <API-and-dependencies-classpath>');
const outputRoot = path.join(root, 'build', 'docs-api-examples');
fs.mkdirSync(outputRoot, { recursive: true });
// Each run owns a fresh directory; no deletion or overwriting existing outputs.
const output = fs.mkdtempSync(path.join(outputRoot, 'check-'));
const sources = [];
const names = new Set();
for (const file of fs.readdirSync(path.join(root, 'docs', 'api')).filter(f => f.endsWith('.md'))) {
    const text = fs.readFileSync(path.join(root, 'docs', 'api', file), 'utf8').replaceAll('\r\n', '\n');
    for (const [, name, java] of text.matchAll(/<!-- compile: ([A-Za-z][A-Za-z0-9]*) -->\n```java\n([\s\S]*?)\n```/g)) {
        assert.ok(!names.has(name), `Duplicate example class ${name}`);
        names.add(name);
        const source = path.join(output, name + '.java');
        fs.writeFileSync(source, java + '\n');
        sources.push(source);
    }
}
assert.ok(sources.length > 0, 'No complete examples found');
const args = ['--release', '25', '-proc:none', '-Xlint:deprecation', '-classpath', classpath, '-d', output, ...sources];
const quote = text => '"' + text.replaceAll('\\', '\\\\').replaceAll('"', '\\"') + '"';
const argfile = path.join(output, 'javac.args');
fs.writeFileSync(argfile, args.map(quote).join('\n') + '\n');
const result = spawnSync(javac, ['@' + argfile], { cwd: root, encoding: 'utf8', windowsHide: true });
if (result.stdout) process.stdout.write(result.stdout);
if (result.stderr) process.stderr.write(result.stderr);
if (result.error) throw result.error;
assert.equal(result.status, 0, 'API examples did not compile');
console.log(`Compiled ${sources.length} complete API examples against supplied classpath: ${output}`);
