'use client';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, apiRequest, logoutSession } from '@/lib/api-client';
import type { SessionUser } from '@/types/domain';

type SessionContextValue = {
  session: SessionUser | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
};
const anonymous: SessionUser = {authenticated:false, admin:false, email:null, displayName:'Invitado', authorities:[]};
async function readSession(): Promise<SessionUser> {
  try { return await apiRequest<SessionUser>('/api/auth/me'); }
  catch (error) { if (error instanceof ApiError && error.status === 401) return anonymous; throw error; }
}
const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setSession(await readSession());
      setError(null);
    } catch (cause) {
      setSession(null);
      setError(cause instanceof Error ? cause.message : 'No se pudo verificar la sesión');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    readSession()
      .then(data => {
        if (!active) return;
        setSession(data);
        setError(null);
      })
      .catch(cause => {
        if (!active) return;
        setSession(null);
        setError(cause instanceof Error ? cause.message : 'No se pudo verificar la sesión');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, []);

  const logout = useCallback(async () => {
    await logoutSession();
    setSession({authenticated:false, admin:false, email:null, displayName:'Invitado', authorities:[]});
    window.location.assign('/');
  }, []);

  const value = useMemo(() => ({session, loading, error, refresh, logout}), [session, loading, error, refresh, logout]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession debe utilizarse dentro de SessionProvider');
  return value;
}
