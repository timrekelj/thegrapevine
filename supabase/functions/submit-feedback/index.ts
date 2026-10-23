import { body, errorResponse, HttpError, json } from '../_shared/cors.ts';
import { caller, check, rateLimit, supabase } from '../_shared/supabase.ts';
import { chat, CHAT_SMALL } from '../_shared/mistral.ts';
import { DRAFT_PROMPT, draftInput, type Draft, fallbackTitle, issueBody, neutralize, parseDraft, parseReport, type Report } from '../_shared/feedback.ts';
import { capture, withTelemetry } from '../_shared/telemetry.ts';

// Each report spends one small model call and files a public issue.
const REPORTS_PER_WINDOW = 5;
const WINDOW_MINUTES = 10;
const REPORTS_PER_DAY = 20;

/** The model's title and summary; a failure files the report with the reporter's own words. */
async function draft(report: Report): Promise<Draft | null> {
  try { return parseDraft(await chat(CHAT_SMALL, DRAFT_PROMPT, draftInput(report))); }
  catch { console.warn('Report draft unavailable'); return null; }
}

async function missingImages(userId: string, images: string[]) {
  if (!images.length) return false;
  const files = await check(supabase.storage.from('feedback').list(userId, { limit: 1000 }));
  const names = new Set((files ?? []).map(file => `${userId}/${file.name}`));
  return images.some(path => !names.has(path));
}

async function fileIssue(title: string, text: string, kind: string) {
  const token = Deno.env.get('GITHUB_TOKEN');
  const repo = Deno.env.get('GITHUB_REPO') ?? 'timrekelj/thegrapevine';
  if (!token) throw new Error('GITHUB_TOKEN is missing');
  const response = await fetch(`https://api.github.com/repos/${repo}/issues`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json', 'User-Agent': 'thegrapevine-reports',
    },
    body: JSON.stringify({ title, body: text, labels: ['report', kind] }),
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error(`GitHub issues returned ${response.status}`);
  const issue = await response.json();
  return { number: issue.number as number, url: issue.html_url as string };
}

Deno.serve(withTelemetry('submit-feedback', async req => {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  try {
    const user = await caller(req);
    const report = parseReport(await body(req), user.id);
    await rateLimit('submit-feedback', user.id, REPORTS_PER_WINDOW, WINDOW_MINUTES, REPORTS_PER_DAY);
    if (await missingImages(user.id, report.images)) throw new HttpError(400, 'A screenshot did not finish uploading. Try attaching it again.');
    const saved = await check(supabase.from('feedback').insert({
      user_id: user.id, kind: report.kind, description: report.description, images: report.images, context: report.context,
    }).select('id').single());
    const ai = await draft(report);
    const title = neutralize(ai?.title ?? fallbackTitle(report.description));
    const urls = report.images.map(path => supabase.storage.from('feedback').getPublicUrl(path).data.publicUrl);
    const text = issueBody(report, ai, urls, saved!.id);
    // The report is saved either way: a GitHub failure is logged for follow-up, not shown as
    // an error the member would retry into a duplicate.
    let issue: { number: number; url: string } | null = null;
    try { issue = await fileIssue(title, text, report.kind); }
    catch (error) { console.error('Report not filed', error instanceof Error ? error.message : 'GitHub error'); }
    await check(supabase.from('feedback').update({
      title, body: text, issue_number: issue?.number ?? null, issue_url: issue?.url ?? null,
    }).eq('id', saved!.id));
    capture('feedback_submitted', { kind: report.kind, screenshots: report.images.length, platform: report.context.platform, filed: !!issue });
    return json({ id: saved!.id, issue_number: issue?.number ?? null, issue_url: issue?.url ?? null });
  } catch (error) { return errorResponse(error); }
}));
