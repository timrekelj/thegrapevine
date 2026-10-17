import { AsyncLocalStorage } from 'node:async_hooks';
import { PostHog } from 'npm:posthog-node@5.56.2';

const scope = new AsyncLocalStorage<{ client?: PostHog; userId: string; traceId: string; flags?: ReturnType<PostHog['evaluateFlags']> }>();
const token = 'phc_kXSTZhnAXUB44jnt3FK8k88YnoLtXuVVZsumUBptu6j9';

export function telemetryUser(id: string) {
  const context = scope.getStore();
  if (context) context.userId = id.toLowerCase();
}

export function capture(event: string, properties: Record<string, unknown> = {}) {
  const context = scope.getStore();
  context?.client?.capture({ distinctId: context.userId, event, properties: {
    ...properties, platform: 'backend', environment: 'production', $process_person_profile: false,
  } });
}

export async function featureEnabled(key: string, fallback = true): Promise<boolean> {
  const context = scope.getStore();
  try {
    if (!context?.client) return fallback;
    context.flags ??= context.client.evaluateFlags(context.userId);
    const result = (await context.flags).getFlag(key);
    return typeof result === 'boolean' ? result : fallback;
  } catch { return fallback; }
}

export function withTelemetry(operation: string, handler: (req: Request) => Promise<Response>) {
  return (req: Request) => scope.run({
    userId: crypto.randomUUID(), traceId: crypto.randomUUID(),
    client: Deno.env.get('POSTHOG_ENABLED') === 'true' ? new PostHog(token, {
      host: 'https://eu.i.posthog.com', flushAt: 20, flushInterval: 0,
      requestTimeout: 2000, featureFlagsRequestTimeoutMs: 2000, fetchRetryCount: 0,
      before_send: event => ({ ...event, properties: { ...event.properties,
        platform: 'backend', environment: 'production', $process_person_profile: false,
      } }),
    }) : undefined,
  }, async () => {
    const started = performance.now();
    const context = scope.getStore()!;
    try {
      const response = await handler(req);
      if (req.method === 'POST') {
        capture('backend_request_completed', { operation, status: response.status, duration_ms: Math.round(performance.now() - started) });
        if (response.status >= 500) context.client?.captureException(new Error(`${operation} failed`), context.userId, { operation });
      }
      return response;
    } catch (error) {
      // Only fixed operation names reach error tracking, never response bodies or submitted text.
      context.client?.captureException(new Error(`${operation} failed`), context.userId, { operation });
      throw error;
    } finally {
      try { await context.client?.shutdown(); } catch { console.warn('PostHog delivery unavailable'); }
    }
  });
}

// No prompts, completions, URLs or embeddings are captured.
export async function observeAI<T extends { usage?: { prompt_tokens?: number; completion_tokens?: number; input_tokens?: number; output_tokens?: number } }>(
  provider: string, model: string, operation: string, run: () => Promise<T>,
): Promise<T> {
  const started = performance.now();
  const common = { $ai_provider: provider, $ai_model: model, $ai_trace_id: scope.getStore()?.traceId,
    $ai_span_id: crypto.randomUUID(), $ai_span_name: operation };
  try {
    const result = await run();
    capture(operation === 'embeddings' ? '$ai_embedding' : '$ai_generation', {
      ...common, $ai_latency: (performance.now() - started) / 1000,
      $ai_input_tokens: result.usage?.prompt_tokens ?? result.usage?.input_tokens,
      $ai_output_tokens: result.usage?.completion_tokens ?? result.usage?.output_tokens,
    });
    return result;
  } catch (error) {
    capture(operation === 'embeddings' ? '$ai_embedding' : '$ai_generation', {
      ...common, $ai_latency: (performance.now() - started) / 1000, $ai_is_error: true, $ai_error: 'Provider request failed',
    });
    throw error;
  }
}
