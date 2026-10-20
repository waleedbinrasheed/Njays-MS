import type { NextConfig } from "next";

// Browser traffic goes only to this Next.js server; /api/* is proxied to Spring Boot so the
// session and CSRF cookies are same-origin. BACKEND_URL is read when the server starts/builds.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // The Docker image runs the self-contained server (.next/standalone/server.js); other builds
  // (development, the Render demo with `next start`) keep the normal output.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  // Lets a second build (e.g. for testing against another backend) live beside the normal one.
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // Pin the project root so stray lockfiles in parent folders are ignored.
  outputFileTracingRoot: __dirname,
  experimental: {
    // Free hosting puts the backend to sleep; waking it can take about two minutes, so let the /api
    // proxy wait up to 4 minutes instead of failing after the default 30 seconds.
    proxyTimeout: 240_000,
  },
  // Browser hardening for every page (the API sets its own headers in Spring Security).
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
          { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
        ],
      },
    ];
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
