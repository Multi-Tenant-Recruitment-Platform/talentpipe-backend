import axios from 'axios';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from '../components/AuthShell';

type LoginMode = 'candidate' | 'company';

interface LoginLocationState {
  subdomain?: string;
  /** Set by the registration pages after a successful sign-up. */
  registered?: boolean | 'company' | 'candidate';
  /** Pre-selects the login tab (e.g. after candidate registration). */
  mode?: LoginMode;
  /** Prefills the email field (e.g. after candidate registration). */
  email?: string;
  /** Set by the reset-password page after a successful reset. */
  passwordReset?: boolean;
  /** Set by the accept-invite page after activation. */
  inviteAccepted?: boolean;
  from?: string;
}

const iconProps = {
  className: 'h-4 w-4',
  fill: 'none',
  viewBox: '0 0 24 24',
  strokeWidth: 1.5,
  stroke: 'currentColor',
} as const;

const MODES: { id: LoginMode; label: string; icon: ReactNode }[] = [
  {
    id: 'candidate',
    label: 'Candidate',
    icon: (
      <svg {...iconProps}>
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z"
        />
      </svg>
    ),
  },
  {
    id: 'company',
    label: 'Company',
    icon: (
      <svg {...iconProps}>
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21"
        />
      </svg>
    ),
  },
];

/**
 * Login (PB-007), split-screen edition: the brand panel showcases the
 * platform's highlights while the form serves both personas. Company users
 * log in with their subdomain, which travels as the X-Tenant-Subdomain
 * header (ADR-1); candidates authenticate globally, without a tenant.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LoginLocationState;

  const [mode, setMode] = useState<LoginMode>(state.mode ?? 'company');

  const [email, setEmail] = useState(state.email ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Set when the backend reports the account exists but is unverified (403),
  // which is the only case where offering a resend is useful — and safe,
  // because the caller has already proven they know the password.
  const [needsVerification, setNeedsVerification] = useState(false);

  function switchMode(next: LoginMode) {
    setMode(next);
    setError(null);
    setNotice(null);
    setNeedsVerification(false);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setNeedsVerification(false);
    setSubmitting(true);
    try {
      const loggedIn = await login(
        '',
        email,
        password,
      );
      // The company dashboard is for company roles only — candidates have no
      // tenant and would hit 403s there, so send them to the job board.
      const home = loggedIn.role === 'CANDIDATE' ? '/jobs' : '/dashboard';
      navigate(state.from ?? home, { replace: true });
    } catch (err: unknown) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      const message = apiErrorMessage(err, 'Login failed. Please try again.');
      setError(message);
      setNeedsVerification(status === 403 && message.toLowerCase().includes('not verified'));
    } finally {
      setSubmitting(false);
    }
  }

  /** Requests a fresh verification email; always reports the same neutral result. */
  async function handleResendVerification() {
    setError(null);
    try {
      await api.post(
        '/auth/resend-verification',
        { email },
      );
    } catch {
      // Deliberately ignored: the confirmation below must look identical
      // whether or not the address is registered.
    } finally {
      setNeedsVerification(false);
      setNotice(`If ${email} needs verifying, a new link is on its way.`);
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
    'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

  return (
    <AuthShell>
      <h1 className="text-2xl font-bold tracking-tight">Welcome back</h1>
      <p className="mt-2 text-sm text-slate-600">
        {mode === 'company'
          ? 'Sign in to your company workspace.'
          : 'Sign in to track your applications.'}
      </p>

      {state.registered && (
        <div className="mt-6 rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">
          {state.registered === 'candidate'
            ? 'Account created. Check your email for the verification link — you can sign in once it is confirmed.'
            : 'Company registered. Check your email for the verification link — you can sign in once it is confirmed.'}
        </div>
      )}

      {state.passwordReset && (
        <div className="mt-6 rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">
          Password updated. Sign in with your new password.
        </div>
      )}

      {state.inviteAccepted && (
        <div className="mt-6 rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700">
          Invitation accepted. Sign in with your email to reach the workspace.
        </div>
      )}

      {/* Persona switch: candidate vs. company login. */}
      <div role="tablist" aria-label="Login type" className="mt-6 grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1">
        {MODES.map(({ id, label, icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={mode === id}
            onClick={() => switchMode(id)}
            className={`flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
              mode === id
                ? 'bg-white text-indigo-700 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {icon}
            {label}
          </button>
        ))}
      </div>

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-6 space-y-5">
        {error && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <p>{error}</p>
            {needsVerification && (
              <button
                type="button"
                onClick={() => void handleResendVerification()}
                className="mt-2 font-semibold text-red-800 underline hover:text-red-900"
              >
                Send me a new verification link
              </button>
            )}
          </div>
        )}
        {notice && (
          <div role="status" className="rounded-md border border-indigo-200 bg-indigo-50 p-3 text-sm text-indigo-700">
            {notice}
          </div>
        )}


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
          <div className="flex items-center justify-between">
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Password
            </label>
            <Link to="/forgot-password" className="text-xs font-medium text-indigo-600 hover:text-indigo-500">
              Forgot password?
            </Link>
          </div>
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
          className="w-full rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {submitting
            ? 'Signing in…'
            : mode === 'company'
              ? 'Sign in as company'
              : 'Sign in as candidate'}
        </button>
      </form>

      <div className="mt-6 flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-slate-200" />
        <span className="text-xs font-medium uppercase tracking-wide text-slate-400">or continue with</span>
        <span className="h-px flex-1 bg-slate-200" />
      </div>

      <button
        type="button"
        onClick={() => {
          setError(null);
          setNotice('Google sign-in is coming soon.');
        }}
        className="mt-4 flex w-full items-center justify-center gap-3 rounded-md border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden="true">
          <path
            fill="#4285F4"
            d="M23.49 12.27c0-.79-.07-1.54-.19-2.27H12v4.51h6.47a5.57 5.57 0 0 1-2.4 3.58v3h3.86c2.26-2.09 3.56-5.17 3.56-8.82Z"
          />
          <path
            fill="#34A853"
            d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.86-3c-1.08.72-2.45 1.16-4.07 1.16-3.13 0-5.78-2.11-6.73-4.96H1.29v3.09A11.99 11.99 0 0 0 12 24Z"
          />
          <path
            fill="#FBBC05"
            d="M5.27 14.29A7.2 7.2 0 0 1 4.89 12c0-.8.14-1.57.38-2.29V6.62H1.29a12 12 0 0 0 0 10.76l3.98-3.09Z"
          />
          <path
            fill="#EA4335"
            d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42A11.98 11.98 0 0 0 12 0a11.99 11.99 0 0 0-10.71 6.62l3.98 3.09C6.22 6.86 8.87 4.75 12 4.75Z"
          />
        </svg>
        Continue with Google
      </button>

      {/* Registration is persona-specific, mirroring the active login tab. */}
      <p className="mt-6 text-center text-sm text-slate-600">
        New to TalentPipe?{' '}
        <Link
          to={mode === 'company' ? '/register' : '/register-candidate'}
          className="font-semibold text-indigo-600 hover:text-indigo-500"
        >
          Create an account
        </Link>
      </p>
    </AuthShell>
  );
}
