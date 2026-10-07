import { Suspense } from 'react';
import type { Metadata } from 'next';
import LoginForm from './LoginForm';
import './login.css';

export const metadata: Metadata = {
  title: 'Iniciar sesión | FlashStock',
  description: 'Accede a tu cuenta FlashStock con correo y contraseña.',
};

export default function LoginPage() {
  return <Suspense fallback={<p>Cargando…</p>}><LoginForm /></Suspense>;
}
