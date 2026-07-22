import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * Auth guard for private pages. Waits for the initial session restore before
 * deciding, so a hard reload on /dashboard doesn't bounce a valid session to
 * the login page.
 *
 * <p>{@code deniedRoles} lets a route exclude roles that are authenticated but
 * don't belong there — a candidate typing {@code /dashboard} is logged in, yet
 * the company workspace would only serve them 403s, so they're redirected to a
 * page that is theirs.</p>
 */
export function ProtectedRoute({
  children,
  deniedRoles = [],
  redirectTo = '/jobs',
}: {
  children: ReactNode;
  deniedRoles?: string[];
  redirectTo?: string;
}) {
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

  if (deniedRoles.includes(user.role)) {
    return <Navigate to={redirectTo} replace />;
  }

  return <>{children}</>;
}
