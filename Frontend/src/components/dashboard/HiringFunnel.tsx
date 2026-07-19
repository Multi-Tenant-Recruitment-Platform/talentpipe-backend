import type { FunnelStage } from '../../data/mockDashboard';

const STAGE_GRADIENTS = [
  'from-indigo-600 to-indigo-400',
  'from-violet-600 to-violet-400',
  'from-purple-600 to-purple-400',
  'from-fuchsia-600 to-fuchsia-400',
  'from-emerald-600 to-emerald-400',
];

/** Conversion percentage from one stage to the next (null for the first). */
export function stageConversion(stages: FunnelStage[], index: number): number | null {
  if (index === 0 || stages[index - 1].count === 0) {
    return null;
  }
  return Math.round((stages[index].count / stages[index - 1].count) * 100);
}

/**
 * Horizontal-bar hiring funnel: each stage scales against the top of the
 * funnel, with stage-to-stage conversion percentages alongside.
 */
export function HiringFunnel({ stages }: { stages: FunnelStage[] }) {
  const top = stages[0]?.count ?? 1;

  return (
    <div className="space-y-5">
      {stages.map((stage, i) => {
        const conversion = stageConversion(stages, i);
        const width = Math.max(4, Math.round((stage.count / top) * 100));
        return (
          <div key={stage.label}>
            <div className="flex items-baseline justify-between gap-4">
              <span className="text-sm font-medium text-slate-700">{stage.label}</span>
              <span className="text-sm font-semibold tabular-nums text-slate-900">
                {stage.count.toLocaleString()}
                {conversion !== null && (
                  <span className="ml-2 text-xs font-normal text-slate-400">
                    {conversion}% from {stages[i - 1].label.toLowerCase()}
                  </span>
                )}
              </span>
            </div>
            <div className="mt-1.5 h-3 overflow-hidden rounded-full bg-slate-100">
              <div
                className={`h-full rounded-full bg-gradient-to-r ${STAGE_GRADIENTS[i % STAGE_GRADIENTS.length]}`}
                style={{ width: `${width}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
