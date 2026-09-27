import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { limits, parseArticle, validateApi } from './validate-api-docs.mjs';

const valid = [
    '---', 'title: "Test"', 'description: "A documentation fixture"',
    'order: 0', 'updated: 2026-09-28', 'minecraftVersion: "26.3"', '---', '',
    'An introduction.', '', '## Example', '', '```java', 'int value = 1;', '```', '',
].join('\n');

test('accepts required/optional metadata, CRLF, default ordering', () => {
    assert.equal(parseArticle(valid.replaceAll('\n', '\r\n'), 'index.md').data.order, 0);
    assert.equal(parseArticle(valid.replace('order: 0\n', ''), 'topic.md').data.order, 100);
    assert.equal(parseArticle(valid.replace('order: 0', 'order: 1.5'), 'topic.md').data.order, 1.5);
});

for (const name of ['UPPER.md', 'with space.md', '../outside.md', 'a'.repeat(81) + '.md']) {
    test(`rejects filename ${name}`, () => assert.throws(() => parseArticle(valid, name)));
}

for (const [name, before, after] of [
    ['missing title', 'title: "Test"\n', ''],
    ['missing description', 'description: "A documentation fixture"\n', ''],
    ['empty title', 'title: "Test"', 'title: " "'],
    ['title length', 'title: "Test"', `title: "${'x'.repeat(201)}"`],
    ['description length', 'description: "A documentation fixture"', `description: "${'x'.repeat(601)}"`],
    ['numeric version', 'minecraftVersion: "26.3"', 'minecraftVersion: 26.3'],
    ['version length', 'minecraftVersion: "26.3"', `minecraftVersion: "${'x'.repeat(41)}"`],
    ['navTitle length', 'title: "Test"', `title: "Test"\nnavTitle: "${'x'.repeat(101)}"`],
    ['badge length', 'title: "Test"', `title: "Test"\nbadge: "${'x'.repeat(81)}"`],
    ['unknown field', 'title: "Test"', 'title: "Test"\nsecret: "not metadata"'],
    ['invalid date', '2026-09-28', '2026-02-30'],
    ['quoted order', 'order: 0', 'order: "0"'],
    ['non-finite order', 'order: 0', 'order: 1e999'],
    ['duplicate metadata', 'title: "Test"', 'title: "Test"\ntitle: "Other"'],
    ['index ordering', 'order: 0', 'order: 10'],
    ['top-level title', '## Example', '# Example'],
    ['deep heading', '## Example', '##### Example'],
    ['duplicate anchor', '## Example', '## Example\n\n## Example'],
    ['missing fence language', '```java', '```'],
    ['cross-directory article link', 'An introduction.', 'An introduction. [Bad](../private.md)'],
    ['private image', 'An introduction.', 'An introduction. ![Bad](image.png)'],
    ['URL credentials', 'An introduction.', 'An introduction. [Bad](https://user:password@example.invalid/)'],
]) {
    test(`rejects ${name}`, () => assert.throws(() => parseArticle(valid.replace(before, after), 'index.md')));
}

test('accepts optional field boundaries and leap day', () => {
    const source = valid.replace('title: "Test"', `title: "${'x'.repeat(200)}"\nnavTitle: "${'x'.repeat(100)}"\nbadge: "${'x'.repeat(80)}"`)
        .replace('description: "A documentation fixture"', `description: "${'x'.repeat(600)}"`)
        .replace('minecraftVersion: "26.3"', `minecraftVersion: "${'x'.repeat(40)}"`)
        .replace('2026-09-28', '2024-02-29');
    assert.equal(parseArticle(source, 'a'.repeat(80) + '.md').data.updated, '2024-02-29');
});

test('rejects unclosed code fence', () => {
    assert.throws(() => parseArticle(valid.replace(/```\n$/, ''), 'index.md'), /Unclosed/);
});

function fixture(t) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'tessera-api-doc-test-'));
    t.after(() => {
        assert.ok(path.resolve(directory).startsWith(path.resolve(os.tmpdir()) + path.sep));
        assert.ok(path.basename(directory).startsWith('tessera-api-doc-test-'));
        fs.rmSync(directory, { recursive: true, force: true });
    });
    fs.writeFileSync(path.join(directory, 'index.md'), valid);
    fs.writeFileSync(path.join(directory, 'README.md'), '# Maintenance\n');
    return directory;
}

test('counts source bytes and ignores README as article', t => {
    const result = validateApi(fixture(t));
    assert.equal(result.articles, 1);
    assert.equal(result.bytes, Buffer.byteLength(valid + '# Maintenance\n'));
});
for (const bound of ['articles', 'fileBytes', 'sourceBytes']) {
    test(`enforces ${bound}`, t => assert.throws(() => validateApi(fixture(t), { ...limits, [bound]: 0 }), /exceed/));
}
test('rejects nesting', t => {
    const directory = fixture(t);
    fs.mkdirSync(path.join(directory, 'nested'));
    assert.throws(() => validateApi(directory), /nested/);
});
test('verifies article and Unicode anchor targets', t => {
    const directory = fixture(t);
    const target = path.join(directory, 'index.md');
    fs.appendFileSync(target, '\n[Self](index.md#example)\n');
    assert.equal(validateApi(directory).links, 1);
    fs.appendFileSync(target, '\n## Überblick\n\n[Unicode](index.md#%C3%BCberblick)\n');
    assert.equal(validateApi(directory).links, 2);
    fs.appendFileSync(target, '[Bad](index.md#absent)\n');
    assert.throws(() => validateApi(directory), /missing anchor/);
    fs.writeFileSync(target, valid + '\n[Bad](missing.md)\n');
    assert.throws(() => validateApi(directory), /broken local link/);
});
