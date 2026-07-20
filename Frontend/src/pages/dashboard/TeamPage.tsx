import { useCallback, useEffect, useState } from 'react';
import { apiErrorMessage } from '../../api/client';
import { teamApi } from '../../api/team';
import type { UserResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Avatar } from '../../components/dashboard/Avatar';
import { Badge, type BadgeTone } from '../../components/dashboard/Badge';
import { Card } from '../../components/dashboard/Card';
import { Icon } from '../../components/dashboard/Icon';
import { InviteMemberModal, type InviteFormValues } from '../../components/dashboard/InviteMemberModal';
import { PageHeader } from '../../components/dashboard/PageHeader';
import { StatCard } from '../../components/dashboard/StatCard';
import { formatRole } from '../../utils/format';

const ROLE_BADGE_TONE: Record<string, BadgeTone> = {
  COMPANY_ADMIN: 'slate',
  HR_MANAGER: 'indigo',
  INTERVIEWER: 'violet',
};

/** Renders an ISO timestamp as a short, locale-aware date. */
function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

/**
 * Team & invitations: the company admin's control centre for bringing HR
 * managers and interviewers onto the platform (PB-003 / PB-004).
 *
 * <p>Backed by the real invitations API. The tenant is never sent — the
 * backend derives it from the access token — so this page cannot address
 * another company's workspace even if asked to.</p>
 */
