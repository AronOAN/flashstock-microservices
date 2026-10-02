import type { NextConfig } from "next";

const apiBaseUrl = (process.env.FLASHSTOCK_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      // ========== REGLAS MÁS ESPECÍFICAS PRIMERO ==========
      
      // PUERTO 8082: INVENTORY (ESPECÍFICO)
      { source: "/api/inventory/:path*", destination: `${apiBaseUrl}/api/inventory/:path*` },
      
      // PUERTO 8083: ORDERS + RECEIPTS (ESPECÍFICO)
      { source: "/api/orders/:path*", destination: `${apiBaseUrl}/api/orders/:path*` },
      { source: "/api/receipts/:path*", destination: `${apiBaseUrl}/api/receipts/:path*` },
      
      // PUERTO 8084: SHIPPING + MAPS + CART + PAYMENTS + COUPONS (ESPECÍFICO)
      { source: "/api/shipping/:path*", destination: `${apiBaseUrl}/api/shipping/:path*` },
      { source: "/api/maps/:path*", destination: `${apiBaseUrl}/api/maps/:path*` },
      { source: "/api/cart/:path*", destination: `${apiBaseUrl}/api/cart/:path*` },
      { source: "/api/payments/:path*", destination: `${apiBaseUrl}/api/payments/:path*` },
      { source: "/api/coupons/:path*", destination: `${apiBaseUrl}/api/coupons/:path*` },
      
      // PUERTO 8081: AUTH (GENERAL - VA ÚLTIMAS)
      { source: "/api/auth/:path*", destination: `${apiBaseUrl}/api/auth/:path*` },
      { source: "/api/admin/:path*", destination: `${apiBaseUrl}/api/admin/:path*` },
      { source: "/oauth2/:path*", destination: `${apiBaseUrl}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${apiBaseUrl}/login/oauth2/:path*` },
      { source: "/logout", destination: `${apiBaseUrl}/logout` },
          
    ];
  }
};

export default nextConfig;
