const PALETTE = [
  'bg-indigo-100 text-indigo-700',
  'bg-violet-100 text-violet-700',
  'bg-emerald-100 text-emerald-700',
  'bg-amber-100 text-amber-700',
  'bg-rose-100 text-rose-700',
  'bg-sky-100 text-sky-700',
];

const SIZES = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-12 w-12 text-base',
} as const;

/**
 * Initials avatar with a deterministic tint derived from the person's name,
 * so the same name always renders in the same colour across the dashboard.
 */
export function Avatar({
  firstName,
  lastName,
  size = 'md',
  className = '',
}: {
  firstName: string;
  lastName?: string;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const full = `${firstName} ${lastName ?? ''}`.trim();
  const initials = full
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
  const hash = [...full].reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  const tone = PALETTE[hash % PALETTE.length];

  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold ${SIZES[size]} ${tone} ${className}`}
      aria-hidden="true"
    >
      {initials || '?'}
    </span>
  );
}
