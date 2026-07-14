import { useEffect, useState } from 'react';
import { api, apiErrorMessage } from '../api/client';
import type { JobSummary, PageResponse } from '../api/types';

/**
 * Public job board (PB-005, partial). Calls GET /public/jobs — which returns
 * an empty page until the Job module lands — and renders the empty state
 * gracefully.
 */
export function JobsPage() {
  const [jobs, setJobs] = useState<JobSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get<PageResponse<JobSummary>>('/public/jobs')
      .then(({ data }) => {
        if (!cancelled) setJobs(data.content);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not load jobs. Is the backend running?'));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section>
      <h1 className="text-3xl font-bold tracking-tight">Open positions</h1>
      <p className="mt-2 text-slate-600">Roles published by companies hiring on TalentPipe.</p>

      <div className="mt-8">
        {error && (
          <div className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}

        {!error && jobs === null && <p className="text-slate-500">Loading jobs…</p>}

        {!error && jobs !== null && jobs.length === 0 && (
          <div className="rounded-lg border border-dashed border-slate-300 bg-white p-12 text-center">
            <h2 className="text-lg font-semibold text-slate-900">No open positions yet</h2>
            <p className="mt-2 text-sm text-slate-500">
              Companies are just getting set up — check back soon.
            </p>
          </div>
        )}

        {!error && jobs !== null && jobs.length > 0 && (
          <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
            {jobs.map((job) => (
              <li key={job.id} className="p-4">
                <span className="font-medium text-slate-900">{job.title}</span>
                <span className="ml-2 text-sm text-slate-500">{job.companyName}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
