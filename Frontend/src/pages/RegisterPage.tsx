import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/** Company onboarding (PB-001): tenant + first COMPANY_ADMIN, then off to login. */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [companyName, setCompanyName] = useState('');
  const [subdomain, setSubdomain] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register({
        companyName,
        subdomain: subdomain.trim().toLowerCase(),
        admin: { firstName, lastName, email, password },
      });
      // Registration issues no tokens: continue at login, subdomain prefilled.
      navigate('/login', { state: { subdomain: subdomain.trim().toLowerCase(), registered: true } });
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'Registration failed. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass =
    'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
    'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

  return (
    <section className="mx-auto max-w-lg">
      <h1 className="text-3xl font-bold tracking-tight">Register your company</h1>
      <p className="mt-2 text-sm text-slate-600">
        Creates your company workspace and its first administrator account.
      </p>

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-8 space-y-5 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        {error && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="companyName" className="block text-sm font-medium text-slate-700">
            Company name
          </label>
          <input
            id="companyName"
            required
            maxLength={255}
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            className={inputClass}
          />
        </div>

        <div>
          <label htmlFor="subdomain" className="block text-sm font-medium text-slate-700">
            Subdomain
          </label>
          <div className="mt-1 flex items-center">
            <input
              id="subdomain"
              required
              minLength={2}
              maxLength={100}
              pattern="[a-z0-9]([a-z0-9-]*[a-z0-9])?"
              title="Lowercase letters, digits and inner hyphens"
              value={subdomain}
              onChange={(e) => setSubdomain(e.target.value)}
              className={`${inputClass} mt-0 rounded-r-none`}
              placeholder="acme"
            />
            <span className="rounded-r-md border border-l-0 border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-500">
              .talentpipe.io
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">You'll use this to log in.</p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="firstName" className="block text-sm font-medium text-slate-700">
              First name
            </label>
            <input
              id="firstName"
              required
              maxLength={100}
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="lastName" className="block text-sm font-medium text-slate-700">
              Last name
            </label>
            <input
              id="lastName"
              required
              maxLength={100}
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Work email
          </label>
          <input
            id="email"
            type="email"
            required
            maxLength={255}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
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
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
          />
          <p className="mt-1 text-xs text-slate-500">At least 8 characters.</p>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-60"
        >
          {submitting ? 'Creating workspace…' : 'Create company account'}
        </button>
      </form>
    </section>
  );
}
