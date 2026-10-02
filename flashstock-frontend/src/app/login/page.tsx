export default function LoginPage() {
  return (
    <main style={{minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24}}>
      <section style={{maxWidth: 520, width: '100%', padding: 32, border: '1px solid #ddd', borderRadius: 16}}>
        <h1>Ingresar a FlashStock</h1>
        <p>Inicia sesión mediante Amazon Cognito.</p>
        <Link href="/auth/login" style={{display:'block',padding:16,background:'#198754',color:'#fff',borderRadius:8,textAlign:'center'}}>Iniciar sesión con Cognito</Link>
        <p><Link href="/">Volver al inicio</Link></p>
      </section>
    </main>
  );
}
