import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * Auth guard for private pages. Waits for the initial session restore before
 * deciding, so a hard reload on /dashboard doesn't bounce a valid session to
 * the login page.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center text-slate-500">
        Loading session…
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}
