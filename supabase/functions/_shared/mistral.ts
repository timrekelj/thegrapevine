import { observeAI } from './telemetry.ts';

export const CHAT_LARGE = 'mistral-large-latest';
export const CHAT_SMALL = 'mistral-small-latest';
export const EMBEDDINGS = 'mistral-embed';

function request(path: string, payload: { model: string; [key: string]: unknown }) {
  return observeAI('mistral', payload.model, path, async () => {
  const key = Deno.env.get('MISTRAL_API_KEY');
  if (!key) throw new Error('MISTRAL_API_KEY is missing');
  const response = await fetch(`https://api.mistral.ai/v1/${path}`, {
    method: 'POST', headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload), signal: AbortSignal.timeout(45000),
  });
  if (!response.ok) throw new Error(`Mistral ${path} returned ${response.status}`);
  return response.json();
  });
}

export async function chat(model: string, system: string, content: string) {
  const data = await request('chat/completions', {
    model, temperature: 0.2, max_tokens: 600,
    messages: [{ role: 'system', content: system }, { role: 'user', content }],
  });
  const text = data.choices?.[0]?.message?.content;
  if (typeof text !== 'string' || !text.trim()) throw new Error('Empty Mistral response');
  return text.trim();
}

export async function embed(input: string): Promise<number[]> {
  const data = await request('embeddings', { model: EMBEDDINGS, input: [input.slice(0, 12000) || 'Unavailable source'] });
  const vector = data.data?.[0]?.embedding;
  if (!Array.isArray(vector) || vector.length !== 1024 || !vector.every(Number.isFinite)) {
    throw new Error('Invalid Mistral embedding');
  }
  return vector;
}
