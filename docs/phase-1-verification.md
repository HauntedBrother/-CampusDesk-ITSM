# Phase 1 verification

Verified on 15 November 2026 using portable Temurin Java 21.0.13, Maven 3.9.9 and Node 24.18.0. Downloaded build tools and dependency caches live under ignored `.tmp/`; they are not application dependencies.

- Frontend: TypeScript compilation and Vite production build passed.
- Frontend: 8 Vitest tests passed, including session state, login/logout, anonymous protection and role redirects.
- Backend: Java 21 compilation and executable Spring Boot JAR packaging passed.
- Backend: 20 JUnit/Mockito/Spring MVC tests passed, including real BCrypt login, signed JWT validation, all nine role combinations, registration role escalation prevention and logout token revocation.
- Backend: 8 PostgreSQL Testcontainers test entries were skipped because no Docker installation/daemon is available. These tests have not verified the Flyway migration or database persistence in this environment.
- The local Vite server returned HTTP 200; it was stopped after checking. Interactive browser QA was blocked by an unavailable browser access host.
- PowerShell smoke-script syntax parsed successfully. Running it stopped at its Docker prerequisite check. Compose image builds, service health, real database-backed authentication and restart persistence remain unverified here.

To finish runtime verification on a Docker-enabled machine, copy `.env.example` to `.env`, then run `powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1` from the repository root and `mvn verify` from `backend`. The smoke script leaves the application running and creates a uniquely named test employee. The script checks three seed logins, every role combination, token revocation, and registration persistence across database/backend restart.
