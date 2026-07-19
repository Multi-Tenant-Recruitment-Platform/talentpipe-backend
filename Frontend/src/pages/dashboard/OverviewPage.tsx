import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Avatar } from '../../components/dashboard/Avatar';
import { Badge } from '../../components/dashboard/Badge';
import { Card } from '../../components/dashboard/Card';
import { HiringFunnel } from '../../components/dashboard/HiringFunnel';
import { Icon, type IconName } from '../../components/dashboard/Icon';
import { InviteMemberModal, type InviteFormValues } from '../../components/dashboard/InviteMemberModal';
import { PageHeader } from '../../components/dashboard/PageHeader';
import { StatCard } from '../../components/dashboard/StatCard';
import {
  activityFeed,
  hiringFunnel,
  overviewStats,
  recruitmentHealth,
  upcomingInterviews,
} from '../../data/mockDashboard';

const INTERVIEW_TYPE_BADGE: Record<string, { tone: 'sky' | 'violet' | 'amber'; label: string }> = {
  VIDEO: { tone: 'sky', label: 'Video' },
  ONSITE: { tone: 'violet', label: 'On-site' },
  PHONE: { tone: 'amber', label: 'Phone' },
};

const ACTIVITY_TONES: Record<string, string> = {
  'user-plus': 'bg-indigo-50 text-indigo-600',
  briefcase: 'bg-violet-50 text-violet-600',
  calendar: 'bg-sky-50 text-sky-600',
  check: 'bg-emerald-50 text-emerald-600',
  envelope: 'bg-amber-50 text-amber-600',
};

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

/**
 * Company admin overview: headline KPIs, the hiring funnel, recruitment
 * health, today's interviews and the latest activity — everything an admin
 * needs to understand the state of hiring at a glance.
 */
