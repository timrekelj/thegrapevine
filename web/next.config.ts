import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  // cacheComponents + partialPrefetching disabled: every route is fully dynamic
  // (auth + database), so there is nothing to prerender or cache
};

export default nextConfig;
