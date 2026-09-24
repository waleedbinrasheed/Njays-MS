import type { NextConfig } from "next";

// Browser traffic goes only to this Next.js server; /api/* is proxied to Spring Boot so the
// session and CSRF cookies are same-origin. BACKEND_URL is read when the server starts/builds.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // Pin the project root so stray lockfiles in parent folders are ignored.
  outputFileTracingRoot: __dirname,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
