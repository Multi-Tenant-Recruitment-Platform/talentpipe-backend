/**
 * Mock data powering the company admin dashboard.
 *
 * TODO(sprint2): replace every export here with real API calls once the
 * invitation, tenant-update, job and pipeline endpoints land (see
 * docs/DECISIONS.md). The shapes below intentionally mirror the backend DTO
 * conventions so the swap is a drop-in replacement inside the pages.
 */

export type TeamRole = 'COMPANY_ADMIN' | 'HR_MANAGER' | 'INTERVIEWER';
export type InvitableRole = Exclude<TeamRole, 'COMPANY_ADMIN'>;

export interface TeamMember {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: TeamRole;
  status: 'ACTIVE';
  joinedAt: string;
}

export interface PendingInvite {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: InvitableRole;
  invitedAt: string;
  invitedBy: string;
}

export interface FunnelStage {
  label: string;
  count: number;
}

export interface JobPipeline {
  id: string;
  title: string;
  department: string;
  location: string;
  daysOpen: number;
  status: 'OPEN' | 'ON_HOLD';
  /** Candidates currently in each stage, ordered Applied → Hired. */
  stageCounts: number[];
}

export interface UpcomingInterview {
  id: string;
  candidateName: string;
  jobTitle: string;
  dayLabel: string;
  time: string;
  interviewerName: string;
  type: 'VIDEO' | 'ONSITE' | 'PHONE';
}

export interface ActivityItem {
  id: string;
  icon: 'user-plus' | 'briefcase' | 'calendar' | 'check' | 'envelope';
  text: string;
  time: string;
}

export interface CompanyProfile {
  name: string;
  subdomain: string;
  industry: string;
  size: string;
  website: string;
  location: string;
  about: string;
}

/** Headline KPIs shown on the overview page. */
export const overviewStats = {
  activeJobs: 8,
  candidatesInPipeline: 306,
  interviewsThisWeek: 14,
  pendingInvites: 3,
};

/** Company-wide hiring funnel (all open jobs, last 90 days). */
export const hiringFunnel: FunnelStage[] = [
  { label: 'Applied', count: 486 },
  { label: 'Screening', count: 172 },
  { label: 'Interview', count: 64 },
  { label: 'Offer', count: 18 },
  { label: 'Hired', count: 11 },
];

export const recruitmentHealth = {
  avgTimeToHireDays: 18,
  offerAcceptanceRate: 86,
  hiredThisMonth: 5,
};

export const pipelineInsights = {
  fastestStage: { label: 'Offer → Hired', days: 2 },
  slowestStage: { label: 'Screening', days: 6 },
  topSource: { label: 'LinkedIn', share: 42 },
};

export const jobPipelines: JobPipeline[] = [
  {
    id: 'job-1',
    title: 'Senior Frontend Engineer',
    department: 'Engineering',
    location: 'Colombo · Hybrid',
    daysOpen: 21,
    status: 'OPEN',
    stageCounts: [128, 46, 18, 4, 2],
  },
  {
    id: 'job-2',
    title: 'Backend Engineer (Java)',
    department: 'Engineering',
    location: 'Remote',
    daysOpen: 14,
    status: 'OPEN',
    stageCounts: [96, 38, 15, 3, 1],
  },
  {
    id: 'job-3',
    title: 'Product Manager',
    department: 'Product',
    location: 'Colombo · On-site',
    daysOpen: 32,
    status: 'OPEN',
    stageCounts: [84, 31, 12, 5, 3],
  },
  {
    id: 'job-4',
    title: 'UX Designer',
    department: 'Design',
    location: 'Remote',
    daysOpen: 9,
    status: 'OPEN',
    stageCounts: [72, 24, 9, 2, 1],
  },
  {
    id: 'job-5',
    title: 'DevOps Engineer',
    department: 'Engineering',
    location: 'Colombo · Hybrid',
    daysOpen: 26,
    status: 'ON_HOLD',
    stageCounts: [61, 19, 6, 2, 1],
  },
  {
    id: 'job-6',
    title: 'HR Generalist',
    department: 'People',
    location: 'Colombo · On-site',
    daysOpen: 12,
    status: 'OPEN',
    stageCounts: [45, 14, 4, 2, 3],
  },
];

