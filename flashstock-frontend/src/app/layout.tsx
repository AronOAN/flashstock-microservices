
import type { Metadata } from 'next';
import type { ReactNode } from 'react';

import { Geist, Geist_Mono } from 'next/font/google';

import { SessionProvider } from '@/components/session/SessionProvider';
import Header from '@/components/layout/Header';

import './globals.css';
import './storefront.css';
import './theme.css'; // MUST be last so the forest theme overrides legacy colors


// Fuentes
const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

// Metadatos
export const metadata: Metadata = {
  title: 'FlashStock',
  description:
    'Tienda FlashStock con Next.js, Cognito y microservicios Spring Boot.',
};

// Layout principal
export default function RootLayout({children}: Readonly<{children: ReactNode;}>) {
  return (
    <html lang="es"className={`${geistSans.variable} ${geistMono.variable}`}>
      <body>
        <SessionProvider>
          {/* Navbar global */}
          <Header />

          {/* Contenido de cada página */}
          {children}
        </SessionProvider>
      </body>
    </html>
  );
}
