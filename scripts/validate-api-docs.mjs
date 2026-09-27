import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateLinks } from './validate-changelogs.mjs';

export const limits = { articles: 100, fileBytes: 512 * 1024, sourceBytes: 8 * 1024 * 1024 };
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const lengths = { title: 200, description: 600, navTitle: 100, minecraftVersion: 40, badge: 80 };

// Intentional, documented YAML 1.2 subset: quoted JSON strings, numeric order,
// ISO date. Reject aliases/implicit coercion instead of guessing importer types.
export function parseArticle(text, filename) {
    assert.match(filename, /^[a-z0-9-]{1,80}\.md$/, 'Invalid permanent article filename');
    const match = text.replaceAll('\r\n', '\n').match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
    assert.ok(match, 'Missing frontmatter');
    const data = {};
    for (const line of match[1].split('\n')) {
        if (!line.trim()) continue;
        const field = line.match(/^(\w+): (.+)$/);
        assert.ok(field, `Unsupported frontmatter style: ${line}`);
        const [, key, value] = field;
        assert.ok(!Object.hasOwn(data, key), `Duplicate field ${key}`);
        if (Object.hasOwn(lengths, key)) {
            assert.match(value, /^".*"$/, `${key} must be a quoted string`);
            data[key] = JSON.parse(value);
            assert.equal(typeof data[key], 'string');
            assert.ok(data[key].trim() && data[key].length <= lengths[key], `${key} length`);
        } else if (key === 'order') {
            assert.match(value, /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/, 'order must be a number');
            data.order = Number(value);
            assert.ok(Number.isFinite(data.order), 'order must be finite');
        } else if (key === 'updated') {
            assert.match(value, /^\d{4}-\d{2}-\d{2}$/, 'updated must be YYYY-MM-DD');
            assert.equal(new Date(value).toISOString().slice(0, 10), value, 'Invalid calendar date');
            data.updated = value;
        } else {
            assert.fail(`Unsupported field ${key}`);
        }
    }
    for (const required of ['title', 'description']) assert.ok(Object.hasOwn(data, required), `Missing ${required}`);
    if (filename === 'index.md') assert.equal(data.order, 0, 'index.md needs explicit order 0');
    data.order ??= 100;
    const body = match[2].trim();
    assert.ok(body && !/^(?:#|```|<!--|\|)/.test(body), 'Start with a short introduction');
    const headings = new Set();
    let fenced = false;
    for (const line of body.split('\n')) {
        if (line.startsWith('```')) {
            if (!fenced) assert.match(line, /^```[a-z][a-z0-9+-]*$/, 'Code fence needs a language');
            else assert.equal(line, '```', 'Unexpected nested code fence');
            fenced = !fenced;
        } else if (!fenced && line.startsWith('#')) {
            assert.match(line, /^#{2,4} /, 'Only headings ## through #### are allowed');
            const slug = line.replace(/^#+ /, '').trim().toLowerCase().replace(/[^\p{L}\p{N}_\- ]/gu, '').replaceAll(' ', '-');
            assert.ok(!headings.has(slug), `Duplicate heading anchor ${slug}`);
            headings.add(slug);
        }
    }
    assert.equal(fenced, false, 'Unclosed code fence');
    const prose = body.replace(/^```[^\n]*\n[\s\S]*?^```\s*$/gm, '');
    for (const [, href] of prose.matchAll(/\]\(([^\s)]+)\)/g)) {
        if (/^https?:\/\//.test(href)) {
            const url = new URL(href);
            assert.ok(!url.username && !url.password, 'Credentials in URL');
        } else {
            assert.match(href, /^(?:[a-z0-9-]{1,80}\.md)?(?:#[^\s]+)?$/, `Article link must stay in flat API directory: ${href}`);
            assert.ok(href.length > 0);
        }
    }
    assert.ok(!/!\[[^\]]*\]\((?!https?:\/\/)/.test(prose), 'Images must use public HTTP(S) sources');
    assert.ok(!/(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)/.test(text), 'Possible credential material');
    return { data, body };
}

export function validateApi(directory, bounds = limits) {
    const articles = [];
    let bytes = 0, maxFileBytes = 0;
    for (const file of fs.readdirSync(directory, { withFileTypes: true })) {
        assert.ok(file.isFile() && !file.isSymbolicLink(), `No nested directories or symlinks: ${file.name}`);
        const target = path.join(directory, file.name);
        const size = fs.statSync(target).size;
        bytes += size;
        assert.ok(size <= bounds.fileBytes, `File exceeds limit: ${file.name}`);
        if (file.name === 'README.md') continue;
        maxFileBytes = Math.max(maxFileBytes, size);
        try { parseArticle(fs.readFileSync(target, 'utf8'), file.name); }
        catch (error) { throw new Error(`${file.name}: ${error.message}`, { cause: error }); }
        articles.push(target);
    }
    assert.ok(articles.some(f => path.basename(f) === 'index.md'), 'Missing index.md');
    assert.ok(articles.length <= bounds.articles, 'Article count exceeds limit');
    assert.ok(bytes <= bounds.sourceBytes, 'Source bytes exceed limit');
    const links = validateLinks(articles, root);
    return { articles: articles.length, bytes, maxFileBytes, links };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try { console.log('API documentation OK:', JSON.stringify(validateApi(path.join(root, 'docs/api')))); }
    catch (error) { console.error(error.message); process.exitCode = 1; }
}
