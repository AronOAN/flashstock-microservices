import type { NextConfig } from 'next';

// All /api/* calls are handled by app/api/[...path]/route.ts: same-origin BFF.
// Never build direct browser rewrites to localhost, public API Gateway or private ECS.
const nextConfig:NextConfig={};
export default nextConfig;
