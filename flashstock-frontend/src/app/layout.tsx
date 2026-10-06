import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { SessionProvider } from '@/components/session/SessionProvider';
import './globals.css';
import './storefront.css';
const geistSans=Geist({variable:'--font-geist-sans',subsets:['latin']});
const geistMono=Geist_Mono({variable:'--font-geist-mono',subsets:['latin']});
export const metadata:Metadata={title:'FlashStock',description:'Tienda FlashStock con Next.js, Cognito y microservicios Spring Boot.'};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="es" className={`${geistSans.variable} ${geistMono.variable}`}><body><SessionProvider>{children}</SessionProvider></body></html>;}
