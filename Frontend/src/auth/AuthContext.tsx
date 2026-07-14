import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api, SESSION_EXPIRED_EVENT } from '../api/client';
import { tokenStore } from '../api/tokenStore';
import type { AuthResponse, RegisterRequest, RegisterResponse, UserResponse } from '../api/types';

/**
 * Application auth state. The access token itself lives in the token store
 * (in memory); this context owns the user profile and the login/logout
 * transitions the UI reacts to.
 */
interface AuthContextValue {
  /** Authenticated user, or null when signed out. */
  user: UserResponse | null;
  /** True while the initial session restore (refresh-token exchange) runs. */
  initializing: boolean;
  login: (subdomain: string, email: string, password: string) => Promise<UserResponse>;
  register: (request: RegisterRequest) => Promise<RegisterResponse>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initializing, setInitializing] = useState(true);

  const applyAuth = useCallback((auth: AuthResponse) => {
    tokenStore.setAccessToken(auth.accessToken);
    tokenStore.setRefreshToken(auth.refreshToken);
    setUser(auth.user);
  }, []);

  const clearAuth = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  // Restore the session on hard reload: the access token is memory-only, so
  // exchange the persisted refresh token for a fresh pair once at startup.
  useEffect(() => {
    let cancelled = false;
    async function restore() {
      const refreshToken = tokenStore.getRefreshToken();
      if (!refreshToken) {
        setInitializing(false);
        return;
      }
      try {
        const { data } = await api.post<AuthResponse>('/auth/refresh', { refreshToken });
        if (!cancelled) {
          applyAuth(data);
        }
      } catch {
        if (!cancelled) {
          clearAuth();
        }
      } finally {
        if (!cancelled) {
          setInitializing(false);
        }
      }
    }
    void restore();
    return () => {
      cancelled = true;
    };
  }, [applyAuth, clearAuth]);

  // The API client signals an unrecoverable 401 (refresh failed) here.
  useEffect(() => {
    const onExpired = () => clearAuth();
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, [clearAuth]);

  const login = useCallback(
    async (subdomain: string, email: string, password: string) => {
      // Tenant identity travels in the header, never the body (ADR-1).
      const { data } = await api.post<AuthResponse>(
        '/auth/login',
        { email, password },
        { headers: { 'X-Tenant-Subdomain': subdomain } },
      );
      applyAuth(data);
      return data.user;
    },
    [applyAuth],
  );

  const register = useCallback(async (request: RegisterRequest) => {
    const { data } = await api.post<RegisterResponse>('/auth/register', request);
    return data;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    try {
      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken });
      }
    } catch {
      // Best effort — the server-side token expires regardless; the client
      // session is cleared either way.
    } finally {
      clearAuth();
    }
  }, [clearAuth]);

  const value = useMemo(
    () => ({ user, initializing, login, register, logout }),
    [user, initializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
