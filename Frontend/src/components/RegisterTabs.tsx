import { Link } from 'react-router-dom';

/**
 * Persona switch shared by the two registration pages, mirroring the tab
 * pattern on the login page. Makes both sign-up paths reachable from either
 * page — a job seeker who lands on the company form (e.g. via "Get started")
 * can flip straight to candidate sign-up.
 */
export function RegisterTabs({ active }: { active: 'company' | 'candidate' }) {
  const base =
    'flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold transition-colors';
  const on = 'bg-white text-indigo-700 shadow-sm';
  const off = 'text-slate-500 hover:text-slate-700';

  return (
    <div role="tablist" aria-label="Account type" className="grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1">
      <Link
        to="/register-candidate"
        role="tab"
        aria-selected={active === 'candidate'}
        className={`${base} ${active === 'candidate' ? on : off}`}
      >
        Job seeker
      </Link>
      <Link
        to="/register"
        role="tab"
        aria-selected={active === 'company'}
        className={`${base} ${active === 'company' ? on : off}`}
      >
        Company
      </Link>
    </div>
  );
}
