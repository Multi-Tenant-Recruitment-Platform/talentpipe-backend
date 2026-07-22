import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, apiErrorMessage } from '../api/client';
import type { CandidateRegisterRequest } from '../api/types';
import { AuthShell } from '../components/AuthShell';
import { RegisterTabs } from '../components/RegisterTabs';

/** Small divider that labels a group of fields inside the form. */
function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-3" aria-hidden="true">
      <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">{children}</span>
      <span className="h-px flex-1 bg-slate-200" />
    </div>
  );
}

/**
 * Candidate self-registration (public, no tenant). On success the candidate
 * continues at the login page with the Candidate tab pre-selected.
 */
export function CandidateRegisterPage() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [identityCardNumber, setIdentityCardNumber] = useState('');
  const [address, setAddress] = useState('');
  const [contactNumber, setContactNumber] = useState('');
  const [email, setEmail] = useState('');
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
    setSubmitting(true);
    try {
      const request: CandidateRegisterRequest = {
        fullName,
        identityCardNumber,
        address,
        contactNumber,
        email,
        password,
      };
      await api.post('/public/candidates/register', request);
      // Registration issues no tokens: continue at the candidate login tab.
      navigate('/login', { state: { mode: 'candidate', registered: 'candidate', email } });
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
    <AuthShell>
      <h1 className="text-2xl font-bold tracking-tight">Create your account</h1>
      <p className="mt-2 text-sm text-slate-600">
        Choose how you want to use TalentPipe.
      </p>

      <div className="mt-6">
        <RegisterTabs active="candidate" />
      </div>

      <h2 className="mt-6 text-lg font-semibold tracking-tight">Create your candidate account</h2>
      <p className="mt-1 text-sm text-slate-600">
        One account for every company hiring on TalentPipe — apply once, get discovered again.
      </p>

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-6 space-y-5">
        {error && (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <SectionLabel>Personal details</SectionLabel>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="fullName" className="block text-sm font-medium text-slate-700">
              Full name
            </label>
            <input
              id="fullName"
              required
              maxLength={200}
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className={inputClass}
              autoComplete="name"
            />
          </div>
          <div>
            <label htmlFor="identityCardNumber" className="block text-sm font-medium text-slate-700">
              ID card number
            </label>
            <input
              id="identityCardNumber"
              required
              maxLength={30}
              value={identityCardNumber}
              onChange={(e) => setIdentityCardNumber(e.target.value)}
              className={inputClass}
              autoComplete="off"
            />
          </div>
        </div>

        <div>
          <label htmlFor="address" className="block text-sm font-medium text-slate-700">
            Address
          </label>
          <input
            id="address"
            required
            maxLength={500}
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            className={inputClass}
            autoComplete="street-address"
          />
        </div>

        <SectionLabel>Contact details</SectionLabel>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="contactNumber" className="block text-sm font-medium text-slate-700">
              Contact number
            </label>
            <input
              id="contactNumber"
              type="tel"
              required
              maxLength={20}
              value={contactNumber}
              onChange={(e) => setContactNumber(e.target.value)}
              className={inputClass}
              autoComplete="tel"
              placeholder="+94 77 123 4567"
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
              maxLength={255}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
              autoComplete="email"
            />
          </div>
        </div>

        <SectionLabel>Account security</SectionLabel>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
              minLength={8}
              maxLength={72}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={inputClass}
              autoComplete="new-password"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {submitting ? 'Creating account…' : 'Create candidate account'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-semibold text-indigo-600 hover:text-indigo-500">
          Log in
        </Link>
      </p>
    </AuthShell>
  );
}
