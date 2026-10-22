// AI usage accounting: one ai_usage row per model call, written once per request.
import { uuid } from './cors.ts';

// USD per million tokens. Priced at write time, so changing a rate never rewrites history.
// TO CHECK before trusting cost figures: these come from third-party price trackers (October 2026),
// not the providers' own pricing pages. Mistral: https://mistral.ai/pricing, OpenAI: https://openai.com/api/pricing
export const PRICES: Record<string, { input: number; output: number }> = {
  'mistral-large-latest': { input: 0.50, output: 1.50 },
  'mistral-small-latest': { input: 0.15, output: 0.60 },
  'mistral-embed': { input: 0.10, output: 0 },
  'gpt-6-luna': { input: 0.10, output: 0 }, // Decisions API bills input only
};

export type UsageCall = {
  operation: string; provider: string; model: string;
  input_tokens: number; output_tokens: number; latency_ms: number; is_error: boolean;
};

export type UsageContext = {
  requestId: string; functionName: string;
  userId?: string; groupId?: string; storyId?: string;
};

export function costUsd(model: string, inputTokens: number, outputTokens: number) {
  const price = PRICES[model];
  if (!price) return 0;
  return Number(((inputTokens * price.input + outputTokens * price.output) / 1_000_000).toFixed(8));
}

const idOrNull = (value?: string) => uuid(value) ? value : null;

export function usageRows(context: UsageContext, calls: UsageCall[]) {
  return calls.map(call => ({
    request_id: context.requestId, function_name: context.functionName,
    operation: call.operation, provider: call.provider, model: call.model,
    input_tokens: call.input_tokens, output_tokens: call.output_tokens,
    cost_usd: costUsd(call.model, call.input_tokens, call.output_tokens),
    latency_ms: call.latency_ms, is_error: call.is_error,
    user_id: idOrNull(context.userId), group_id: idOrNull(context.groupId), story_id: idOrNull(context.storyId),
  }));
}

// Plain PostgREST insert with the service role: avoids an import cycle with supabase.ts,
// and a failure here is logged but never fails the member's request.
export async function recordUsage(context: UsageContext, calls: UsageCall[]) {
  const url = Deno.env.get('SUPABASE_URL');
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!calls.length || !url || !key) return;
  try {
    const response = await fetch(`${url}/rest/v1/ai_usage`, {
      method: 'POST',
      headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
      body: JSON.stringify(usageRows(context, calls)),
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) console.warn('AI usage not recorded', response.status);
  } catch { console.warn('AI usage not recorded'); }
}
