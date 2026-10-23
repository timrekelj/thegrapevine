import { fallbackTitle, issueBody, neutralize, parseDraft, parseReport } from './feedback.ts';

function assert(condition: boolean) { if (!condition) throw new Error('Assertion failed'); }
function rejects(run: () => unknown) {
  let rejected = false;
  try { run(); } catch { rejected = true; }
  assert(rejected);
}

const user = '11111111-1111-4111-8111-111111111111';
const image = `${user}/22222222-2222-4222-8222-222222222222.jpg`;

Deno.test('reports are validated and limited to the caller\'s own screenshots', () => {
  const report = parseReport({ kind: 'bug', description: '  It broke  ', images: [image, image],
    context: { platform: 'ios', os: 'iOS\n26', secret: 'dropped', error: 42 } }, user);
  assert(report.description === 'It broke' && report.images.length === 1);
  assert(report.context.os === 'iOS 26' && !('secret' in report.context) && !('error' in report.context));
  rejects(() => parseReport({ kind: 'question', description: 'x' }, user));
  rejects(() => parseReport({ kind: 'bug', description: '   ' }, user));
  rejects(() => parseReport({ kind: 'bug', description: 'x'.repeat(4001) }, user));
  rejects(() => parseReport({ kind: 'bug', description: 'x', images: [`33333333-3333-4333-8333-333333333333/22222222-2222-4222-8222-222222222222.jpg`] }, user));
  rejects(() => parseReport({ kind: 'bug', description: 'x', images: [`${user}/../media/a.jpg`] }, user));
  rejects(() => parseReport({ kind: 'bug', description: 'x', images: Array(6).fill(image) }, user));
});

Deno.test('public issue text cannot ping users or link issues', () => {
  const text = neutralize('cc @octocat, fixes #12, mail me@example.com');
  assert(!/@[a-z]/i.test(text) && !/#\d/.test(text) && text.replace(/‍/g, '') === 'cc @octocat, fixes #12, mail me@example.com');
});

Deno.test('AI drafts are parsed defensively with a fallback title', () => {
  assert(parseDraft('Sure!\n{"title": "\\"Story fails to load\\"", "summary": "**What happened**\\nIt fails"}')?.title === 'Story fails to load');
  assert(parseDraft('{"title": "", "summary": "x"}') === null);
  assert(parseDraft('no json') === null);
  assert(parseDraft('{"title": "Ok"}')?.summary === null);
  assert(fallbackTitle('\n\nShort one\nmore') === 'Short one');
  const long = fallbackTitle('word '.repeat(40));
  assert(long.length <= 81 && long.endsWith('…'));
});

Deno.test('the issue keeps the reporter\'s words verbatim and quoted', () => {
  const report = parseReport({ kind: 'bug', description: 'Line one\n### not a heading\n@someone', images: [image],
    context: { platform: 'web', error: 'a | b' } }, user);
  const body = issueBody(report, { title: 'T', summary: '**What happened**\nX' }, ['https://cdn/x.jpg'], 'id-1');
  assert(body.startsWith('**What happened**'));
  assert(body.includes('> Line one\n> ### not a heading\n> @‍someone'));
  assert(body.includes('<img src="https://cdn/x.jpg" alt="Screenshot 1"'));
  assert(body.includes('| Error shown | a \\| b |') && body.includes('report id-1'));
  assert(!issueBody(report, null, [], 'id-2').includes('drafted by AI'));
});
