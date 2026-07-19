import { useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Avatar } from '../../components/dashboard/Avatar';
import { Badge, type BadgeTone } from '../../components/dashboard/Badge';
import { Card } from '../../components/dashboard/Card';
import { Icon } from '../../components/dashboard/Icon';
import { InviteMemberModal, type InviteFormValues } from '../../components/dashboard/InviteMemberModal';
import { PageHeader } from '../../components/dashboard/PageHeader';
import { StatCard } from '../../components/dashboard/StatCard';
import {
  pendingInvites as seedInvites,
  teamMembers,
  type PendingInvite,
  type TeamRole,
} from '../../data/mockDashboard';
import { formatRole } from '../../utils/format';

const ROLE_BADGE_TONE: Record<TeamRole, BadgeTone> = {
  COMPANY_ADMIN: 'slate',
  HR_MANAGER: 'indigo',
  INTERVIEWER: 'violet',
};

/**
 * Team & invitations: the company admin's control centre for bringing HR
 * managers and interviewers onto the platform, and for seeing who already
 * has access to the workspace.
 *
 * TODO(sprint1-w2): back every mutation here with the invitations API.
 */
export function TeamPage() {
  const { user } = useAuth();
  const [invites, setInvites] = useState<PendingInvite[]>(seedInvites);
  const [resentIds, setResentIds] = useState<Set<string>>(new Set());
  const [inviteOpen, setInviteOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const hrCount = teamMembers.filter((m) => m.role === 'HR_MANAGER').length;
  const interviewerCount = teamMembers.filter((m) => m.role === 'INTERVIEWER').length;

  function handleInvite(values: InviteFormValues) {
    // TODO(sprint1-w2): POST /invitations.
    setInvites((current) => [
      {
        id: `inv-${Date.now()}`,
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        role: values.role,
        invitedAt: 'Just now',
        invitedBy: 'You',
      },
      ...current,
    ]);
    setInviteOpen(false);
    setNotice(`Invitation sent to ${values.email} as ${formatRole(values.role)}.`);
  }

  function handleResend(id: string) {
    // TODO(sprint1-w2): POST /invitations/{id}/resend.
    setResentIds((current) => new Set(current).add(id));
  }

  function handleRevoke(id: string) {
    // TODO(sprint1-w2): DELETE /invitations/{id}.
    setInvites((current) => current.filter((invite) => invite.id !== id));
  }

  return (
    <>
      <PageHeader title="Team & invitations" subtitle="Manage who can access your hiring workspace and what they can do.">
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
        <div role="status" className="mb-6 flex items-start justify-between gap-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <p>{notice}</p>
          <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss" className="rounded p-0.5 text-emerald-400 hover:text-emerald-600">
            <Icon name="x-mark" className="h-4 w-4" />
          </button>
        </div>
      )}

      <div className="grid gap-5 sm:grid-cols-3">
        <StatCard label="HR Managers" value={String(hrCount)} icon="briefcase" tone="indigo" />
        <StatCard label="Interviewers" value={String(interviewerCount)} icon="identification" tone="violet" />
        <StatCard label="Pending invites" value={String(invites.length)} icon="envelope" tone="amber" />
      </div>

      {/* Members */}
      <Card
        title="Members"
        subtitle={`${teamMembers.length + 1} people with workspace access`}
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
              <th scope="col" className="px-6 py-3 text-right">Actions</th>
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
                <td className="px-6 py-4"><Badge tone={ROLE_BADGE_TONE.COMPANY_ADMIN}>{formatRole(user.role)}</Badge></td>
                <td className="px-6 py-4"><Badge tone="emerald">Active</Badge></td>
                <td className="px-6 py-4 text-sm text-slate-500">Owner</td>
                <td className="px-6 py-4" />
              </tr>
            )}
            {teamMembers.map((member) => (
              <tr key={member.id} className="hover:bg-slate-50/60">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <Avatar firstName={member.firstName} lastName={member.lastName} size="sm" />
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{member.firstName} {member.lastName}</p>
                      <p className="text-xs text-slate-500">{member.email}</p>
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4"><Badge tone={ROLE_BADGE_TONE[member.role]}>{formatRole(member.role)}</Badge></td>
                <td className="px-6 py-4"><Badge tone="emerald">Active</Badge></td>
                <td className="px-6 py-4 text-sm text-slate-500">{member.joinedAt}</td>
                <td className="px-6 py-4 text-right">
                  <button
                    type="button"
                    className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                    aria-label={`Manage ${member.firstName} ${member.lastName}`}
                    title="Member management arrives with the admin API"
                  >
                    <Icon name="pencil" className="h-4 w-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {/* Pending invitations */}
      <Card
        title="Pending invitations"
        subtitle="People who haven't accepted yet — invitations expire after 7 days"
        className="mt-6"
        bodyClassName={invites.length === 0 ? 'p-10 text-center' : 'divide-y divide-slate-100'}
      >
        {invites.length === 0 ? (
          <div>
            <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
              <Icon name="check" className="h-6 w-6" />
            </span>
            <p className="mt-3 text-sm font-semibold text-slate-900">All caught up</p>
            <p className="mt-1 text-sm text-slate-500">No outstanding invitations right now.</p>
          </div>
        ) : (
          invites.map((invite) => {
            const resent = resentIds.has(invite.id);
            return (
              <div key={invite.id} className="flex flex-wrap items-center gap-4 px-6 py-4 first:pt-5 last:pb-5">
                <Avatar firstName={invite.firstName} lastName={invite.lastName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-slate-900">
                    {invite.firstName} {invite.lastName}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {invite.email} · invited {invite.invitedAt} by {invite.invitedBy}
                  </p>
                </div>
                <Badge tone={ROLE_BADGE_TONE[invite.role]}>{formatRole(invite.role)}</Badge>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => handleResend(invite.id)}
                    disabled={resent}
                    className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold ${
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
                    onClick={() => handleRevoke(invite.id)}
                    className="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50"
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

      <InviteMemberModal open={inviteOpen} onClose={() => setInviteOpen(false)} onInvite={handleInvite} />
    </>
  );
}
