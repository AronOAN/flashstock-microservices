
'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import {
  ApiError,
  apiRequest,
  logoutSession,
} from '@/lib/api-client';

import type { SessionUser } from '@/types/domain';

type SessionContextValue = {
  session: SessionUser | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
};

const ANONYMOUS_SESSION: SessionUser = {
  authenticated: false,
  admin: false,
  email: null,
  displayName: 'Invitado',
  authorities: [],
};

const SessionContext = createContext<SessionContextValue | null>(
  null
);

function getErrorMessage(
  cause: unknown,
  fallback: string
): string {
  if (
    cause instanceof Error &&
    cause.message.trim().length > 0
  ) {
    return cause.message;
  }

  return fallback;
}

async function readSession(): Promise<SessionUser> {
  try {
    return await apiRequest<SessionUser>('/api/auth/me');
  } catch (cause: unknown) {
    if (
      cause instanceof ApiError &&
      cause.status === 401
    ) {
      return ANONYMOUS_SESSION;
    }

    throw cause;
  }
}

type SessionProviderProps = {
  children: ReactNode;
};

export function SessionProvider({
  children,
}: SessionProviderProps) {
  const [session, setSession] = useState<SessionUser | null>(
    null
  );

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(
    null
  );

  // Permite descartar respuestas antiguas.
  const requestIdRef = useRef(0);

  // Evita actualizar la sesión durante el logout.
  const loggingOutRef = useRef(false);

  const runRefresh = useCallback(
    async (initial = false): Promise<void> => {
      if (loggingOutRef.current) {
        return;
      }

      const requestId = ++requestIdRef.current;

      if (!initial) {
        setLoading(true);
        setError(null);
      }

      try {
        const currentSession = await readSession();

        // Ignorar respuestas obsoletas.
        if (
          requestId !== requestIdRef.current ||
          loggingOutRef.current
        ) {
          return;
        }

        setSession(currentSession);
        setError(null);
      } catch (cause: unknown) {
        if (
          requestId !== requestIdRef.current ||
          loggingOutRef.current
        ) {
          return;
        }

        setSession(null);

        setError(
          getErrorMessage(
            cause,
            'No se pudo verificar la sesión'
          )
        );
      } finally {
        if (
          requestId === requestIdRef.current &&
          !loggingOutRef.current
        ) {
          setLoading(false);
        }
      }
    },
    []
  );

  const refresh = useCallback(
    async (): Promise<void> => {
      await runRefresh();
    },
    [runRefresh]
  );

  // Verificar la sesión al montar el Provider.
  useEffect(() => {
    let active = true;

    // Ejecutar la verificación fuera de la fase
    // síncrona del efecto.
    void Promise.resolve().then(() => {
      if (active) {
        return runRefresh(true);
      }
    });

    return () => {
      active = false;

      // CONSERVA AQUÍ tu lógica actual de limpieza
      // que invalida las respuestas HTTP pendientes.
    };
  }, [runRefresh]);

  const logout = useCallback(
    async (): Promise<void> => {
      if (loggingOutRef.current) {
        return;
      }

      loggingOutRef.current = true;

      // Invalida cualquier refresh pendiente.
      const logoutRequestId = ++requestIdRef.current;

      setLoading(true);
      setError(null);

      try {
        await logoutSession();

        if (logoutRequestId !== requestIdRef.current) {
          return;
        }

        setSession(ANONYMOUS_SESSION);

        // Reemplaza la ruta para evitar volver
        // a la página protegida mediante "Atrás".
        window.location.replace('/');
      } catch (cause: unknown) {
        if (logoutRequestId === requestIdRef.current) {
          setError(
            getErrorMessage(
              cause,
              'No se pudo cerrar la sesión'
            )
          );
        }
      } finally {
        loggingOutRef.current = false;

        if (logoutRequestId === requestIdRef.current) {
          setLoading(false);
        }
      }
    },
    []
  );

  const value = useMemo<SessionContextValue>(
    () => ({
      session,
      loading,
      error,
      refresh,
      logout,
    }),
    [session, loading, error, refresh, logout]
  );

  return (
    <SessionContext.Provider value={value}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);

  if (context === null) {
    throw new Error(
      'useSession debe utilizarse dentro de SessionProvider'
    );
  }

  return context;
}
