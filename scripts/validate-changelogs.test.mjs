import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { limits, parseRelease, validateLinks, validateTree } from './validate-changelogs.mjs';

const valid = `---
version: "0.0.1"
minecraftVersion: "26.3"
title: "Test: YAML strings"
description: "A test fixture, not a published release."
date: 2026-09-27
status: alpha
breaking: false
tags:
  - "Tessera"
  - "Sinopia"
---

## Verbesserungen

Test.

## Fehlerbehebungen

Test.

## Änderungen am Unterbau

Test.
`;

test('canonical YAML types and CRLF are preserved', () => {
    const { data } = parseRelease(valid.replaceAll('\n', '\r\n'));
    assert.equal(data.version, '0.0.1');
    assert.equal(data.minecraftVersion, '26.3');
    assert.equal(data.breaking, false);
    assert.deepEqual(data.tags, ['Tessera', 'Sinopia']);
});

for (const [name, before, after] of [
    ['unquoted version', 'version: "0.0.1"', 'version: 0.0.1'],
    ['planned status', 'status: alpha', 'status: planned'],
    ['string Boolean', 'breaking: false', 'breaking: "false"'],
    ['numeric tag', '  - "Tessera"', '  - 123'],
    ['duplicate field', 'status: alpha', 'status: alpha\nstatus: beta'],
    ['duplicate tag', '  - "Sinopia"', '  - "Tessera"'],
    ['missing title', 'title: "Test: YAML strings"\n', ''],
    ['invalid date', '2026-09-27', '2026-02-30'],
    ['changes ignored by importer', 'status: alpha', 'status: alpha\nchanges: {}'],
    ['path escape', 'version: "0.0.1"', 'version: "../bad"'],
    ['unsafe URL', 'status: alpha', 'status: alpha\nreleaseUrl: "javascript:alert(1)"'],
    ['URL credentials', 'status: alpha', 'status: alpha\ndownloadUrl: "https://user:secret@example.com/file.jar"'],
    ['missing category', '## Fehlerbehebungen', '## Fixes'],
]) {
    test(`rejects ${name}`, () => assert.throws(() => parseRelease(valid.replace(before, after))));
}

test('optional HTTP(S) URLs accepted', () => {
    assert.equal(parseRelease(valid.replace('status: alpha', 'status: stable\nreleaseUrl: "https://example.com/release"')).data.status, 'stable');
});

function fixture(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tessera-changelogs-test-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    fs.mkdirSync(path.join(root, '26.3'));
    const release = path.join(root, '26.3/0.0.1.md');
    fs.writeFileSync(release, valid);
    fs.writeFileSync(path.join(root, 'README.md'), '# Not a release\n');
    return { root, release };
}

test('discovers releases; excludes READMEs; counts source bytes', t => {
    const { root } = fixture(t);
    const result = validateTree(root);
    assert.equal(result.files, 1);
    assert.equal(result.directories, 1);
    assert.equal(result.bytes, Buffer.byteLength(valid) + Buffer.byteLength('# Not a release\n'));
});

test('rejects wrong filename, Minecraft folder and extra depth', t => {
    const { root, release } = fixture(t);
    fs.writeFileSync(release, valid.replace('version: "0.0.1"', 'version: "0.0.2"'));
    assert.throws(() => validateTree(root), /filename mismatch/);
    fs.writeFileSync(release, valid.replace('minecraftVersion: "26.3"', 'minecraftVersion: "26.2"'));
    assert.throws(() => validateTree(root), /directory mismatch/);
    fs.writeFileSync(release, valid);
    fs.mkdirSync(path.join(root, '26.3/nested'));
    assert.throws(() => validateTree(root), /nested directories/);
});

for (const bound of ['files', 'directories', 'fileBytes', 'sourceBytes']) {
    test(`enforces importer ${bound} limit without dropping releases`, t => {
        const { root } = fixture(t);
        assert.throws(() => validateTree(root, { ...limits, [bound]: 0 }), /exceeds/);
    });
}

test('checks local files and Unicode heading anchors; ignores code and HTTP links', t => {
    const { root, release } = fixture(t);
    const readme = path.join(root, 'README.md');
    fs.writeFileSync(readme, '[Release](26.3/0.0.1.md#änderungen-am-unterbau)\n[External](https://example.com)\n```md\n[Template](missing.md)\n```\n');
    assert.equal(validateLinks([readme], root), 1);
    fs.appendFileSync(readme, '[Bad](26.3/0.0.1.md#missing)');
    assert.throws(() => validateLinks([readme], root), /missing anchor/);
    fs.writeFileSync(readme, '[Bad](missing.md)');
    assert.throws(() => validateLinks([readme], root), /broken local link/);
    assert.ok(fs.existsSync(release));
});
