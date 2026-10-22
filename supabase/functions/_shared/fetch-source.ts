import { HttpError } from './cors.ts';

const MAX_BYTES = 1024 * 1024;
const MAX_REDIRECTS = 5;
const TIMEOUT_MS = 10000;

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

// ---------------------------------------------------------------- address checks

function ipv4(address: string): number[] | null {
  const parts = address.split('.');
  if (parts.length !== 4 || !parts.every(part => /^\d{1,3}$/.test(part))) return null;
  const bytes = parts.map(Number);
  return bytes.every(byte => byte <= 255) ? bytes : null;
}

function privateIpv4([a, b, c]: number[]) {
  return a === 0 || a === 10 || a === 127 || a >= 224 || // this network, private, loopback, multicast/reserved
    (a === 100 && b >= 64 && b <= 127) || // carrier-grade NAT
    (a === 169 && b === 254) || // link-local, cloud metadata
    (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) ||
    (a === 192 && b === 0 && (c === 0 || c === 2)) || (a === 192 && b === 88 && c === 99) ||
    (a === 198 && (b === 18 || b === 19)) || (a === 198 && b === 51 && c === 100) || (a === 203 && b === 0 && c === 113);
}

/** Eight 16-bit groups, or null if this is not an IPv6 literal. Accepts a trailing dotted IPv4. */
function ipv6(address: string): number[] | null {
  let text = address.toLowerCase();
  const zone = text.indexOf('%');
  if (zone >= 0) text = text.slice(0, zone);
  if (!text.includes(':')) return null;
  const tail = text.match(/:(\d+\.\d+\.\d+\.\d+)$/);
  if (tail) {
    const v4 = ipv4(tail[1]);
    if (!v4) return null;
    text = `${text.slice(0, -tail[1].length)}${((v4[0] << 8) | v4[1]).toString(16)}:${((v4[2] << 8) | v4[3]).toString(16)}`;
  }
  const halves = text.split('::');
  if (halves.length > 2) return null;
  const parse = (half: string) => half ? half.split(':').map(group => /^[0-9a-f]{1,4}$/.test(group) ? parseInt(group, 16) : NaN) : [];
  const head = parse(halves[0]);
  const rest = halves.length === 2 ? parse(halves[1]) : [];
  const missing = 8 - head.length - rest.length;
  if (halves.length === 1 ? missing !== 0 : missing < 1) return null;
  const groups = [...head, ...Array(halves.length === 2 ? missing : 0).fill(0), ...rest];
  return groups.every(Number.isFinite) ? groups : null;
}

function privateIpv6(g: number[]) {
  if (g.slice(0, 6).every(x => x === 0)) return true; // ::, ::1, IPv4-compatible
  if (g.slice(0, 5).every(x => x === 0) && g[5] === 0xffff) return true; // IPv4-mapped
  if (g[0] === 0x64 && g[1] === 0xff9b) return true; // NAT64 (well-known and local-use)
  if (g[0] === 0x100 && g[1] === 0 && g[2] === 0 && g[3] === 0) return true; // discard-only
  if (g[0] === 0x2001 && (g[1] === 0 || g[1] === 0xdb8)) return true; // Teredo, documentation
  if (g[0] === 0x2002) return true; // 6to4 can embed any IPv4 address
  return (g[0] & 0xfe00) === 0xfc00 || // unique local
    (g[0] & 0xffc0) === 0xfe80 || (g[0] & 0xffc0) === 0xfec0 || // link-local, site-local
    (g[0] & 0xff00) === 0xff00; // multicast
}

/** True for any address an edge function must never fetch: private, loopback, link-local, metadata. */
export function privateAddress(address: string) {
  const host = address.replace(/^\[|\]$/g, '');
  const v4 = ipv4(host);
  if (v4) return privateIpv4(v4);
  const v6 = ipv6(host);
  if (v6) return privateIpv6(v6);
  return null; // not an IP literal
}

/** Host names that resolve inside private networks, regardless of public DNS. */
function privateHostname(host: string) {
  host = host.toLowerCase().replace(/\.$/, '');
  return !host.includes('.') || // single-label names: "localhost", "metadata", internal service names
    /\.(localhost|local|internal|intranet|lan|home\.arpa|corp)$/.test(host);
}

