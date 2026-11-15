# Phase 1 test coverage

| Test | Expected behavior | Suite |
| --- | --- | --- |
| Email normalization | Case and surrounding spaces normalize | AuthServiceTest |
| Duplicate registration | Conflict before insert | AuthServiceTest, AuthIntegrationTest |
| Unknown account | Generic 401 and BCrypt work | AuthServiceTest |
| Wrong password | No token or session issued | AuthServiceTest |
| Session revocation | Active session becomes inactive | AuthServiceTest |
| Expired session | Session cannot authenticate | AuthServiceTest |
| Invalid technician capacity | Nonpositive values rejected | AuthServiceTest |
| Employee registration | Persists BCrypt hash; role injection ignored | AuthIntegrationTest |
| Database-backed login | Registered user logs in and loads profile | AuthIntegrationTest |
| Employee authorization | Own dashboard 200; other dashboards 403 | AuthIntegrationTest |
| Technician authorization | Own dashboard 200; other dashboards 403 | AuthIntegrationTest |
| Admin authorization | Own dashboard 200; other dashboards 403 | AuthIntegrationTest |
| Logout | Previously valid JWT gets 401 | AuthIntegrationTest |
| Anonymous request | Protected endpoint gets 401 | AuthIntegrationTest |
| Invalid registration fields | 400 with safe validation response | AuthIntegrationTest |
| Malformed JWT | 401 before endpoint access | AuthIntegrationTest |
| Invalid credentials | 401 without user details | AuthIntegrationTest |
| API bearer header | Token sent to backend | Vitest |
| API authorization error | Backend 403 is preserved | Vitest |
| Empty logout response | 204 succeeds without JSON parse error | Vitest |
| Validation error rendering contract | Backend problem detail preserved | Vitest |
| Anonymous route protection | Redirect to login | Vitest |
| Stale browser session | Clear token and redirect | Vitest |
| Wrong frontend role | Employee is redirected away from admin shell | Vitest |
| Browser login/logout | Store session after login; revoke and clear on logout | Vitest |
| Signed JWT authorization | All nine role combinations checked with real signatures | SecurityHttpTest |
| Signature/expiry/session checks | Tampered, expired and sessionless tokens rejected | SecurityHttpTest |
| HTTP login/logout | BCrypt login and server revocation through Spring Security | SecurityHttpTest |
| Credential logging | Passwords and tokens redacted from DTO string representations | AuthServiceTest |
| Restart persistence | Registered user survives DB/backend restart | scripts/smoke.ps1 |

Integration tests require Docker; skipped tests do not count as executed verification. See README for commands. Later phases must add tests for incident business rules and complete workflows.
