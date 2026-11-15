# Security

The brief's minimum: authentication, role-based authorisation, password protection, input
validation, secure API access, protection against unauthorised operations. All are implemented;
this document also records the remaining risks honestly.

## 1. Authentication

| Measure | Implementation |
|---|---|
| Login with e-mail + password | `POST /api/v1/auth/login` → signed **JWT** (HS256, 8 h) |
| Password storage | **BCrypt** (cost 10, salted) via `BCryptPasswordHasher`; plain passwords are never stored or logged |
| Password policy | 8-72 characters, at least one letter and one digit (72 = BCrypt input limit) |
| No account enumeration | Unknown e-mail, wrong password and deactivated account all return the same 401 `INVALID_CREDENTIALS` |
| Signing key | `JWT_SECRET` from the environment; the application refuses to start with a key under 32 bytes |
| Deactivation takes effect immediately | On every request the token's user is reloaded (`ActiveUserJwtConverter`); a deactivated account's still-valid token gets 401 (TC-96) |
| Expiry | Tokens expire after 8 h (`JWT_TTL`); the frontend signs the user out on the first 401 |

## 2. Authorisation (three layers)

1. **URL rules** (`SecurityConfig`): public = health, login, register; `/admin/**` requires the
   ADMINISTRATOR role; `/reports/**` requires VIEW_REPORTS; everything else requires a valid token.
2. **Method security** (`@PreAuthorize` on controllers): e.g. technician list for ASSIGN_INCIDENTS
   or VIEW_REPORTS.
3. **Domain checks** on every action: role permissions (`user.requirePermission`) *and*
   relationships - only the assignee can resolve, only the reporter can verify, reporters never
   see internal notes, employees only see their own incidents.

Because the domain checks are the last line, a forgotten annotation cannot open a hole: the
domain still refuses (tested in TC-13 – TC-19, TC-88).

Authorities are recomputed from the database on every request, so a role or permission change
applies immediately.

## 3. Input validation

- **Bean Validation** on every request body (`@NotBlank`, `@Size`, `@Email`, `@Min/@Max`, enums) →
  400 before business code runs.
- **Domain validation** in constructors and factories (title 5-120 characters, affected users
  1-1,000,000, valid e-mail and phone formats, skill 1-5, SLA targets consistent…). Invalid
  objects cannot exist.
- **SQL injection:** all queries are parameterised (JPA Criteria API / Spring Data); user search
  text is also escaped for `LIKE` wildcards (`%`, `_`).
- **XSS:** React escapes all output; the e-mail adapter HTML-escapes message bodies (TC-76).

## 4. Secure API access

- Stateless bearer tokens; no cookies or server sessions, so CSRF does not apply (disabled
  deliberately and documented).
- **CORS** restricted to configured origins (`CORS_ALLOWED_ORIGINS`).
- **Error responses** never contain stack traces, SQL or internal class names (TC-113); database
  problems return a generic 503.
- **Optimistic locking** (`version` column) prevents two users silently overwriting each other's
  changes.
- **Audit log** is append-only; every administrative change (users, strategies, SLA policies,
  services, categories) is recorded with the actor.
- **Least privilege for automation:** the `SYSTEM` actor can classify, assign and auto-close, but
  cannot resolve incidents or report them.

## 5. Privacy

- Duplicate-check results shown to employees omit who reported the other incidents.
- Reporters see their own incidents only; technicians see their queue.
- Notifications are delivered only to people involved (reporter, assignee, supervisors) and never
  to the person who performed the action.

## 6. Known risks and next steps

| Risk | Current state | Recommended next step |
|---|---|---|
| Token in browser `localStorage` | Readable by injected scripts (XSS) | Move to an httpOnly, Secure, SameSite cookie |
| No brute-force protection on login | Unlimited attempts | Rate limiting / account lock-out after repeated failures |
| No refresh tokens | User signs in again after 8 h | Short-lived access token + refresh token rotation |
| Default JWT secret in `application.yml` | For development only | Always set `JWT_SECRET` in every non-local environment |
| Demo accounts with a shared password | Seeded by Flyway V3 for demos | Remove or disable demo data and accounts in production (`APP_DEMO_ENABLED=false` and a migration) |
| Next.js 15.0.3 | Has a published vulnerability (CVE-2025-66478) | Upgrade to a patched 15.x release |
| No MFA | Password only | Add TOTP or SSO (OAuth2/OIDC) for staff accounts |
| HTTPS | Provided by the hosting platforms (Render, Vercel) | Enforce HSTS |