export const teamMembers: TeamMember[] = [
  {
    id: 'mem-1',
    firstName: 'Nimal',
    lastName: 'Perera',
    email: 'nimal@acme.com',
    role: 'HR_MANAGER',
    status: 'ACTIVE',
    joinedAt: 'Jan 12, 2026',
  },
  {
    id: 'mem-2',
    firstName: 'Sachini',
    lastName: 'Fernando',
    email: 'sachini@acme.com',
    role: 'HR_MANAGER',
    status: 'ACTIVE',
    joinedAt: 'Feb 3, 2026',
  },
  {
    id: 'mem-3',
    firstName: 'Kasun',
    lastName: 'Silva',
    email: 'kasun@acme.com',
    role: 'INTERVIEWER',
    status: 'ACTIVE',
    joinedAt: 'Feb 18, 2026',
  },
  {
    id: 'mem-4',
    firstName: 'Tharushi',
    lastName: 'Jayasinghe',
    email: 'tharushi@acme.com',
    role: 'INTERVIEWER',
    status: 'ACTIVE',
    joinedAt: 'Mar 2, 2026',
  },
  {
    id: 'mem-5',
    firstName: 'Dilshan',
    lastName: 'Wickramasinghe',
    email: 'dilshan@acme.com',
    role: 'INTERVIEWER',
    status: 'ACTIVE',
    joinedAt: 'Mar 21, 2026',
  },
];

export const pendingInvites: PendingInvite[] = [
  {
    id: 'inv-1',
    firstName: 'Amaya',
    lastName: 'Rathnayake',
    email: 'amaya@acme.com',
    role: 'HR_MANAGER',
    invitedAt: '2 days ago',
    invitedBy: 'You',
  },
  {
    id: 'inv-2',
    firstName: 'Ravindu',
    lastName: 'Gunasekara',
    email: 'ravindu.g@acme.com',
    role: 'INTERVIEWER',
    invitedAt: '4 days ago',
    invitedBy: 'You',
  },
  {
    id: 'inv-3',
    firstName: 'Ishara',
    lastName: 'Bandara',
    email: 'ishara@acme.com',
    role: 'INTERVIEWER',
    invitedAt: '1 week ago',
    invitedBy: 'Nimal Perera',
  },
];

export const upcomingInterviews: UpcomingInterview[] = [
  {
    id: 'int-1',
    candidateName: 'Malith Jayasuriya',
    jobTitle: 'Senior Frontend Engineer',
    dayLabel: 'Today',
    time: '10:30 AM',
    interviewerName: 'Kasun Silva',
    type: 'VIDEO',
  },
  {
    id: 'int-2',
    candidateName: 'Sanduni Herath',
    jobTitle: 'Product Manager',
    dayLabel: 'Today',
    time: '2:00 PM',
    interviewerName: 'Nimal Perera',
    type: 'ONSITE',
  },
  {
    id: 'int-3',
    candidateName: 'Chamath Weerasinghe',
    jobTitle: 'Backend Engineer (Java)',
    dayLabel: 'Tomorrow',
    time: '9:00 AM',
    interviewerName: 'Dilshan Wickramasinghe',
    type: 'VIDEO',
  },
  {
    id: 'int-4',
    candidateName: 'Nethmi Gunawardena',
    jobTitle: 'UX Designer',
    dayLabel: 'Tomorrow',
    time: '11:30 AM',
    interviewerName: 'Tharushi Jayasinghe',
    type: 'PHONE',
  },
  {
    id: 'int-5',
    candidateName: 'Pasindu Ekanayake',
    jobTitle: 'Senior Frontend Engineer',
    dayLabel: 'Wed, Jul 22',
    time: '3:30 PM',
    interviewerName: 'Kasun Silva',
    type: 'VIDEO',
  },
];

export const activityFeed: ActivityItem[] = [
  {
    id: 'act-1',
    icon: 'check',
    text: 'Sanduni Herath accepted the offer for Product Manager',
    time: '2 hours ago',
  },
  {
    id: 'act-2',
    icon: 'user-plus',
    text: 'You invited Amaya Rathnayake as HR Manager',
    time: '2 days ago',
  },
  {
    id: 'act-3',
    icon: 'calendar',
    text: 'Interview scheduled with Malith Jayasuriya (Senior Frontend Engineer)',
    time: '2 days ago',
  },
  {
    id: 'act-4',
    icon: 'briefcase',
    text: 'UX Designer role published to the careers page',
    time: '3 days ago',
  },
  {
    id: 'act-5',
    icon: 'envelope',
    text: '12 new applications for Backend Engineer (Java)',
    time: '4 days ago',
  },
];

/** Editable company profile seed — pre-populated from the tenant on load. */
export const companyProfile: CompanyProfile = {
  name: 'Acme Corporation',
  subdomain: 'acme',
  industry: 'Information Technology',
  size: '51–200 employees',
  website: 'https://acme.com',
  location: 'Colombo, Sri Lanka',
  about:
    'Acme builds workforce software that helps distributed teams ship faster. We hire for craft, curiosity and ownership — and we want every candidate to enjoy the process.',
};

export const planUsage = {
  tier: 'Growth',
  seatsUsed: 11,
  seatsTotal: 15,
};
