import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, apiErrorMessage } from '../api/client';
import { AuthShell } from '../components/AuthShell';

type Status = 'verifying' | 'success' | 'error';

/**
 * Landing page for the link in a verification email (PB-001). Exchanges the
 * token from the query string for account activation, then sends the user on
 * to log in.
 *
 * <p>Works for both identities: the backend resolves whether the token belongs
 * to a company user or a candidate.</p>
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<Status>('verifying');
  const [message, setMessage] = useState('');
  // React 18 StrictMode mounts effects twice in development; the token is
  // single-use, so the second call would always report "already used".
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) {
      return;
    }
    attempted.current = true;

    if (!token) {
      setStatus('error');
      setMessage('This link is missing its verification token. Please use the link from your email.');
      return;
    }

    api
      .post('/auth/verify-email', { token })
      .then(() => setStatus('success'))
      .catch((err: unknown) => {
        setStatus('error');
        setMessage(
          apiErrorMessage(err, 'We could not verify this link. It may have expired or already been used.'),
        );
      });
  }, [token]);

  return (
    <AuthShell>
      {status === 'verifying' && (
        <>
          <h1 className="text-2xl font-bold tracking-tight">Verifying your email…</h1>
          <p className="mt-2 text-sm text-slate-600">This only takes a moment.</p>
        </>
      )}

      {status === 'success' && (
        <>
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
            </svg>
          </span>
          <h1 className="mt-4 text-2xl font-bold tracking-tight">Email verified</h1>
          <p className="mt-2 text-sm text-slate-600">
            Your account is active. You can sign in now.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-block rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
          >
            Continue to sign in
          </Link>
        </>
      )}

      {status === 'error' && (
        <>
          <h1 className="text-2xl font-bold tracking-tight">We couldn't verify this link</h1>
          <div role="alert" className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {message}
          </div>
          <p className="mt-4 text-sm text-slate-600">
            Verification links expire after 24 hours and can only be used once. Request a fresh
            one from the sign-in page.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-block text-sm font-semibold text-indigo-600 hover:text-indigo-500"
          >
            Back to sign in
          </Link>
        </>
      )}
    </AuthShell>
  );
}
