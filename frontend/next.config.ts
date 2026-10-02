import type { NextConfig } from "next";

// Browser traffic goes only to this Next.js server; /api/* is proxied to Spring Boot so the
// session and CSRF cookies are same-origin. BACKEND_URL is read when the server starts/builds.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // Lets a second build (e.g. for testing against another backend) live beside the normal one.
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // Pin the project root so stray lockfiles in parent folders are ignored.
  outputFileTracingRoot: __dirname,
  experimental: {
    // Free hosting puts the backend to sleep; waking it can take about two minutes, so let the /api
    // proxy wait up to 4 minutes instead of failing after the default 30 seconds.
    proxyTimeout: 240_000,
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
