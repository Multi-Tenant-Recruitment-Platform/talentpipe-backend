import { Link } from 'react-router-dom';

/** Public landing shell: name, one-liner, entry points. */
export function LandingPage() {
  return (
    <section className="flex flex-col items-center py-16 text-center">
      <h1 className="max-w-2xl text-5xl font-extrabold tracking-tight text-slate-900">
        TalentPipe
      </h1>
      <p className="mt-6 max-w-xl text-lg text-slate-600">
        The multi-tenant recruitment intelligence platform — one place for your
        jobs, candidates and hiring pipeline.
      </p>
      <div className="mt-10 flex gap-4">
        <Link
          to="/register"
          className="rounded-md bg-indigo-600 px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          Register your company
        </Link>
        <Link
          to="/login"
          className="rounded-md border border-slate-300 bg-white px-6 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          Log in
        </Link>
      </div>
      <Link to="/jobs" className="mt-8 text-sm font-medium text-indigo-600 hover:text-indigo-500">
        Browse open positions →
      </Link>
    </section>
  );
}
