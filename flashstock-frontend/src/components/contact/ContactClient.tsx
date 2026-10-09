
'use client';

import { useState } from 'react';

type ContactMessage = {
  name: string;
  email: string;
  message: string;
};

export default function ContactClient() {
  const [status, setStatus] = useState('');
  const [error, setError] = useState<string | null>(null);

  const [preparedMessage, setPreparedMessage] =
    useState<ContactMessage | null>(null);

  function prepareMessage(form: HTMLFormElement): void {
    const formData = new FormData(form);

    const name = String(
      formData.get('name') ?? ''
    ).trim();

    const email = String(
      formData.get('email') ?? ''
    ).trim();

    const message = String(
      formData.get('message') ?? ''
    ).trim();

    // Validación adicional para evitar campos vacíos
    // que contengan únicamente espacios.
    if (
      name.length < 2 ||
      email.length === 0 ||
      message.length < 10
    ) {
      setError(
        'Ingresa un nombre válido y un mensaje de al menos 10 caracteres.'
      );

      setStatus('');
      setPreparedMessage(null);
      return;
    }

    setError(null);

    setPreparedMessage({
      name,
      email,
      message,
    });

    setStatus(
      'Mensaje preparado correctamente. ' +
      'Todavía no se ha enviado al soporte.'
    );
  }

  function clearFeedback(): void {
    setStatus('');
    setError(null);
    setPreparedMessage(null);
  }

  return (
    <section className="fs-section fs-narrow">
      <span className="fs-kicker">
        Contacto
      </span>

      <h2>Conversemos</h2>

      <p>
        Antonio Varas, Providencia · Santiago de Chile
      </p>

      <form
        className="fs-form"
        onSubmit={(event) => {
          event.preventDefault();
          prepareMessage(event.currentTarget);
        }}
        onInput={clearFeedback}
        onReset={clearFeedback}
      >
        {/* Nombre */}
        <label htmlFor="contact-name">
          Nombre

          <input
            id="contact-name"
            className="fs-input"
            name="name"
            type="text"
            autoComplete="name"
            placeholder="Tu nombre completo"
            required
            minLength={2}
            maxLength={100}
          />
        </label>

        {/* Correo */}
        <label htmlFor="contact-email">
          Correo electrónico

          <input
            id="contact-email"
            className="fs-input"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="correo@ejemplo.com"
            required
            maxLength={254}
          />
        </label>

        {/* Mensaje */}
        <label htmlFor="contact-message">
          Mensaje

          <textarea
            id="contact-message"
            className="fs-input"
            name="message"
            rows={6}
            placeholder="Escribe tu consulta..."
            required
            minLength={10}
            maxLength={5000}
          />
        </label>

        {/* Acciones */}
        <div className="fs-actions">
          <button
            className="fs-button"
            type="submit"
          >
            Preparar mensaje
          </button>

          <button
            className="fs-button fs-button-secondary"
            type="reset"
          >
            Limpiar
          </button>
        </div>
      </form>

      {/* Errores de validación */}
      {error && (
        <p className="fs-status" role="alert">
          {error}
        </p>
      )}

      {/* Estado del formulario */}
      {status && (
        <p className="fs-status" role="status">
          {status}
        </p>
      )}

      {/* Vista previa del mensaje */}
      {preparedMessage && (
        <article className="fs-order-card">
          <h3>Vista previa del mensaje</h3>

          <p>
            <strong>Nombre:</strong>{' '}
            {preparedMessage.name}
          </p>

          <p>
            <strong>Correo:</strong>{' '}
            {preparedMessage.email}
          </p>

          <p>
            <strong>Mensaje:</strong>
          </p>

          <p
            style={{
              whiteSpace: 'pre-wrap',
              overflowWrap: 'anywhere',
            }}
          >
            {preparedMessage.message}
          </p>
        </article>
      )}
    </section>
  );
}
