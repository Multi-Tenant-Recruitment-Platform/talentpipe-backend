import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';

/**
 * Password-reset request. The confirmation is intentionally identical whether
 * or not the email is registered, so the endpoint can't be used to probe for
 * existing accounts.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/auth/forgot-password', { email });
    } catch {
      // Best effort — the neutral confirmation below is shown either way.
    } finally {
      setSubmitting(false);
      setSent(true);
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
    'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

  return (
    <section className="mx-auto max-w-md">
      <h1 className="text-3xl font-bold tracking-tight">Reset your password</h1>
      <p className="mt-2 text-sm text-slate-600">
        Enter your account email and we'll send you a reset link.
      </p>

      {sent ? (
        <div className="mt-6 space-y-5 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <div role="status" className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">
            If an account exists for {email}, a password reset link is on its way.
          </div>
          <Link
            to="/login"
            className="block text-center text-sm font-semibold text-indigo-600 hover:text-indigo-500"
          >
            Back to log in
          </Link>
        </div>
      ) : (
        <form
          onSubmit={(e) => void handleSubmit(e)}
          className="mt-6 space-y-5 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
        >
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

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-60"
          >
            {submitting ? 'Sending…' : 'Send reset link'}
          </button>

          <Link
            to="/login"
            className="block text-center text-sm font-medium text-slate-600 hover:text-slate-900"
          >
            Back to log in
          </Link>
        </form>
      )}
    </section>
  );
}
