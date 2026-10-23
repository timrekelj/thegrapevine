import type { NextConfig } from "next";
import { withPostHogConfig } from "@posthog/nextjs-config";

// Every page: no framing (clickjacking), no MIME sniffing, no full URLs (invite codes, emails) in
// referrers to the publishers members click through to, no powerful browser features, and HTTPS only.
// A script CSP would need per-request nonces; these headers hold without one.
const security = [
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'; base-uri 'self'; object-src 'none'" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  // HTTPS only from the first visit after this one (browsers ignore it over plain http, so localhost is unaffected).
  { key: "Strict-Transport-Security", value: "max-age=63072000" },
];

const nextConfig: NextConfig = {
  // A native addon (renders avatar PNGs for the apps); load it from node_modules, don't bundle it.
  serverExternalPackages: ["@resvg/resvg-js"],
  poweredByHeader: false,
  async headers() {
    return [
      { source: "/:path*", headers: security },
      // The Apple file has no extension, so its JSON type has to be set explicitly.
      { source: "/.well-known/apple-app-site-association", headers: [{ key: "Content-Type", value: "application/json" }] },
      // Demo pictures for the landing page and the local seed data; unhashed, so cached for a day rather than forever.
      { source: "/seed/:file*", headers: [{ key: "Cache-Control", value: "public, max-age=86400, stale-while-revalidate=604800" }] },
    ];
  },
};

export default process.env.POSTHOG_CLI_API_KEY ? withPostHogConfig(nextConfig, {
  personalApiKey: process.env.POSTHOG_CLI_API_KEY,
  projectId: "310029",
  host: "https://eu.posthog.com",
  sourcemaps: { enabled: true, deleteAfterUpload: true },
}) : nextConfig;
