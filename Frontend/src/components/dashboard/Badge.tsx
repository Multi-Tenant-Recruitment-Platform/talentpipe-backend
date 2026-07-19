import type { ReactNode } from 'react';

export type BadgeTone = 'indigo' | 'violet' | 'emerald' | 'amber' | 'slate' | 'red' | 'sky';

const TONE_CLASSES: Record<BadgeTone, string> = {
  indigo: 'bg-indigo-50 text-indigo-700 ring-indigo-600/20',
  violet: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  amber: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  slate: 'bg-slate-100 text-slate-700 ring-slate-500/20',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
  sky: 'bg-sky-50 text-sky-700 ring-sky-600/20',
};

/** Small status/role pill with a soft tinted ring. */
export function Badge({ tone = 'slate', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}
