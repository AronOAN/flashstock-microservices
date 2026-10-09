
import type { ReactNode } from 'react';

import Footer from '@/components/layout/Footer';

export default function AppShell({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <>
      <main className="fs-main">
        {children}
      </main>

      <Footer />
    </>
  );
}
