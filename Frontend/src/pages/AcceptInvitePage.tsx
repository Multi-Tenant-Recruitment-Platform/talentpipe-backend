import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, apiErrorMessage } from '../api/client';
import { AuthShell } from '../components/AuthShell';

/**
 * Landing page for the link in an invitation email (PB-003 / PB-004). Setting
 * a password accepts the invitation and activates the account.
 *
 * <p>The tenant and role are fixed by the invitation itself — nothing here can
 * influence which workspace or role the account joins.</p>
 */
export function AcceptInvitePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }

    setSubmitting(true);
    try {
      await api.post('/auth/accept-invite', { token, password });
      navigate('/login', { state: { mode: 'company', inviteAccepted: true } });
    } catch (err: unknown) {
      setError(
        apiErrorMessage(err, 'We could not accept this invitation. It may have expired or already been used.'),
      );
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
    'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

  if (!token) {
    return (
      <AuthShell>
        <h1 className="text-2xl font-bold tracking-tight">This link is incomplete</h1>
        <p className="mt-2 text-sm text-slate-600">
          The invitation token is missing. Please open the link directly from your invitation email.
        </p>
      </AuthShell>
    );
  }

  return (
    <AuthShell>
      <h1 className="text-2xl font-bold tracking-tight">Accept your invitation</h1>
      <p className="mt-2 text-sm text-slate-600">
        Choose a password to activate your account and join the workspace.
      </p>

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-6 space-y-5">
        {error && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="password"
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
            autoComplete="new-password"
          />
          <p className="mt-1 text-xs text-slate-500">At least 8 characters.</p>
        </div>

        <div>
          <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700">
            Confirm password
          </label>
          <input
            id="confirmPassword"
            type="password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={inputClass}
            autoComplete="new-password"
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {submitting ? 'Activating…' : 'Accept invitation'}
        </button>

        <Link to="/login" className="block text-center text-sm font-medium text-slate-600 hover:text-slate-900">
          Back to sign in
        </Link>
      </form>
    </AuthShell>
  );
}
