import type { ReactNode } from 'react';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
export default function AppShell({ children }: { children: ReactNode }) {
  return <><Header/><main className="fs-main">{children}</main><Footer/></>;
}
