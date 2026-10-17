import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  // cacheComponents + partialPrefetching disabled: every route is fully dynamic
  // (auth + database), so there is nothing to prerender or cache
  // The Apple file has no extension, so its JSON type has to be set explicitly.
  async headers() {
    return [{ source: "/.well-known/apple-app-site-association", headers: [{ key: "Content-Type", value: "application/json" }] }];
  },
};

export default nextConfig;
