import { HttpError } from './cors.ts';

export function normalizeUrl(value: unknown) {
  if (typeof value !== 'string' || value.length > 2048) throw new HttpError(400, 'Invalid URL');
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new HttpError(400, 'Invalid URL'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
    throw new HttpError(400, 'Use an http/https URL without credentials');
  }
  url.pathname = url.pathname.replace(/\/+$/, '') || '/';
  return url.toString().replace(/\/$/, '');
}

function privateAddress(host: string) {
  host = host.replace(/^\[|\]$/g, '').toLowerCase();
  return host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') ||
    /^(0|10|127|169\.254|192\.168)\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host) ||
    /^(::|fc|fd|fe[89ab]|::ffff:)/.test(host) || /^(22[4-9]|23\d|24\d|25[0-5])\./.test(host);
}

async function publicUrl(url: URL) {
  if (!['http:', 'https:'].includes(url.protocol) || privateAddress(url.hostname) ||
      (url.port && !['80', '443'].includes(url.port))) throw new Error('Unsafe source address');
  if (!url.hostname.includes(':') && !/^\d+\.\d+\.\d+\.\d+$/.test(url.hostname)) {
    const addresses = await Promise.allSettled([
      Deno.resolveDns(url.hostname, 'A'), Deno.resolveDns(url.hostname, 'AAAA'),
    ]);
    const ips = addresses.flatMap(result => result.status === 'fulfilled' ? result.value : []);
    if (!ips.length || ips.some(privateAddress)) throw new Error('Unsafe or unresolved source address');
  }
}

function decode(text: string) {
  return text.replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#(?:39|x27);/gi, "'")
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/\s+/g, ' ').trim();
}

export function metadata(html: string) {
  const metas: Record<string, string> = {};
  for (const tag of html.match(/<meta\b[^>]*>/gi) || []) {
    const attrs: Record<string, string> = {};
    for (const match of tag.matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
      attrs[match[1].toLowerCase()] = decode(match[2] ?? match[3] ?? match[4]);
    }
    const key = attrs.property || attrs.name;
    if (key && attrs.content) metas[key.toLowerCase()] = attrs.content;
  }
  return {
    publisher: metas['og:site_name'],
    title: metas['og:title'] || metas['twitter:title'] || decode(html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || ''),
    excerpt: metas['og:description'] || metas['twitter:description'] || metas.description || '',
    image_url: metas['og:image'] || metas['twitter:image'],
    author: metas['twitter:creator'] || metas.author,
  };
}

export async function fetchSource(input: string) {
  const original = new URL(input);
  const domain = original.hostname;
  const fallback = { publisher: domain, title: '', excerpt: '', image_url: null as string | null,
    author: null as string | null, domain, status: 'failed' };
  const isX = /(^|\.)(x\.com|twitter\.com)$/.test(domain);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    let url = original;
    let response: Response | undefined;
    for (let redirects = 0; redirects <= 5; redirects++) {
      await publicUrl(url);
      response = await fetch(url, { redirect: 'manual', signal: controller.signal,
        headers: { 'User-Agent': 'Grapevine/1.0 (link metadata preview)' } });
      if (![301, 302, 303, 307, 308].includes(response.status)) break;
      await response.body?.cancel();
      const location = response.headers.get('location');
      if (!location || redirects === 5) throw new Error('Invalid redirect');
      url = new URL(location, url);
    }
    if (!response) return fallback;
    const paywalled = [401, 402, 403].includes(response.status);
    if (!response.ok && !paywalled) return { ...fallback, status: isX ? 'deleted' : 'failed' };
    const reader = response.body?.getReader();
    if (!reader) return fallback;
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 1024 * 1024) { await reader.cancel(); break; }
      chunks.push(value);
    }
    const bytes = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    const html = new TextDecoder().decode(bytes);
    const meta = metadata(html);
    // shortcut: explicit structured paywall markers only; add publisher-specific rules if pilot pages need them.
    const blocked = paywalled || /"isAccessibleForFree"\s*:\s*(?:false|"false")/i.test(html) || /(?:id|class)=["']paywall["']/i.test(html);
    let image: string | null = null;
    // meta.image_url is undefined when the page has no preview meta — never resolve that as a relative path
    if (meta.image_url) {
      try { const candidate = new URL(meta.image_url, url); if (['http:', 'https:'].includes(candidate.protocol)) image = candidate.href; } catch { /* no usable preview */ }
    }
    return { publisher: meta.publisher || domain, title: meta.title.slice(0, 500),
      excerpt: blocked ? '' : meta.excerpt.slice(0, 4000), image_url: image, author: meta.author || null,
      domain, status: blocked ? 'paywalled' : meta.title || meta.excerpt ? 'ok' : isX ? 'deleted' : 'failed' };
  } catch { return { ...fallback, status: isX ? 'deleted' : 'failed' }; }
  finally { clearTimeout(timer); }
}
