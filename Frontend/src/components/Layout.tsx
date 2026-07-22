import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/** Shared page chrome: top navigation + content outlet. */
export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/', { replace: true });
  }

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-2 text-sm font-medium rounded-md transition-colors ${
      isActive ? 'text-indigo-700 bg-indigo-50' : 'text-slate-600 hover:text-slate-900'
    }`;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <Link to="/" className="text-xl font-bold tracking-tight text-indigo-700">
            TalentPipe
          </Link>
          <div className="flex items-center gap-1">
            <NavLink to="/jobs" className={navLinkClass}>
              Browse jobs
            </NavLink>
            {user ? (
              <>
                {/* The dashboard is the company workspace; candidates have no
                    tenant, so it isn't shown to them. */}
                {user.role !== 'CANDIDATE' && (
                  <NavLink to="/dashboard" className={navLinkClass}>
                    Dashboard
                  </NavLink>
                )}
                <button
                  onClick={() => void handleLogout()}
                  className="ml-2 rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:text-slate-900"
                >
                  Log out
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" className={navLinkClass}>
                  Log in
                </NavLink>
                <NavLink
                  to="/register"
                  className="ml-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
                >
                  Get started
                </NavLink>
              </>
            )}
          </div>
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-10">
        <Outlet />
      </main>
    </div>
  );
}
