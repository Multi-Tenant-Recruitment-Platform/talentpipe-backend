# TalentPipe — Architecture Decision Records

Short, dated records of decisions that shape the codebase. Newest last.

---

## ADR-1 — Tenant resolution at login via `X-Tenant-Subdomain` header (Week 1)

**Date:** 2026-07-14 · **Status:** Accepted (transitional)

### Context

Users are unique per `(tenant_id, email)`, not globally — the same email can
hold accounts in two different companies. Login therefore MUST know which
tenant it is authenticating against before it can even look the user up.

In production the tenant is identified by the subdomain the browser is on
(`acme.talentpipe.io` → tenant `acme`), derived server-side from the **Host
header**. Local development has no real subdomains on `localhost`, and
Week 1 has no ingress/DNS setup.

### Decision

For Week 1, `POST /api/v1/auth/login` resolves the tenant from an explicit
**`X-Tenant-Subdomain` request header**. The login body stays exactly
`{ email, password }`; the SPA's login form collects the subdomain and sends
it as that header.

Two principles preserved by this choice:

1. **Tenant identity never travels in a request body or path parameter** — a
   header is transport context, swapped later for the Host header without any
   change to the body contract.
2. The login failure for a wrong subdomain, unknown email or wrong password is
   the same generic `401` — account existence never leaks across tenants.

### Production follow-up

Replace the header source with Host-header parsing at the edge (keeping the
header as a dev/testing override), once real subdomains exist. The seam is a
single line in `AuthController`.

---

## ADR-2 — Account-lockout columns now, lockout logic in Week 2

**Date:** 2026-07-14 · **Status:** Accepted

### Context

Brute-force protection (failed-attempt counting + temporary lockout) is
scheduled for Week 2, but the `users` table ships in Week 1's
`V2__auth_tables.sql`.

### Decision

`users.failed_login_count` (SMALLINT NOT NULL DEFAULT 0) and
`users.locked_until` (TIMESTAMPTZ NULL) are created **now**, unused. This
avoids an `ALTER TABLE` migration one week after the table's birth and keeps
the Week 2 change purely behavioral (service-layer logic + tests).

The enforcement points are already marked in code with
`// TODO(sprint1-w2):` in `AuthService.login`.

---

## Standing conventions (not individually numbered)

- **Deferred-work marker:** `// TODO(sprint1-w2): ...` — grep for it at sprint
  planning.
- **404 over 403 for cross-tenant access:** a resource that exists but belongs
  to another tenant is indistinguishable from one that doesn't exist.
- **Refresh tokens are stored hashed (SHA-256) and rotated on every use**; raw
  tokens exist only in transit and in the client.
- **TenantContext lifecycle:** set only by `TenantResolvingFilter`, cleared in
  its `finally` block; guarded by `TenantContextLeakIntegrationTest`.
