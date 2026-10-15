import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

// Run with: node components/paper/ranking.test.mjs (from web/).
const source = readFileSync(new URL('../../lib/queries.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { rankStories } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const now = Date.parse('2026-10-12T12:00:00Z');
const ago = hours => new Date(now - hours * 3_600_000).toISOString();
const story = (id, hours, messages = [], sources = []) => ({
  id, title: id, summary: '', category: null, created_at: ago(100),
  last_activity_at: ago(hours), messages: messages.map(h => ({ created_at: ago(h) })),
  sources: sources.map(h => ({ created_at: ago(h) })),
});
const quietLead = story('lead', 0);
const busy = story('busy', 4, [1, 2, 3]);
const sourced = story('source', 4, [], [1, 2]);
const recent = story('recent', 1);
const stale = story('stale', 90, [60, 70, 80], [60]);
const input = [stale, sourced, busy, recent, quietLead];
assert.deepEqual(rankStories(input, now).map(s => s.id), ['lead', 'busy', 'source', 'recent', 'stale']);
assert.equal(input[0], stale, 'ranking must not mutate the printed order');
assert.deepEqual(rankStories([], now), []);
assert.deepEqual(rankStories([quietLead], now), [quietLead]);
assert.deepEqual(rankStories([story('b', 0), story('a', 0)], now).map(s => s.id), ['a', 'b']);
assert.equal(rankStories([story('old-busy', 60, [48]), story('old-quiet', 50), quietLead], now)[1].id, 'old-busy');
console.log('Ranking checks passed: latest activity leads, weighted 48h activity, decay, stable ties, no mutation, empty/single editions.');
