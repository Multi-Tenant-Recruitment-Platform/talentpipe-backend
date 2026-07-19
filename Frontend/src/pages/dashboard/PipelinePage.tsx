import { Badge } from '../../components/dashboard/Badge';
import { Card } from '../../components/dashboard/Card';
import { HiringFunnel } from '../../components/dashboard/HiringFunnel';
import { Icon, type IconName } from '../../components/dashboard/Icon';
import { PageHeader } from '../../components/dashboard/PageHeader';
import { hiringFunnel, jobPipelines, pipelineInsights } from '../../data/mockDashboard';

const STAGE_LABELS = ['Applied', 'Screening', 'Interview', 'Offer', 'Hired'];
const STAGE_COLORS = ['bg-indigo-500', 'bg-violet-500', 'bg-purple-500', 'bg-fuchsia-500', 'bg-emerald-500'];

/**
 * Recruitment pipeline: the full-funnel view of how candidates move from
 * application to hire, which stage is the bottleneck, and how each open job
 * is filling its stages.
 */
export function PipelinePage() {
  const totalInFunnel = hiringFunnel[0]?.count ?? 0;
  const hired = hiringFunnel[hiringFunnel.length - 1]?.count ?? 0;
  const overallConversion = totalInFunnel > 0 ? Math.round((hired / totalInFunnel) * 1000) / 10 : 0;

  const insights: { icon: IconName; label: string; value: string; hint: string; tone: string }[] = [
    {
      icon: 'bolt',
      label: 'Fastest stage',
      value: pipelineInsights.fastestStage.label,
      hint: `~${pipelineInsights.fastestStage.days} days on average`,
      tone: 'bg-emerald-50 text-emerald-600',
    },
    {
      icon: 'clock',
      label: 'Slowest stage',
      value: pipelineInsights.slowestStage.label,
      hint: `~${pipelineInsights.slowestStage.days} days on average`,
      tone: 'bg-amber-50 text-amber-600',
    },
    {
      icon: 'globe',
      label: 'Top candidate source',
      value: pipelineInsights.topSource.label,
      hint: `${pipelineInsights.topSource.share}% of all applicants`,
      tone: 'bg-indigo-50 text-indigo-600',
    },
  ];

  return (
    <>
      <PageHeader
        title="Recruitment pipeline"
        subtitle="Understand how candidates flow through your hiring process — and where they get stuck."
      />

      {/* Insight cards */}
      <div className="grid gap-5 sm:grid-cols-3">
        {insights.map((insight) => (
          <div key={insight.label} className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${insight.tone}`}>
              <Icon name={insight.icon} className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <p className="text-xs text-slate-500">{insight.label}</p>
              <p className="truncate text-base font-bold tracking-tight text-slate-900">{insight.value}</p>
              <p className="text-xs text-slate-400">{insight.hint}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Company-wide funnel */}
      <Card
        title="Company-wide funnel"
        subtitle={`${totalInFunnel.toLocaleString()} applications in the last 90 days · ${overallConversion}% end-to-end conversion`}
        className="mt-6"
      >
        <HiringFunnel stages={hiringFunnel} />
        <div className="mt-6 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <Icon name="warning" className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
          <p className="text-sm leading-relaxed text-amber-800">
            <span className="font-semibold">Screening is your bottleneck.</span> Candidates wait ~6 days on
            average before moving forward. Consider inviting another interviewer or tightening your
            screening criteria to keep the pipeline flowing.
          </p>
        </div>
      </Card>

      {/* Per-job pipelines */}
      <Card
        title="Pipeline by job"
        subtitle="Candidates in each stage, per open role"
        className="mt-6"
        action={
          <div className="flex flex-wrap items-center gap-3">
            {STAGE_LABELS.map((label, i) => (
              <span key={label} className="inline-flex items-center gap-1.5 text-xs text-slate-500">
                <span className={`h-2 w-2 rounded-full ${STAGE_COLORS[i]}`} />
                {label}
              </span>
            ))}
          </div>
        }
        bodyClassName="overflow-x-auto"
      >
        <table className="min-w-full divide-y divide-slate-100 text-left">
          <thead>
            <tr className="text-xs font-semibold uppercase tracking-wide text-slate-400">
              <th scope="col" className="px-6 py-3">Job</th>
              <th scope="col" className="px-6 py-3">Open for</th>
              <th scope="col" className="w-2/5 px-6 py-3">Pipeline</th>
              <th scope="col" className="px-6 py-3">Candidates</th>
              <th scope="col" className="px-6 py-3">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {jobPipelines.map((job) => {
              const total = job.stageCounts.reduce((sum, n) => sum + n, 0);
              return (
                <tr key={job.id} className="hover:bg-slate-50/60">
                  <td className="px-6 py-4">
                    <p className="text-sm font-semibold text-slate-900">{job.title}</p>
                    <p className="text-xs text-slate-500">
                      {job.department} · {job.location}
                    </p>
                  </td>
                  <td className="px-6 py-4 text-sm text-slate-500">{job.daysOpen} days</td>
                  <td className="px-6 py-4">
                    <div
                      className="flex h-2.5 w-full min-w-40 overflow-hidden rounded-full bg-slate-100"
                      title={job.stageCounts.map((n, i) => `${STAGE_LABELS[i]}: ${n}`).join(' · ')}
                    >
                      {job.stageCounts.map((count, i) =>
                        count > 0 ? (
                          <span
                            key={STAGE_LABELS[i]}
                            className={STAGE_COLORS[i]}
                            style={{ width: `${(count / total) * 100}%` }}
                          />
                        ) : null,
                      )}
                    </div>
                    <p className="mt-1.5 text-xs tabular-nums text-slate-400">
                      {job.stageCounts.map((n, i) => `${STAGE_LABELS[i].slice(0, 1)}${n}`).join('  ')}
                    </p>
                  </td>
                  <td className="px-6 py-4 text-sm font-semibold tabular-nums text-slate-900">{total}</td>
                  <td className="px-6 py-4">
                    <Badge tone={job.status === 'OPEN' ? 'emerald' : 'amber'}>
                      {job.status === 'OPEN' ? 'Open' : 'On hold'}
                    </Badge>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
    </>
  );
}
