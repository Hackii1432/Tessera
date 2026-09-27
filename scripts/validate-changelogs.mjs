// Dependency-free checker for the deliberately small YAML style in docs/builds/README.md.
// Double-quoted scalars use JSON escaping (a subset of YAML 1.2); no YAML execution,
// aliases, implicit scalar coercion, or third-party packages are involved.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const limits = { files: 200, directories: 30, fileBytes: 512 * 1024, sourceBytes: 8 * 1024 * 1024 };
const stringFields = ['version', 'minecraftVersion', 'title', 'description', 'releaseUrl', 'downloadUrl'];
const fields = new Set([...stringFields, 'date', 'status', 'breaking', 'tags']);
const segment = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

function quoted(value) {
    assert.match(value, /^".*"$/, 'Use a double-quoted YAML string');
    const parsed = JSON.parse(value);
    assert.equal(typeof parsed, 'string');
    assert.ok(parsed.trim(), 'Strings must not be empty');
    return parsed;
}

export function parseRelease(text) {
    text = text.replaceAll('\r\n', '\n');
    const match = text.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
    assert.ok(match, 'Missing YAML frontmatter delimiters');
    const data = {};
    let list = false;
    for (const line of match[1].split('\n')) {
        if (!line.trim()) continue;
        if (line.startsWith('  - ')) {
            assert.ok(list, 'List outside tags');
            data.tags.push(quoted(line.slice(4)));
            continue;
        }
        const field = line.match(/^(\w+):(?: (.*))?$/);
        assert.ok(field, `Unsupported YAML style: ${line}`);
        const [, key, value = ''] = field;
        assert.ok(fields.has(key), `Unknown field: ${key}; changes belong in Markdown`);
        assert.ok(!Object.hasOwn(data, key), `Duplicate field: ${key}`);
        list = key === 'tags';
        if (list) {
            assert.equal(value, '', 'Write tags as an indented list');
            data.tags = [];
        } else if (stringFields.includes(key)) {
            data[key] = quoted(value);
        } else if (key === 'breaking') {
            assert.ok(['true', 'false'].includes(value), 'breaking must be a Boolean');
            data[key] = value === 'true';
        } else if (key === 'status') {
            assert.ok(['stable', 'beta', 'alpha'].includes(value), 'Invalid status');
            data[key] = value;
        } else {
            assert.match(value, /^\d{4}-\d{2}-\d{2}$/, 'date must be YYYY-MM-DD');
            assert.equal(new Date(value).toISOString().slice(0, 10), value, 'Invalid calendar date');
            data[key] = value;
        }
    }
    for (const key of ['version', 'minecraftVersion', 'title', 'description', 'date', 'status', 'breaking', 'tags']) {
        assert.ok(Object.hasOwn(data, key), `Missing ${key}`);
    }
    for (const key of ['version', 'minecraftVersion']) assert.match(data[key], segment, `Unsafe ${key}`);
    assert.ok(data.tags.length > 0, 'tags must not be empty');
    assert.equal(new Set(data.tags).size, data.tags.length, 'Duplicate tags');
    for (const key of ['releaseUrl', 'downloadUrl']) {
        if (data[key] === undefined) continue;
        const url = new URL(data[key]);
        assert.ok(['http:', 'https:'].includes(url.protocol) && url.hostname, `Invalid ${key}`);
        assert.ok(!url.username && !url.password, 'Do not put credentials in URLs');
    }
    const body = match[2];
    for (const heading of ['Verbesserungen', 'Fehlerbehebungen', 'Änderungen am Unterbau']) {
        assert.equal(body.split(`\n## ${heading}\n`).length - 1 + Number(body.startsWith(`## ${heading}\n`)), 1,
            `Expected exactly one section: ${heading}`);
    }
    return { data, body };
}

