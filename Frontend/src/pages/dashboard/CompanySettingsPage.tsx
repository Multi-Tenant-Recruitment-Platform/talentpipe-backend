import { useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Badge } from '../../components/dashboard/Badge';
import { Card } from '../../components/dashboard/Card';
import { Icon } from '../../components/dashboard/Icon';
import { PageHeader } from '../../components/dashboard/PageHeader';
import { companyProfile as seedProfile, planUsage, type CompanyProfile } from '../../data/mockDashboard';

const INDUSTRIES = [
  'Information Technology',
  'Finance & Banking',
  'Healthcare',
  'Education',
  'Retail & E-commerce',
  'Manufacturing',
  'Hospitality & Leisure',
  'Other',
];

const COMPANY_SIZES = ['1–10 employees', '11–50 employees', '51–200 employees', '201–500 employees', '500+ employees'];

const inputClass =
  'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm ' +
  'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500';

/**
 * Company settings: the admin edits the public-facing company profile, and
 * reviews the workspace identity, careers page URL and plan usage.
 *
 * TODO(sprint1-w2): persist via the tenant update endpoint once it lands.
 */
export function CompanySettingsPage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<CompanyProfile>({
    ...seedProfile,
    name: user?.tenantName ?? seedProfile.name,
  });
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);

  const careersUrl = `${profile.subdomain}.talentpipe.io/jobs`;
  const seatsPercent = Math.round((planUsage.seatsUsed / planUsage.seatsTotal) * 100);

  function update<K extends keyof CompanyProfile>(key: K, value: CompanyProfile[K]) {
    setSaved(false);
    setProfile((current) => ({ ...current, [key]: value }));
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    // TODO(sprint1-w2): PATCH /tenant with the profile payload.
    setSaved(true);
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(`https://${careersUrl}`);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard unavailable (non-secure context) — the URL is visible anyway.
    }
  }

  return (
    <>
      <PageHeader
        title="Company settings"
        subtitle="Keep your company profile up to date — candidates see this on your careers page and job posts."
      />

      {saved && (
        <div role="status" className="mb-6 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <Icon name="check" className="h-4 w-4 shrink-0" />
          Company profile updated.
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Editable profile */}
        <Card title="Company profile" subtitle="Public details shown to candidates" className="lg:col-span-2">
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Logo */}
            <div className="flex items-center gap-4">
              <span className="flex h-16 w-16 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 text-white">
                <Icon name="building" className="h-8 w-8" />
              </span>
              <div>
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
                  title="Logo upload arrives with the tenant media API"
                >
                  Upload logo
                </button>
                <p className="mt-1.5 text-xs text-slate-400">PNG or SVG, at least 256×256.</p>
              </div>
            </div>

            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                <label htmlFor="company-name" className="block text-sm font-medium text-slate-700">Company name</label>
                <input
                  id="company-name"
                  required
                  value={profile.name}
                  onChange={(e) => update('name', e.target.value)}
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="company-industry" className="block text-sm font-medium text-slate-700">Industry</label>
                <select
                  id="company-industry"
                  value={profile.industry}
                  onChange={(e) => update('industry', e.target.value)}
                  className={inputClass}
                >
                  {INDUSTRIES.map((industry) => (
                    <option key={industry}>{industry}</option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="company-size" className="block text-sm font-medium text-slate-700">Company size</label>
                <select
                  id="company-size"
                  value={profile.size}
                  onChange={(e) => update('size', e.target.value)}
                  className={inputClass}
                >
                  {COMPANY_SIZES.map((size) => (
                    <option key={size}>{size}</option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="company-website" className="block text-sm font-medium text-slate-700">Website</label>
                <input
                  id="company-website"
                  type="url"
                  value={profile.website}
                  onChange={(e) => update('website', e.target.value)}
                  className={inputClass}
                  placeholder="https://…"
                />
              </div>
              <div className="sm:col-span-2">
                <label htmlFor="company-location" className="block text-sm font-medium text-slate-700">Headquarters</label>
                <input
                  id="company-location"
                  value={profile.location}
                  onChange={(e) => update('location', e.target.value)}
                  className={inputClass}
                  placeholder="City, Country"
                />
              </div>
              <div className="sm:col-span-2">
                <label htmlFor="company-about" className="block text-sm font-medium text-slate-700">About the company</label>
                <textarea
                  id="company-about"
                  rows={4}
                  value={profile.about}
                  onChange={(e) => update('about', e.target.value)}
                  className={`${inputClass} resize-none`}
                />
                <p className="mt-1.5 text-xs text-slate-400">
                  A short pitch that appears at the top of your careers page.
                </p>
              </div>
            </div>

            <div className="flex justify-end border-t border-slate-100 pt-5">
              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-md bg-gradient-to-r from-indigo-600 to-violet-600 px-5 py-2 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
              >
                <Icon name="check" className="h-4 w-4" />
                Save changes
              </button>
            </div>
          </form>
        </Card>

        {/* Workspace + plan */}
        <div className="space-y-6">
          <Card title="Workspace" subtitle="Your tenant identity on TalentPipe">
            <div className="space-y-4">
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Subdomain</p>
                <div className="mt-1.5 flex items-center rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
                  <span className="truncate text-sm font-semibold text-slate-800">{profile.subdomain}</span>
                  <span className="text-sm text-slate-400">.talentpipe.io</span>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Status</p>
                <Badge tone="emerald">Active</Badge>
              </div>
              <div className="border-t border-slate-100 pt-4">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Careers page</p>
                <div className="mt-1.5 flex items-center justify-between gap-2 rounded-md border border-slate-200 px-3 py-2">
                  <span className="inline-flex min-w-0 items-center gap-2 text-sm text-slate-700">
                    <Icon name="link" className="h-4 w-4 shrink-0 text-slate-400" />
                    <span className="truncate">{careersUrl}</span>
                  </span>
                  <button
                    type="button"
                    onClick={() => void handleCopy()}
                    className={`shrink-0 rounded-md px-2.5 py-1 text-xs font-semibold ${
                      copied ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                    }`}
                  >
                    {copied ? 'Copied' : 'Copy'}
                  </button>
                </div>
                <p className="mt-1.5 text-xs text-slate-400">Share this link — it's where candidates apply.</p>
              </div>
            </div>
          </Card>

          <Card title="Plan & usage" subtitle="Current subscription">
            <div className="flex items-center justify-between">
              <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-900">
                <Icon name="sparkles" className="h-4 w-4 text-violet-500" />
                {planUsage.tier} plan
              </span>
              <Badge tone="indigo">Monthly</Badge>
            </div>
            <div className="mt-4">
              <div className="flex items-baseline justify-between text-sm">
                <span className="text-slate-500">Team seats</span>
                <span className="font-semibold tabular-nums text-slate-900">
                  {planUsage.seatsUsed} / {planUsage.seatsTotal}
                </span>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-violet-500"
                  style={{ width: `${seatsPercent}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-slate-400">
                {planUsage.seatsTotal - planUsage.seatsUsed} seats left — invite HR managers and interviewers anytime.
              </p>
            </div>
            <button
              type="button"
              className="mt-4 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
              title="Billing arrives in a later sprint"
            >
              Manage plan
            </button>
          </Card>
        </div>
      </div>
    </>
  );
}