export function OverviewPage() {
  const { user } = useAuth();
  const [inviteOpen, setInviteOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });

  function handleInvite(values: InviteFormValues) {
    // TODO(sprint1-w2): POST /invitations once the endpoint exists.
    setInviteOpen(false);
    setNotice(`Invitation sent to ${values.email} — they'll join as ${values.role === 'HR_MANAGER' ? 'an HR Manager' : 'an Interviewer'} once they accept.`);
  }

  const healthMetrics: { icon: IconName; label: string; value: string; hint: string; tone: string }[] = [
    {
      icon: 'clock',
      label: 'Avg. time-to-hire',
      value: `${recruitmentHealth.avgTimeToHireDays} days`,
      hint: '2 days faster than last quarter',
      tone: 'bg-indigo-50 text-indigo-600',
    },
    {
      icon: 'check',
      label: 'Offer acceptance',
      value: `${recruitmentHealth.offerAcceptanceRate}%`,
      hint: '11 of 13 offers accepted',
      tone: 'bg-emerald-50 text-emerald-600',
    },
    {
      icon: 'sparkles',
      label: 'Hired this month',
      value: String(recruitmentHealth.hiredThisMonth),
      hint: 'Across 3 departments',
      tone: 'bg-violet-50 text-violet-600',
    },
  ];

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${user?.firstName ?? 'there'}`}
        subtitle={`Here's what's happening with your hiring — ${today}`}
      >
        <button
          type="button"
          onClick={() => setNotice('Job posting arrives with the Job module — for now, invite your hiring team and shape your pipeline.')}
          className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
        >
          <Icon name="briefcase" className="h-4 w-4" />
          Post a job
        </button>
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
        <div role="status" className="mb-6 flex items-start justify-between gap-4 rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm text-indigo-800">
          <p>{notice}</p>
          <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss" className="rounded p-0.5 text-indigo-400 hover:text-indigo-600">
            <Icon name="x-mark" className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* KPI row */}
      <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Active jobs"
          value={String(overviewStats.activeJobs)}
          icon="briefcase"
          tone="indigo"
          delta={{ value: '+2', direction: 'up', hint: 'this month' }}
        />
        <StatCard
          label="Candidates in pipeline"
          value={overviewStats.candidatesInPipeline.toLocaleString()}
          icon="users"
          tone="violet"
          delta={{ value: '+18%', direction: 'up', hint: 'vs last week' }}
        />
        <StatCard
          label="Interviews this week"
          value={String(overviewStats.interviewsThisWeek)}
          icon="calendar"
          tone="emerald"
          delta={{ value: '+4', direction: 'up', hint: 'vs last week' }}
        />
        <StatCard
          label="Pending invites"
          value={String(overviewStats.pendingInvites)}
          icon="envelope"
          tone="amber"
        />
      </div>

      {/* Funnel + health */}
      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card
          title="Hiring funnel"
          subtitle="All open jobs · last 90 days"
          className="lg:col-span-2"
          action={
            <Link to="/dashboard/pipeline" className="inline-flex items-center gap-1 text-sm font-semibold text-indigo-600 hover:text-indigo-500">
              View pipeline
              <Icon name="arrow-left" className="h-4 w-4 rotate-180" />
            </Link>
          }
        >
          <HiringFunnel stages={hiringFunnel} />
        </Card>

        <Card title="Recruitment health" subtitle="Key efficiency signals">
          <ul className="space-y-5">
            {healthMetrics.map((metric) => (
              <li key={metric.label} className="flex items-center gap-4">
                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${metric.tone}`}>
                  <Icon name={metric.icon} className="h-5 w-5" />
                </span>
                <div className="min-w-0">
                  <p className="text-xs text-slate-500">{metric.label}</p>
                  <p className="text-lg font-bold tracking-tight text-slate-900">{metric.value}</p>
                  <p className="truncate text-xs text-slate-400">{metric.hint}</p>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      {/* Interviews + activity */}
      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card title="Upcoming interviews" subtitle="Next on the schedule" className="lg:col-span-2" bodyClassName="divide-y divide-slate-100">
          {upcomingInterviews.map((interview) => {
            const type = INTERVIEW_TYPE_BADGE[interview.type];
            return (
              <div key={interview.id} className="flex flex-wrap items-center gap-4 px-6 py-4 first:pt-5 last:pb-5">
                <div className="w-24 shrink-0">
                  <p className="text-sm font-semibold text-slate-900">{interview.dayLabel}</p>
                  <p className="text-xs text-slate-500">{interview.time}</p>
                </div>
                <Avatar firstName={interview.candidateName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-slate-900">{interview.candidateName}</p>
                  <p className="truncate text-xs text-slate-500">
                    {interview.jobTitle} · with {interview.interviewerName}
                  </p>
                </div>
                <Badge tone={type.tone}>
                  {interview.type === 'VIDEO' && <Icon name="video-camera" className="h-3.5 w-3.5" />}
                  {interview.type === 'ONSITE' && <Icon name="building" className="h-3.5 w-3.5" />}
                  {interview.type === 'PHONE' && <Icon name="clock" className="h-3.5 w-3.5" />}
                  {type.label}
                </Badge>
              </div>
            );
          })}
        </Card>

        <Card title="Recent activity" subtitle="Latest workspace events" bodyClassName="p-6">
          <ol className="relative space-y-5 before:absolute before:bottom-2 before:left-[15px] before:top-2 before:w-px before:bg-slate-200">
            {activityFeed.map((item) => (
              <li key={item.id} className="relative flex gap-3">
                <span className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ring-4 ring-white ${ACTIVITY_TONES[item.icon]}`}>
                  <Icon name={item.icon as IconName} className="h-4 w-4" />
                </span>
                <div className="min-w-0">
                  <p className="text-sm leading-snug text-slate-700">{item.text}</p>
                  <p className="mt-0.5 text-xs text-slate-400">{item.time}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </div>

      <InviteMemberModal open={inviteOpen} onClose={() => setInviteOpen(false)} onInvite={handleInvite} />
    </>
  );
}
