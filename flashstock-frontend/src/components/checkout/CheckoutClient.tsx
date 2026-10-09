
'use client';

import {
  useEffect,
  useRef,
  useState,
} from 'react';

import Link from 'next/link';

import {
  ApiError,
  apiRequest,
} from '@/lib/api-client';

import { useSession } from '@/components/session/SessionProvider';

import type { CartItem } from '@/types/domain';

// Estados de la carga del carrito.
type CartState =
  | { status: 'loading' }
  | { status: 'success'; items: CartItem[] }
  | { status: 'error'; message: string };

// Datos enviados exclusivamente al backend.
type CheckoutPayload = {
  customerFirstName: string;
  customerLastName: string;
  shippingAddress: string;
};

// Conserva el payload asociado a su clave.
type CheckoutAttempt = {
  idempotencyKey: string;
  payload: CheckoutPayload;
};

function getErrorMessage(
  error: unknown,
  fallback: string
): string {
  if (
    error instanceof Error &&
    error.message.trim().length > 0
  ) {
    return error.message;
  }

  return fallback;
}

type CheckoutFormProps = {
  email: string;
};

function CheckoutForm({ email }: CheckoutFormProps) {
  const [cart, setCart] = useState<CartState>({
    status: 'loading',
  });

  const [reloadVersion, setReloadVersion] = useState(0);

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [address, setAddress] = useState('');

  const [submitting, setSubmitting] = useState(false);

  const [submitError, setSubmitError] = useState<
    string | null
  >(null);

  // Bloquea cambios en un intento cuya respuesta
  // todavía podría corresponder a un pedido creado.
  const [attemptLocked, setAttemptLocked] = useState(false);

  const mountedRef = useRef(false);
  const submittingRef = useRef(false);

  const attemptRef = useRef<CheckoutAttempt | null>(null);

  // Control del ciclo de vida.
  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Cargar carrito desde el backend.
  useEffect(() => {
    let active = true;

    async function loadCart(): Promise<void> {
      try {
        const response = await apiRequest<CartItem[]>(
          '/api/cart'
        );

        if (!active) return;

        if (!Array.isArray(response)) {
          throw new Error(
            'La respuesta del carrito no es válida'
          );
        }

        setCart({
          status: 'success',
          items: response,
        });
      } catch (error: unknown) {
        if (!active) return;

        setCart({
          status: 'error',
          message: getErrorMessage(
            error,
            'No se pudo cargar el carrito'
          ),
        });
      }
    }

    void loadCart();

    return () => {
      active = false;
    };
  }, [reloadVersion]);

  function retryCart(): void {
    if (submittingRef.current || attemptRef.current) {
      return;
    }

    setCart({ status: 'loading' });
    setSubmitError(null);
    setReloadVersion((previous) => previous + 1);
  }

  async function submit(): Promise<void> {
    // Protección inmediata contra doble envío.
    if (submittingRef.current) {
      return;
    }

    if (
      cart.status !== 'success' ||
      cart.items.length === 0
    ) {
      setSubmitError(
        'No tienes productos disponibles para comprar.'
      );
      return;
    }

    const payload: CheckoutPayload = {
      customerFirstName: firstName.trim(),
      customerLastName: lastName.trim(),
      shippingAddress: address.trim(),
    };

    if (
      payload.customerFirstName.length < 2 ||
      payload.customerLastName.length < 2 ||
      payload.shippingAddress.length < 5
    ) {
      setSubmitError(
        'Completa correctamente los datos de entrega.'
      );
      return;
    }

    // En los reintentos se conserva la misma clave
    // y exactamente los mismos datos del primer envío.
    const attempt: CheckoutAttempt =
      attemptRef.current ?? {
        idempotencyKey: crypto.randomUUID(),
        payload,
      };

    attemptRef.current = attempt;
    submittingRef.current = true;

    setSubmitting(true);
    setAttemptLocked(true);
    setSubmitError(null);

    try {
      const orderNumbers = await apiRequest<string[]>(
        '/api/orders/checkout',
        {
          method: 'POST',
          headers: {
            'Idempotency-Key': attempt.idempotencyKey,
          },
          body: JSON.stringify(attempt.payload),
        }
      );

      // Validar contrato de respuesta.
      if (
        !Array.isArray(orderNumbers) ||
        orderNumbers.length === 0 ||
        !orderNumbers.every(
          (number) =>
            typeof number === 'string' &&
            number.trim().length > 0
        )
      ) {
        throw new Error(
          'El servidor no devolvió números de pedido válidos. ' +
          'Revisa tu historial antes de intentar otra compra.'
        );
      }

      if (!mountedRef.current) return;

      // Navegar sin conservar el checkout en el historial.
      const query = new URLSearchParams({
        orderNumbers: orderNumbers.join(','),
      });

      window.location.replace(
        `/boleta?${query.toString()}`
      );
    } catch (error: unknown) {
      if (!mountedRef.current) return;

      // Errores de validación explícitos.
      // El usuario puede corregir los datos.
      if (
        error instanceof ApiError &&
        (error.status === 400 || error.status === 422)
      ) {
        attemptRef.current = null;
        setAttemptLocked(false);
      }

      setSubmitError(
        getErrorMessage(
          error,
          'No se pudo completar el pedido. ' +
          'Revisa tu historial antes de reintentar.'
        )
      );
    } finally {
      submittingRef.current = false;

      if (mountedRef.current) {
        setSubmitting(false);
      }
    }
  }

  // Estado de carga.
  if (cart.status === 'loading') {
    return (
      <section className="fs-section fs-narrow">
        <p className="fs-status" role="status">
          Cargando carrito…
        </p>
      </section>
    );
  }

  // Error al obtener el carrito.
  if (cart.status === 'error') {
    return (
      <section className="fs-section fs-narrow">
        <p className="fs-status" role="alert">
          {cart.message}
        </p>

        <button
          className="fs-button fs-button-secondary"
          type="button"
          onClick={retryCart}
        >
          Reintentar
        </button>
      </section>
    );
  }

  // Carrito vacío.
  if (cart.items.length === 0) {
    return (
      <section className="fs-empty">
        <h2>Tu carrito está vacío</h2>

        <p>
          Agrega productos antes de continuar con la compra.
        </p>

        <Link className="fs-button" href="/shop">
          Ir a la tienda
        </Link>
      </section>
    );
  }

  const totalUnits = cart.items.reduce(
    (total, item) => total + item.quantity,
    0
  );

  const fieldsDisabled = submitting || attemptLocked;

  return (
    <section
      className="fs-section fs-narrow"
      aria-busy={submitting}
    >
      <span className="fs-kicker">
        Checkout
      </span>

      <h2>Datos de entrega</h2>

      <p>
        Tienes {totalUnits}{' '}
        {totalUnits === 1 ? 'unidad' : 'unidades'} en
        tu carrito.
      </p>

      <form
        className="fs-form"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {/* Nombre */}
        <label htmlFor="checkout-first-name">
          Nombre

          <input
            id="checkout-first-name"
            className="fs-input"
            name="firstName"
            type="text"
            autoComplete="given-name"
            required
            minLength={2}
            maxLength={100}
            disabled={fieldsDisabled}
            value={firstName}
            onChange={(event) => {
              setFirstName(event.target.value);
              setSubmitError(null);
            }}
          />
        </label>

        {/* Apellido */}
        <label htmlFor="checkout-last-name">
          Apellido

          <input
            id="checkout-last-name"
            className="fs-input"
            name="lastName"
            type="text"
            autoComplete="family-name"
            required
            minLength={2}
            maxLength={100}
            disabled={fieldsDisabled}
            value={lastName}
            onChange={(event) => {
              setLastName(event.target.value);
              setSubmitError(null);
            }}
          />
        </label>

        {/* Dirección */}
        <label htmlFor="checkout-address">
          Dirección de entrega

          <input
            id="checkout-address"
            className="fs-input"
            name="shippingAddress"
            type="text"
            autoComplete="street-address"
            required
            minLength={5}
            maxLength={300}
            disabled={fieldsDisabled}
            value={address}
            onChange={(event) => {
              setAddress(event.target.value);
              setSubmitError(null);
            }}
          />
        </label>

        {/* Email asociado a la sesión */}
        <label htmlFor="checkout-email">
          Correo de tu cuenta

          <input
            id="checkout-email"
            className="fs-input"
            type="email"
            value={email}
            readOnly
          />
        </label>

        {/* Error */}
        {submitError && (
          <p className="fs-status" role="alert">
            {submitError}
          </p>
        )}

        {/* Advertencia de reintento */}
        {attemptLocked && !submitting && (
          <p className="fs-status" role="status">
            La solicitud anterior podría haber creado
            el pedido. Puedes reintentar con los mismos
            datos o revisar tu historial.
          </p>
        )}

        {/* Acciones */}
        <div className="fs-actions">
          <button
            className="fs-button"
            type="submit"
            disabled={submitting}
          >
            {submitting
              ? 'Creando pedido…'
              : attemptLocked
                ? 'Reintentar pedido'
                : 'Crear pedido'}
          </button>

          <Link
            className="fs-button fs-button-secondary"
            href="/order-status"
          >
            Mis pedidos
          </Link>
        </div>
      </form>
    </section>
  );
}

// Verificación de sesión independiente.
export default function CheckoutClient() {
  const {
    session,
    loading,
    error,
    refresh,
  } = useSession();

  if (loading) {
    return (
      <p className="fs-status" role="status">
        Verificando sesión…
      </p>
    );
  }

  if (error) {
    return (
      <section className="fs-empty">
        <h2>No se pudo verificar tu sesión</h2>

        <p className="fs-status" role="alert">
          {error}
        </p>

        <button
          className="fs-button fs-button-secondary"
          type="button"
          onClick={() => void refresh()}
        >
          Reintentar
        </button>
      </section>
    );
  }

  if (!session?.authenticated) {
    return (
      <section className="fs-empty">
        <h2>Necesitas iniciar sesión</h2>

        <p>
          Inicia sesión para completar tu compra.
        </p>

        <Link className="fs-button" href="/login">
          Iniciar sesión
        </Link>
      </section>
    );
  }

  return (
    <CheckoutForm
      key={session.email ?? 'authenticated'}
      email={session.email ?? ''}
    />
  );
}
