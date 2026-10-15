export function vector(value: string | number[]): number[] {
  return typeof value === 'string' ? JSON.parse(value) : value;
}

export function cosineSimilarity(a: number[], b: number[]) {
  if (a.length !== b.length || !a.length) return 0;
  let dot = 0, aa = 0, bb = 0;
  for (let i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] ** 2; bb += b[i] ** 2; }
  return aa && bb ? dot / Math.sqrt(aa * bb) : 0;
}

export const storyText = (story: { title: string; summary: string }) => `${story.title} ${story.summary}`;
export const sourceText = (source: { source_title: string; excerpt: string | null }) => `${source.source_title} ${source.excerpt || ''}`;

// Decision model: OpenAI Decisions API (gpt-6-luna) — typed predicate questions,
// probabilities instead of generated text, $0.10/M input tokens, output free.
export async function decide(state: string, similarity: number) {
  try {
    const response = await fetch('https://api.openai.com/v1/decisions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${Deno.env.get('OPENAI_API_KEY')}` },
      signal: AbortSignal.timeout(15000),
      body: JSON.stringify({
        model: 'gpt-6-luna',
        input: state,
        questions: [
          { type: 'predicate', name: 'same_event', instructions: 'Are these two items about the same specific event?' },
          { type: 'predicate', name: 'conflict', instructions: 'Do the sources disagree on a material fact?' },
        ],
      }),
    });
    if (!response.ok) throw new Error(`Decisions API returned ${response.status}`);
    const data = await response.json();
    type DecisionAnswer = { name?: string; type?: string; probability?: number };
    const byName = new Map<string, DecisionAnswer>();
    for (const a of (data.answers ?? []) as DecisionAnswer[]) byName.set(a.name ?? '', a);
    const same = byName.get('same_event');
    const conflict = byName.get('conflict');
    if (!same || !conflict || same.type === 'refusal' || conflict.type === 'refusal'
        || typeof same.probability !== 'number' || typeof conflict.probability !== 'number') {
      throw new Error('Invalid decision response');
    }
    console.info('decision', JSON.stringify({ similarity: Number(similarity.toFixed(3)), same_event: same.probability, conflict: conflict.probability }));
    return { same_event: same.probability >= 0.5, conflict: conflict.probability >= 0.5 };
  } catch {
    console.warn('Decision model unavailable; using conservative cosine fallback (0.88).');
    return { same_event: similarity >= 0.88, conflict: false };
  }
}
