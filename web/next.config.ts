import type { NextConfig } from "next";
import { withPostHogConfig } from "@posthog/nextjs-config";

const nextConfig: NextConfig = {
  // A native addon (renders avatar PNGs for the apps); load it from node_modules, don't bundle it.
  serverExternalPackages: ["@resvg/resvg-js"],
  /* config options here */
  // cacheComponents + partialPrefetching disabled: every route is fully dynamic
  // (auth + database), so there is nothing to prerender or cache
  // The Apple file has no extension, so its JSON type has to be set explicitly.
  async headers() {
    return [{ source: "/.well-known/apple-app-site-association", headers: [{ key: "Content-Type", value: "application/json" }] }];
  },
};

export default process.env.POSTHOG_CLI_API_KEY ? withPostHogConfig(nextConfig, {
  personalApiKey: process.env.POSTHOG_CLI_API_KEY,
  projectId: "310029",
  host: "https://eu.posthog.com",
  sourcemaps: { enabled: true, deleteAfterUpload: true },
}) : nextConfig;
