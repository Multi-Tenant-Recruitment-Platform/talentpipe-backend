import { useAuth } from '../auth/AuthContext';

/** Protected placeholder — the real dashboard arrives with later modules. */
export function DashboardPage() {
  const { user } = useAuth();

  // ProtectedRoute guarantees a user here.
  if (!user) {
    return null;
  }

  return (
    <section>
      <h1 className="text-3xl font-bold tracking-tight">
        Welcome, {user.firstName}
        {user.tenantName ? ` — ${user.tenantName}` : ''}
      </h1>
      <p className="mt-2 text-slate-600">
        Signed in as {user.email} ({user.role}).
      </p>

      <div className="mt-10 rounded-lg border border-dashed border-slate-300 bg-white p-12 text-center">
        <h2 className="text-lg font-semibold text-slate-900">Your recruiting hub is on its way</h2>
        <p className="mt-2 text-sm text-slate-500">
          Jobs, candidates and pipelines land here in the upcoming sprints.
        </p>
      </div>
    </section>
  );
}
