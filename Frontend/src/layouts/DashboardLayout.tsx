import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/dashboard/Avatar';
import { Badge } from '../components/dashboard/Badge';
import { Icon, type IconName } from '../components/dashboard/Icon';
import { planUsage } from '../data/mockDashboard';
import { formatRole } from '../utils/format';

const NAV_ITEMS: { to: string; label: string; icon: IconName; end?: boolean }[] = [
  { to: '/dashboard', label: 'Overview', icon: 'squares-2x2', end: true },
  { to: '/dashboard/team', label: 'Team', icon: 'users' },
  { to: '/dashboard/pipeline', label: 'Pipeline', icon: 'funnel' },
  { to: '/dashboard/settings', label: 'Company Settings', icon: 'cog' },
];

/** Mock notification list — TODO(sprint2): wire to the notification module. */
const NOTIFICATIONS = [
  { id: 'n-1', icon: 'calendar' as IconName, text: 'Interview with Malith Jayasuriya starts in 30 minutes', time: 'Just now' },
  { id: 'n-2', icon: 'user-plus' as IconName, text: 'Kasun Silva accepted your invitation', time: '1 hour ago' },
  { id: 'n-3', icon: 'briefcase' as IconName, text: '5 new applications for UX Designer', time: '3 hours ago' },
];

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const seatsPercent = Math.round((planUsage.seatsUsed / planUsage.seatsTotal) * 100);

  return (
    <div className="flex h-full flex-col bg-slate-900">
      {/* Brand */}
      <div className="flex h-16 items-center gap-2.5 border-b border-white/10 px-6">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 text-white">
          <Icon name="funnel" className="h-4 w-4" />
        </span>
        <span className="text-lg font-bold tracking-tight text-white">TalentPipe</span>
      </div>

      {/* Workspace identity */}
      <div className="mx-4 mt-5 flex items-center gap-3 rounded-lg bg-white/5 px-3 py-2.5">
        <Avatar firstName={user?.tenantName ?? 'Workspace'} size="sm" className="rounded-md" />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-white">{user?.tenantName ?? 'Workspace'}</p>
          <p className="text-xs text-slate-400">Company workspace</p>
        </div>
      </div>

      {/* Primary navigation */}
      <nav className="mt-6 flex-1 space-y-1 px-4">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-md shadow-indigo-950/40'
                  : 'text-slate-400 hover:bg-white/5 hover:text-white'
              }`
            }
          >
            <Icon name={item.icon} className="h-5 w-5 shrink-0" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Plan usage */}
      <div className="mx-4 mb-4 rounded-xl border border-white/10 bg-white/5 p-4">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">Plan</span>
          <Badge tone="indigo">{planUsage.tier}</Badge>
        </div>
        <p className="mt-3 text-xs text-slate-400">
          <span className="font-semibold text-white">{planUsage.seatsUsed}</span> of {planUsage.seatsTotal} seats used
        </p>
        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-white/10">
          <div
            className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-violet-500"
            style={{ width: `${seatsPercent}%` }}
          />
        </div>
        <button
          type="button"
          className="mt-3 w-full rounded-md bg-white/10 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-white/20"
          title="Billing arrives in a later sprint"
        >
          Manage plan
        </button>
      </div>

      {/* Footer links */}
      <div className="border-t border-white/10 px-4 py-4">
        <Link
          to="/"
          onClick={onNavigate}
          className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-400 transition-colors hover:bg-white/5 hover:text-white"
        >
          <Icon name="arrow-left" className="h-5 w-5" />
          Back to site
        </Link>
      </div>
    </div>
  );
}

/**
 * Dedicated chrome for the company admin area: dark sidebar navigation,
 * a top bar with search / notifications / account, and the routed content
 * outlet. Collapses to a slide-over drawer on small screens.
 */
export function DashboardLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [unread, setUnread] = useState(true);

  async function handleLogout() {
    await logout();
    navigate('/', { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 antialiased">
      {/* Mobile drawer */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            onClick={() => setSidebarOpen(false)}
            className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm"
          />
          <aside className="absolute inset-y-0 left-0 w-72 shadow-2xl">
            <SidebarContent onNavigate={() => setSidebarOpen(false)} />
          </aside>
        </div>
      )}

      {/* Static sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">
        <SidebarContent />
      </aside>

      <div className="flex min-h-screen flex-col lg:pl-64">
        {/* Top bar */}
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            className="rounded-md p-2 text-slate-500 hover:bg-slate-100 lg:hidden"
            aria-label="Open navigation"
          >
            <Icon name="menu" className="h-5 w-5" />
          </button>

          {/* Global search — decorative until the search API lands. */}
          <div className="relative hidden max-w-md flex-1 sm:block">
            <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            {/* TODO(sprint2): wire to global search once it exists. */}
            <input
              type="search"
              placeholder="Search jobs, candidates, people…"
              className="w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pl-9 pr-3 text-sm text-slate-700 placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          <div className="ml-auto flex items-center gap-1.5">
            {/* Notifications */}
            <div className="relative">
              <button
                type="button"
                onClick={() => {
                  setNotificationsOpen((v) => !v);
                  setUnread(false);
                }}
                className="relative rounded-full p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Notifications"
                aria-expanded={notificationsOpen}
              >
                <Icon name="bell" className="h-5 w-5" />
                {unread && <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-white" />}
              </button>
              {notificationsOpen && (
                <>
                  <button
                    type="button"
                    aria-label="Dismiss notifications"
                    onClick={() => setNotificationsOpen(false)}
                    className="fixed inset-0 z-10 cursor-default"
                  />
                  <div className="absolute right-0 z-20 mt-2 w-80 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
                    <p className="border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-900">Notifications</p>
                    <ul className="divide-y divide-slate-100">
                      {NOTIFICATIONS.map((n) => (
                        <li key={n.id} className="flex gap-3 px-4 py-3 hover:bg-slate-50">
                          <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
                            <Icon name={n.icon} className="h-4 w-4" />
                          </span>
                          <div>
                            <p className="text-sm leading-snug text-slate-700">{n.text}</p>
                            <p className="mt-0.5 text-xs text-slate-400">{n.time}</p>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </div>
                </>
              )}
            </div>

            {/* Account */}
            <div className="ml-1 flex items-center gap-3 border-l border-slate-200 pl-3">
              <Avatar firstName={user?.firstName ?? '?'} lastName={user?.lastName} size="sm" />
              <div className="hidden sm:block">
                <p className="text-sm font-semibold leading-tight text-slate-900">
                  {user?.firstName} {user?.lastName}
                </p>
                <p className="text-xs leading-tight text-slate-500">{user ? formatRole(user.role) : ''}</p>
              </div>
              <button
                type="button"
                onClick={() => void handleLogout()}
                className="rounded-md p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Log out"
                title="Log out"
              >
                <Icon name="logout" className="h-5 w-5" />
              </button>
            </div>
          </div>
        </header>

        {/* Routed dashboard content */}
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
