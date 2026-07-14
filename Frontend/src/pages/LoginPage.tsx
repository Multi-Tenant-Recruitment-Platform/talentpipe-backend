import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

interface LoginLocationState {
  subdomain?: string;
  registered?: boolean;
  from?: string;
}

/**
 * Login (PB-007). Collects the company subdomain alongside the credentials;
 * the subdomain travels as the X-Tenant-Subdomain header (ADR-1) because
 * localhost has no real subdomains this week.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LoginLocationState;

  const [subdomain, setSubdomain] = useState(state.subdomain ?? '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(subdomain.trim().toLowerCase(), email, password);
      navigate(state.from ?? '/dashboard', { replace: true });
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'Login failed. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
    'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

  return (
    <section className="mx-auto max-w-md">
      <h1 className="text-3xl font-bold tracking-tight">Log in</h1>
      <p className="mt-2 text-sm text-slate-600">Welcome back to your company workspace.</p>

      {state.registered && (
        <div className="mt-6 rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">
          Company registered successfully — log in to continue.
        </div>
      )}

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-6 space-y-5 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        {error && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="subdomain" className="block text-sm font-medium text-slate-700">
            Company subdomain
          </label>
          <input
            id="subdomain"
            required
            value={subdomain}
            onChange={(e) => setSubdomain(e.target.value)}
            className={inputClass}
            placeholder="acme"
            autoComplete="organization"
          />
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
            autoComplete="email"
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="password"
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
            autoComplete="current-password"
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-60"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </section>
  );
}
