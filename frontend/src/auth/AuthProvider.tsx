import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, ApiError, type AuthResponse, type User } from '@/lib/api';
interface AuthState { user: User | null; loading: boolean; authenticate: (mode: 'login' | 'register', fields: Record<string, string>) => Promise<void>; logout: () => Promise<void>; request: <T>(path: string, options?: RequestInit) => Promise<T> }
const Context = createContext<AuthState | null>(null);
const storageKey = 'campusdesk.session';
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const token = useRef<string | null>(sessionStorage.getItem(storageKey));
  const expiryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const clear = useCallback(() => { clearTimeout(expiryTimer.current); token.current = null; sessionStorage.removeItem(storageKey); setUser(null); }, []);
  const scheduleExpiry = useCallback((value: string) => {
    try {
      const payload = JSON.parse(atob(value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      expiryTimer.current = setTimeout(clear, Math.max(0, payload.exp * 1000 - Date.now()));
    } catch { clear(); }
  }, [clear]);
  useEffect(() => {
    let mounted = true;
    if (!token.current) { setLoading(false); return; }
    scheduleExpiry(token.current);
    api<User>('/auth/me', token.current).then(value => { if (mounted) setUser(value); }).catch(() => { if (mounted) clear(); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; clearTimeout(expiryTimer.current); };
  }, [clear, scheduleExpiry]);
  const authenticate = async (mode: 'login' | 'register', fields: Record<string, string>) => {
    const result = await api<AuthResponse>(`/auth/${mode}`, undefined, { method: 'POST', body: JSON.stringify(fields) });
    clearTimeout(expiryTimer.current); token.current = result.accessToken; sessionStorage.setItem(storageKey, result.accessToken); setUser(result.user); scheduleExpiry(result.accessToken);
  };
  const logout = async () => {
    if (token.current) {
      try { await api<void>('/auth/logout', token.current, { method: 'POST' }); }
      catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error; }
    }
    clear();
  };
  const request = useCallback(async <T,>(path: string, options?: RequestInit): Promise<T> => {
    try { return await api<T>(path, token.current ?? undefined, options); }
    catch (error) { if (error instanceof ApiError && error.status === 401) clear(); throw error; }
  }, [clear]);
  return <Context.Provider value={{ user, loading, authenticate, logout, request }}>{children}</Context.Provider>;
}
export function useAuth() { const context = useContext(Context); if (!context) throw new Error('AuthProvider is required'); return context; }