export function TeamPage() {
  const { user } = useAuth();

  const [members, setMembers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [resentIds, setResentIds] = useState<Set<string>>(new Set());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setMembers(await teamApi.list());
      setError(null);
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'We could not load your team right now.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // The signed-in admin is already in this list; everyone else is a teammate.
  const others = members.filter((member) => member.id !== user?.id);
  const activeMembers = others.filter((member) => member.status === 'ACTIVE');
  const pendingInvites = others.filter((member) => member.status === 'INVITED');

  const hrCount = activeMembers.filter((m) => m.role === 'HR_MANAGER').length;
  const interviewerCount = activeMembers.filter((m) => m.role === 'INTERVIEWER').length;

  async function handleInvite(values: InviteFormValues) {
    setError(null);
    try {
      await teamApi.invite({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        role: values.role,
      });
      setInviteOpen(false);
      setNotice(`Invitation sent to ${values.email} as ${formatRole(values.role)}.`);
      await load();
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'We could not send that invitation.'));
    }
  }

  async function handleResend(userId: string) {
    setBusyId(userId);
    setError(null);
    try {
      await teamApi.resend(userId);
      setResentIds((current) => new Set(current).add(userId));
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'We could not re-send that invitation.'));
    } finally {
      setBusyId(null);
    }
  }

  async function handleRevoke(userId: string) {
    setBusyId(userId);
    setError(null);
    try {
      await teamApi.revoke(userId);
      await load();
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'We could not revoke that invitation.'));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Team & invitations"
        subtitle="Manage who can access your hiring workspace and what they can do."
      >
        <button
          type="button"
          onClick={() => setInviteOpen(true)}
          className="inline-flex items-center gap-2 rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
        >
          <Icon name="user-plus" className="h-4 w-4" />
          Invite member
        </button>
      </PageHeader>

      {notice && (
        <div
          role="status"
          className="mb-6 flex items-start justify-between gap-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
        >
          <p>{notice}</p>
          <button
            type="button"
            onClick={() => setNotice(null)}
            aria-label="Dismiss"
            className="rounded p-0.5 text-emerald-400 hover:text-emerald-600"
          >
            <Icon name="x-mark" className="h-4 w-4" />
          </button>
        </div>
      )}

      {error && (
        <div role="alert" className="mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="grid gap-5 sm:grid-cols-3">
        <StatCard label="HR Managers" value={String(hrCount)} icon="briefcase" tone="indigo" />
        <StatCard label="Interviewers" value={String(interviewerCount)} icon="identification" tone="violet" />
        <StatCard label="Pending invites" value={String(pendingInvites.length)} icon="envelope" tone="amber" />
      </div>

      {/* Members */}
      <Card
        title="Members"
        subtitle={loading ? 'Loading…' : `${activeMembers.length + (user ? 1 : 0)} people with workspace access`}
        className="mt-6"
        bodyClassName="overflow-x-auto"
      >
        <table className="min-w-full divide-y divide-slate-100 text-left">
          <thead>
            <tr className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              <th scope="col" className="px-6 py-3">Member</th>
              <th scope="col" className="px-6 py-3">Role</th>
              <th scope="col" className="px-6 py-3">Status</th>
              <th scope="col" className="px-6 py-3">Joined</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {/* The signed-in admin always leads the list. */}
            {user && (
              <tr className="bg-indigo-50/40">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <Avatar firstName={user.firstName} lastName={user.lastName} size="sm" />
                    <div>
                      <p className="text-sm font-semibold text-slate-900">
                        {user.firstName} {user.lastName}
                        <span className="ml-2 text-xs font-normal text-indigo-600">You</span>
                      </p>
                      <p className="text-xs text-slate-500">{user.email}</p>
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <Badge tone={ROLE_BADGE_TONE[user.role] ?? 'slate'}>{formatRole(user.role)}</Badge>
                </td>
                <td className="px-6 py-4"><Badge tone="emerald">Active</Badge></td>
                <td className="px-6 py-4 text-sm text-slate-500">Owner</td>
              </tr>
            )}
            {activeMembers.map((member) => (
              <tr key={member.id} className="hover:bg-slate-50/60">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <Avatar firstName={member.firstName} lastName={member.lastName} size="sm" />
                    <div>
                      <p className="text-sm font-semibold text-slate-900">
                        {member.firstName} {member.lastName}
                      </p>
                      <p className="text-xs text-slate-500">{member.email}</p>
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <Badge tone={ROLE_BADGE_TONE[member.role] ?? 'slate'}>{formatRole(member.role)}</Badge>
                </td>
                <td className="px-6 py-4"><Badge tone="emerald">Active</Badge></td>
                <td className="px-6 py-4 text-sm text-slate-500">{formatDate(member.createdAt)}</td>
              </tr>
            ))}
            {!loading && activeMembers.length === 0 && (
              <tr>
                <td colSpan={4} className="px-6 py-8 text-center text-sm text-slate-500">
                  No teammates yet — invite an HR manager or interviewer to get started.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>

      {/* Pending invitations */}
      <Card
        title="Pending invitations"
        subtitle="People who haven't accepted yet — invitations expire after 7 days"
        className="mt-6"
        bodyClassName={pendingInvites.length === 0 ? 'p-10 text-center' : 'divide-y divide-slate-100'}
      >
        {pendingInvites.length === 0 ? (
          <div>
            <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
              <Icon name="check" className="h-6 w-6" />
            </span>
            <p className="mt-3 text-sm font-semibold text-slate-900">All caught up</p>
            <p className="mt-1 text-sm text-slate-500">No outstanding invitations right now.</p>
          </div>
        ) : (
          pendingInvites.map((invite) => {
            const resent = resentIds.has(invite.id);
            const busy = busyId === invite.id;
            return (
              <div key={invite.id} className="flex flex-wrap items-center gap-4 px-6 py-4 first:pt-5 last:pb-5">
                <Avatar firstName={invite.firstName} lastName={invite.lastName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-slate-900">
                    {invite.firstName} {invite.lastName}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {invite.email} · invited {formatDate(invite.createdAt)}
                  </p>
                </div>
                <Badge tone={ROLE_BADGE_TONE[invite.role] ?? 'slate'}>{formatRole(invite.role)}</Badge>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void handleResend(invite.id)}
                    disabled={resent || busy}
                    className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold disabled:opacity-60 ${
                      resent
                        ? 'cursor-default bg-emerald-50 text-emerald-700'
                        : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                    }`}
                  >
                    <Icon name={resent ? 'check' : 'send'} className="h-3.5 w-3.5" />
                    {resent ? 'Sent' : 'Resend'}
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleRevoke(invite.id)}
                    disabled={busy}
                    className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50 disabled:opacity-60"
                  >
                    <Icon name="trash" className="h-3.5 w-3.5" />
                    Revoke
                  </button>
                </div>
              </div>
            );
          })
        )}
      </Card>

      <InviteMemberModal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvite={(values) => void handleInvite(values)}
      />
    </>
  );
}
