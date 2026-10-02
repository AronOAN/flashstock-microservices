import type { Metadata } from 'next';
import LoginForm from './LoginForm';
import './login.css';

export const metadata: Metadata = {
  title: 'Iniciar sesión | FlashStock',
  description: 'Accede a tu cuenta FlashStock con correo y contraseña.',
};

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ auth_error?: string }> }) {
  const params = await searchParams;
  return <LoginForm loginError={params.auth_error === '1'} />;
}
