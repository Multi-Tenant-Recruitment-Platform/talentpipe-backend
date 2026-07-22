import { Link } from 'react-router-dom';

/** Public landing shell: name, one-liner, and a clear entry point per persona. */
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

      {/* Two audiences, two doors — employers and job seekers each get their own. */}
      <div className="mt-12 grid w-full max-w-3xl gap-6 sm:grid-cols-2">
        <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-8 text-left shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">For employers</h2>
          <p className="mt-2 flex-1 text-sm text-slate-600">
            Create your company workspace, invite your hiring team, and manage
            your pipeline end to end.
          </p>
          <Link
            to="/register"
            className="mt-6 inline-block rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-5 py-2.5 text-center text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
          >
            Register your company
          </Link>
        </div>

        <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-8 text-left shadow-sm">
          <h2 className="text-lg font-bold text-slate-900">For job seekers</h2>
          <p className="mt-2 flex-1 text-sm text-slate-600">
            Create one candidate account, apply to every company hiring on
            TalentPipe, and get discovered again.
          </p>
          <Link
            to="/register-candidate"
            className="mt-6 inline-block rounded-md border border-indigo-600 px-5 py-2.5 text-center text-sm font-semibold text-indigo-700 transition-colors hover:bg-indigo-50"
          >
            Create a candidate account
          </Link>
        </div>
      </div>

      <div className="mt-10 flex items-center gap-6 text-sm">
        <Link to="/login" className="font-medium text-slate-600 hover:text-slate-900">
          Already have an account? Log in
        </Link>
        <span aria-hidden="true" className="text-slate-300">
          |
        </span>
        <Link to="/jobs" className="font-medium text-indigo-600 hover:text-indigo-500">
          Browse open positions →
        </Link>
      </div>
    </section>
  );
}
