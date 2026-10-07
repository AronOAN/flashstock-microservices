import type { NextConfig } from 'next';

// Transport only: no Next API routes, session handling or business logic.
// Same-origin forwarding keeps Auth's HttpOnly cookie first-party on Vercel.
const gateway = (process.env.FLASHSTOCK_API_BASE_URL || '').replace(/\/$/, '');
if (gateway && !/^https:\/\/[a-z0-9.-]+$/i.test(gateway) && !/^http:\/\/localhost(?::\d+)?$/.test(gateway)) {
  throw new Error('FLASHSTOCK_API_BASE_URL debe ser el origen del API Gateway');
}
if (process.env.VERCEL && !gateway && !process.env.NEXT_PUBLIC_API_BASE_URL) {
  throw new Error('Falta FLASHSTOCK_API_BASE_URL o NEXT_PUBLIC_API_BASE_URL');
}
const nextConfig: NextConfig = {
  async rewrites() { return gateway ? [{ source: '/api/:path*', destination: `${gateway}/api/:path*` }] : []; },
};
export default nextConfig;