// Every hop (the first URL and each redirect) is checked, including all of its DNS answers.
// Residual risk: fetch() resolves the name again, so a DNS-rebinding host can still race this
// check; the edge runtime has no API to pin the connection to the vetted address.
async function assertPublicUrl(url: URL, signal: AbortSignal) {
  if (!['http:', 'https:'].includes(url.protocol) || (url.port && !['80', '443'].includes(url.port))) {
    throw new Error('Unsafe source address');
  }
  const literal = privateAddress(url.hostname);
  if (literal !== null) {
    if (literal) throw new Error('Unsafe source address');
    return;
  }
  if (privateHostname(url.hostname)) throw new Error('Unsafe source address');
  const answers = await Promise.allSettled([
    Deno.resolveDns(url.hostname, 'A', { signal }), Deno.resolveDns(url.hostname, 'AAAA', { signal }),
  ]);
  const ips = answers.flatMap(result => result.status === 'fulfilled' ? result.value : []);
  if (!ips.length || ips.some(ip => privateAddress(ip) !== false)) throw new Error('Unsafe or unresolved source address');
}

// ---------------------------------------------------------------- metadata

function decode(text: string) {
  return text.replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#(?:39|x27);/gi, "'")
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/\p{Cc}/gu, ' ').replace(/\s+/g, ' ').trim();
}

function titleTag(html: string) {
  // indexOf rather than a lazy regex: a page full of unclosed <title> tags must not cost O(n²).
  const open = html.search(/<title[\s>]/i);
  if (open < 0) return '';
  const start = html.indexOf('>', open);
  if (start < 0) return '';
  const end = html.toLowerCase().indexOf('</title>', start);
  return decode(html.slice(start + 1, end < 0 ? start + 1 : Math.min(end, start + 2000)));
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
    title: metas['og:title'] || metas['twitter:title'] || titleTag(html),
    excerpt: metas['og:description'] || metas['twitter:description'] || metas.description || '',
    image_url: metas['og:image'] || metas['twitter:image'],
    author: metas['twitter:creator'] || metas.author,
  };
}

/** A preview image every client loads directly: absolute http(s), no credentials, not a private address. */
export function previewImage(value: string | undefined, base: URL) {
  if (!value) return null; // never resolve a missing value as a relative path
  try {
    const url = new URL(value, base);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password ||
        privateAddress(url.hostname) || privateHostname(url.hostname) || url.href.length > 2048) return null;
    return url.href;
  } catch { return null; }
}

async function readText(response: Response, limit: number) {
  const reader = response.body?.getReader();
  if (!reader) return '';
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    chunks.push(value);
    size += value.length;
    if (size >= limit) { await reader.cancel(); break; }
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return new TextDecoder().decode(bytes.subarray(0, limit));
}

export async function fetchSource(input: string) {
  const original = new URL(input);
  const domain = original.hostname;
  const isX = /(^|\.)(x\.com|twitter\.com)$/.test(domain);
  const fallback = { publisher: domain, title: '', excerpt: '', image_url: null as string | null,
    author: null as string | null, domain, status: isX ? 'deleted' : 'failed' };
  const signal = AbortSignal.timeout(TIMEOUT_MS); // covers DNS, redirects and the body read
  try {
    let url = original;
    let response: Response | undefined;
    for (let redirects = 0; ; redirects++) {
      await assertPublicUrl(url, signal);
      response = await fetch(url, { redirect: 'manual', signal, headers: {
        'User-Agent': 'Grapevine/1.0 (link metadata preview)', Accept: 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.1',
      } });
      if (![301, 302, 303, 307, 308].includes(response.status)) break;
      await response.body?.cancel();
      const location = response.headers.get('location');
      if (!location || redirects === MAX_REDIRECTS) return fallback;
      url = new URL(location, url);
    }
    const paywalled = [401, 402, 403].includes(response.status);
    const type = response.headers.get('content-type')?.toLowerCase() ?? '';
    if ((!response.ok && !paywalled) || (type && !/text\/html|application\/xhtml\+xml/.test(type))) {
      await response.body?.cancel();
      return fallback;
    }
    const html = await readText(response, MAX_BYTES);
    const meta = metadata(html);
    // shortcut: explicit structured paywall markers only; add publisher-specific rules if pilot pages need them.
    const blocked = paywalled || /"isAccessibleForFree"\s*:\s*(?:false|"false")/i.test(html) || /(?:id|class)=["']paywall["']/i.test(html);
    return {
      publisher: (meta.publisher || domain).slice(0, 200), title: meta.title.slice(0, 500),
      excerpt: blocked ? '' : meta.excerpt.slice(0, 4000), image_url: previewImage(meta.image_url, url),
      author: meta.author?.slice(0, 200) || null, domain,
      status: blocked ? 'paywalled' : meta.title || meta.excerpt ? 'ok' : fallback.status,
    };
  } catch { return fallback; }
}
