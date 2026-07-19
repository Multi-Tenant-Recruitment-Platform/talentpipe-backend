import { useEffect, useState, type FormEvent } from 'react';
import type { InvitableRole } from '../../data/mockDashboard';
import { Icon, type IconName } from './Icon';

export interface InviteFormValues {
  firstName: string;
  lastName: string;
  email: string;
  role: InvitableRole;
  note: string;
}

const ROLE_OPTIONS: { id: InvitableRole; label: string; description: string; icon: IconName }[] = [
  {
    id: 'HR_MANAGER',
    label: 'HR Manager',
    description: 'Posts jobs, manages candidates and drives the full pipeline.',
    icon: 'briefcase',
  },
  {
    id: 'INTERVIEWER',
    label: 'Interviewer',
    description: 'Joins interview panels, scores candidates and leaves feedback.',
    icon: 'identification',
  },
];

const inputClass =
  'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
  'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

/**
 * Invitation dialog used by the company admin to bring HR managers and
 * interviewers onto the platform. Closes on Escape or backdrop click.
 *
 * TODO(sprint1-w2): submit against the real invitations endpoint.
 */
export function InviteMemberModal({
  open,
  onClose,
  onInvite,
}: {
  open: boolean;
  onClose: () => void;
  onInvite: (values: InviteFormValues) => void;
}) {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<InvitableRole>('HR_MANAGER');
  const [note, setNote] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onInvite({ firstName: firstName.trim(), lastName: lastName.trim(), email: email.trim(), role, note: note.trim() });
    setFirstName('');
    setLastName('');
    setEmail('');
    setRole('HR_MANAGER');
    setNote('');
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="invite-member-title">
      <button
        type="button"
        aria-label="Close dialog"
        onClick={onClose}
        className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm"
      />
      <div className="relative w-full max-w-lg rounded-2xl bg-white shadow-2xl">
        <header className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-5">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-600 to-violet-600 text-white">
              <Icon name="user-plus" className="h-5 w-5" />
            </span>
            <div>
              <h2 id="invite-member-title" className="text-base font-semibold text-slate-900">
                Invite team member
              </h2>
              <p className="text-xs text-slate-500">They'll receive an email with a link to join your workspace.</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            aria-label="Close"
          >
            <Icon name="x-mark" className="h-5 w-5" />
          </button>
        </header>

        <form onSubmit={handleSubmit} className="space-y-5 px-6 py-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="invite-first-name" className="block text-sm font-medium text-slate-700">
                First name
              </label>
              <input
                id="invite-first-name"
                required
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                className={inputClass}
                placeholder="Amaya"
                autoComplete="off"
              />
            </div>
            <div>
              <label htmlFor="invite-last-name" className="block text-sm font-medium text-slate-700">
                Last name
              </label>
              <input
                id="invite-last-name"
                required
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                className={inputClass}
                placeholder="Rathnayake"
                autoComplete="off"
              />
            </div>
          </div>

          <div>
            <label htmlFor="invite-email" className="block text-sm font-medium text-slate-700">
              Work email
            </label>
            <input
              id="invite-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
              placeholder="name@company.com"
              autoComplete="off"
            />
          </div>

          <fieldset>
            <legend className="block text-sm font-medium text-slate-700">Role</legend>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              {ROLE_OPTIONS.map((option) => {
                const selected = role === option.id;
                return (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => setRole(option.id)}
                    aria-pressed={selected}
                    className={`rounded-lg border p-3 text-left transition-colors ${
                      selected
                        ? 'border-indigo-600 bg-indigo-50/60 ring-1 ring-indigo-600'
                        : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    <span className={`flex h-8 w-8 items-center justify-center rounded-md ${selected ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-500'}`}>
                      <Icon name={option.icon} className="h-4 w-4" />
                    </span>
                    <span className="mt-2 block text-sm font-semibold text-slate-900">{option.label}</span>
                    <span className="mt-0.5 block text-xs leading-relaxed text-slate-500">{option.description}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div>
            <label htmlFor="invite-note" className="block text-sm font-medium text-slate-700">
              Personal note <span className="font-normal text-slate-400">(optional)</span>
            </label>
            <textarea
              id="invite-note"
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className={`${inputClass} resize-none`}
              placeholder="Add a short welcome message to the invitation email…"
            />
          </div>

          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-5">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="inline-flex items-center gap-2 rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
            >
              <Icon name="send" className="h-4 w-4" />
              Send invitation
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
