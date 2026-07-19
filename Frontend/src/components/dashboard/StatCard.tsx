import { Icon, type IconName } from './Icon';

export type StatTone = 'indigo' | 'violet' | 'emerald' | 'amber';

const TONE_CLASSES: Record<StatTone, string> = {
  indigo: 'bg-indigo-50 text-indigo-600',
  violet: 'bg-violet-50 text-violet-600',
  emerald: 'bg-emerald-50 text-emerald-600',
  amber: 'bg-amber-50 text-amber-600',
};

/**
 * KPI tile: tinted icon chip, bold value, label, and an optional trend delta
 * (e.g. "+12% vs last week").
 */
export function StatCard({
  label,
  value,
  icon,
  tone,
  delta,
}: {
  label: string;
  value: string;
  icon: IconName;
  tone: StatTone;
  delta?: { value: string; direction: 'up' | 'down'; hint?: string };
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <span className={`flex h-10 w-10 items-center justify-center rounded-lg ${TONE_CLASSES[tone]}`}>
          <Icon name={icon} className="h-5 w-5" />
        </span>
        {delta && (
          <span
            className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${
              delta.direction === 'up' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
            }`}
          >
            <Icon name={delta.direction === 'up' ? 'trending-up' : 'trending-down'} className="h-3.5 w-3.5" />
            {delta.value}
          </span>
        )}
      </div>
      <p className="mt-4 text-2xl font-bold tracking-tight text-slate-900">{value}</p>
      <p className="mt-1 text-sm text-slate-500">
        {label}
        {delta?.hint && <span className="text-slate-400"> · {delta.hint}</span>}
      </p>
    </div>
  );
}