export function validateTree(root, bounds = limits) {
    let files = 0, bytes = 0, directories = 0, maxFileBytes = 0;
    const releases = [];
    assert.ok(fs.statSync(root).isDirectory(), 'Missing changelog directory');
    for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
        const directory = path.join(root, entry.name);
        assert.ok(!entry.isSymbolicLink(), `Symlink is not a changelog source: ${directory}`);
        if (entry.isFile() && entry.name === 'README.md') {
            bytes += fs.statSync(directory).size;
            continue;
        }
        assert.ok(entry.isDirectory() && segment.test(entry.name), `Expected Minecraft directory: ${directory}`);
        directories++;
        for (const file of fs.readdirSync(directory, { withFileTypes: true })) {
            const target = path.join(directory, file.name);
            assert.ok(file.isFile() && file.name.endsWith('.md'), `No nested directories or non-Markdown files: ${target}`);
            const size = fs.statSync(target).size;
            bytes += size;
            if (file.name === 'README.md') continue;
            files++;
            maxFileBytes = Math.max(maxFileBytes, size);
            assert.ok(size <= bounds.fileBytes, `Release exceeds file limit: ${target}`);
            let release;
            try { release = parseRelease(fs.readFileSync(target, 'utf8')); }
            catch (error) { throw new Error(`${target}: ${error.message}`, { cause: error }); }
            assert.equal(release.data.minecraftVersion, entry.name, `Minecraft directory mismatch: ${target}`);
            assert.equal(`${release.data.version}.md`, file.name, `Version filename mismatch: ${target}`);
            releases.push(target);
        }
    }
    assert.ok(files > 0, 'No releases found');
    assert.ok(files <= bounds.files, `Release count ${files} exceeds ${bounds.files}`);
    assert.ok(directories <= bounds.directories, `Minecraft directory count ${directories} exceeds ${bounds.directories}`);
    assert.ok(bytes <= bounds.sourceBytes, `Source bytes ${bytes} exceeds ${bounds.sourceBytes}`);
    return { files, directories, bytes, maxFileBytes, releases };
}

export function validateLinks(files, root) {
    // Check inline Markdown file links, including their GitHub-style heading anchors.
    // Historical literal build paths inside code spans are not live download links.
    let count = 0;
    for (const file of files) {
        const source = fs.readFileSync(file, 'utf8').replace(/^```[^\n]*\n[\s\S]*?^```\s*$/gm, '');
        for (const match of source.matchAll(/\]\(([^\s)]+)(?:\s+"[^"]*")?\)/g)) {
            const href = match[1].replace(/^<|>$/g, '');
            if (/^[a-z][a-z0-9+.-]*:/i.test(href)) continue;
            const [pathname, anchor] = href.split('#');
            const target = pathname ? path.resolve(href.startsWith('/') ? root : path.dirname(file), decodeURIComponent(pathname).replace(/^\//, '')) : file;
            assert.ok(fs.existsSync(target), `${file}: broken local link ${href}`);
            if (anchor && target.endsWith('.md')) {
                const headings = fs.readFileSync(target, 'utf8').matchAll(/^#{1,6} (.+)$/gm);
                const slugs = new Set();
                const seen = new Map();
                for (const [, heading] of headings) {
                    const base = heading.trim().toLowerCase().replace(/[^\p{L}\p{N}_\- ]/gu, '').replaceAll(' ', '-');
                    const suffix = seen.get(base) || 0;
                    slugs.add(base + (suffix ? `-${suffix}` : ''));
                    seen.set(base, suffix + 1);
                }
                assert.ok(slugs.has(decodeURIComponent(anchor)), `${file}: missing anchor ${href}`);
            }
            count++;
        }
    }
    return count;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        const result = validateTree(path.join(repository, 'docs', 'builds'));
        const links = validateLinks([...result.releases, path.join(repository, 'docs/builds/README.md'), path.join(repository, 'README.md')], repository);
        console.log(`Changelogs OK: ${result.files}/${limits.files} releases, ${result.directories}/${limits.directories} Minecraft directories, ${result.bytes}/${limits.sourceBytes} bytes, largest ${result.maxFileBytes}/${limits.fileBytes} bytes; ${links} local links checked.`);
    } catch (error) {
        console.error(error.message);
        process.exitCode = 1;
    }
}
