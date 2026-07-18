import type { ReactNode } from 'react';
import { AuthBrandPanel } from './AuthBrandPanel';

/**
 * Split-screen layout shared by the auth pages: brand panel on the left,
 * the page's form on the right (the form stands alone on small screens).
 */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <section className="mx-auto max-w-5xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl lg:grid lg:grid-cols-2">
      <AuthBrandPanel />
      <div className="px-6 py-10 sm:px-10">{children}</div>
    </section>
  );
}
