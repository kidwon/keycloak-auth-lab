# PRD — Keycloak Auth Lab

- Version: 1.0
- Goal: Local, reproducible, observable learning environment for OAuth 2.0, OIDC, Keycloak and Spring Security.
- Audience: Java backend developer practicing implementation and interview explanations.
- Priority: correctness and observability over UI polish.

## 1. Product objective
Build a local application that demonstrates end-to-end OAuth 2.0 Authorization Code Flow with PKCE, OIDC login, JWT validation, role/scope authorization, refresh/logout, and machine-to-machine Client Credentials Flow. The user must be able to inspect each request, token, claim, and access decision. It is a **learning lab, not production IAM**.

## 2. Technology and local environment
- Java 21, Spring Boot 3.x (compatible stable patch versions), Spring Security OAuth2 Client and Resource Server.
- Keycloak: compatible, stable supported release; pin image version (no `latest`).
- PostgreSQL for Keycloak and optionally app data, Docker Compose.
- Simple server-rendered HTML (Thymeleaf) or minimal static frontend; avoid SPA complexity.
- Maven or Gradle, choose one and document. JUnit 5, Spring Security Test, Testcontainers where appropriate.
- Default localhost-only ports: Keycloak 8081, web client 8080, resource API 8082, optionally service client 8083. Choose non-conflicting ports if needed and document them.
- All components start with documented `docker compose up -d` + one simple app start command, or fully containerized Compose. Prefer fully containerized for reproducibility, while supporting IDE debug runs.

## 3. Architecture
1. **Keycloak**: `auth-lab` realm, users, clients, roles, scopes, key material and login UI.
2. **web-client**: confidential server-side OAuth2/OIDC client, Authorization Code + PKCE, browser session, state handling, ID Token display (safe claims only), login/logout and API call.
3. **resource-api**: Spring Security OAuth2 Resource Server; validate JWT using issuer metadata/JWKS, enforce audience and scopes/roles, return sample data.
4. **service-client**: sample machine client that obtains a Client Credentials token and calls a protected service endpoint (can be a CLI command/test rather than separate server).
5. **learning UI**: display sequence, sanitized decoded claims, expected HTTP statuses and explanations; never expose credentials or raw refresh tokens in UI/logs.

Flow: browser -> web-client -> Keycloak authorization endpoint -> redirect callback with code/state -> web-client token endpoint (code + verifier) -> web-client calls resource-api with Access Token -> resource-api validates JWT locally using Keycloak JWKS -> role/scope/business authorization -> response.

## 4. Functional requirements and acceptance criteria

### F01. One-command environment setup (P0)
- Compose provisions PostgreSQL and Keycloak with a checked-in realm export/import.
- Provide seeded demo users `alice` (USER) and `admin` (ADMIN), development-only passwords in `.env.example`/README, never real secrets.
- Configurable ports and issuer URLs. Avoid `localhost` vs Docker service-host issuer mismatch; explain browser and container networking and choose a working consistent configuration.
- **Acceptance:** fresh clone and README steps produce healthy services and working login without manual Keycloak console setup.

### F02. Authorization Code + PKCE and state (P0)
- web-client starts login via Spring Security OAuth2 Client with OIDC `openid profile` and PKCE S256. Ensure PKCE actually enabled for chosen client type, not just assumed.
- Generate high-entropy verifier/challenge and request-bound state; compare and consume state at callback.
- Explain in UI/docs: authorization request sends `code_challenge`, token POST body (`application/x-www-form-urlencoded`) sends `code_verifier`.
- **Acceptance:** successful login and documented reproducible inspection of the two requests; tampered state is rejected; mismatched verifier is rejected in an isolated test.
- **Important:** never log or display a real authorization code, verifier, or token in production-like logs; if the lab offers a demo trace, redact sensitive values by default. For experiments, use explicit test fixtures, not interception of real sessions.

### F03. ID Token vs Access Token vs Refresh Token (P0)
- Display sanitized, decoded **ID Token** claims (`iss`, `sub`, `aud`, `exp`, `iat`, `preferred_username`) and **Access Token** claims separately, including intended audience and scopes.
- Explain that ID Token is for client authentication result, Access Token for API, Refresh Token for renewing tokens; JWT payload is encoded, not encrypted.
- **Acceptance:** the UI clearly distinguishes the tokens and API calls use Access Token only; ID Token as bearer credential is rejected by resource-api based on audience/type policy.

### F04. JWT verification (P0)
- Resource server validates signature, `iss`, `exp`, `nbf` when present, and explicit `aud=resource-api`; use OIDC discovery/JWKS and proper key rotation support.
- Configure Keycloak audience mapper/client scope so legitimate Access Tokens contain `resource-api` in audience. Explicitly reject tokens without that audience.
- Document `sub` as subject identifier; explain `iss + sub` identity key; note UUID is a possible value, not the meaning of `sub`.
- **Acceptance:** valid token succeeds; expired, altered, wrong-issuer, and wrong-audience tokens fail with 401 (using tests/fixtures rather than manually modifying live JWTs).

### F05. Authorization scopes and roles (P0)
- APIs: `GET /api/public` (anonymous), `GET /api/me` (authenticated), `GET /api/orders` (requires `orders:read` scope), `POST /api/orders` (requires `orders:write` scope), `GET /api/admin` (requires ADMIN role), `GET /api/orders/{id}` (requires owner or ADMIN).
- Seed simple in-memory order data with distinct owners; no real PII.
- Explicitly map Keycloak realm/client roles to Spring `GrantedAuthority`; do not assume default `SCOPE_` converter handles Keycloak roles.
- **Acceptance:** matrix of USER/ADMIN and missing scope/role yields 200/401/403 as appropriate; object-level access denies other users' orders.

### F06. Refresh and logout (P1)
- Demonstrate refreshing an expired Access Token using refresh token from secure server-side authorized-client storage; never put refresh token in browser localStorage.
- Demonstrate RP-initiated OIDC logout where supported, plus local session invalidation.
- **Acceptance:** refresh succeeds for valid session; after logout user must log in again. Explain limits of JWT revocation and short-lived tokens.

### F07. Client Credentials Flow (P1)
- Configure service account client and restricted audience/scope for a dedicated machine endpoint, e.g. `GET /api/internal/status`.
- Show request `grant_type=client_credentials`, authentication method and machine identity. Explain no human user is present.
- **Acceptance:** correct machine token succeeds, user token without service authority fails, bad client credentials fail.

### F08. Interactive learning documentation (P0)
- `docs/01-overview.md`: OAuth 2.0 vs OIDC vs JWT vs Keycloak vs Spring Security.
- `docs/02-auth-code-pkce.md`: sequence diagram (Mermaid), sample authorization GET and token POST, `state`, verifier/challenge and Base64URL details.
- `docs/03-tokens-claims.md`: ID/Access/Refresh, `iss/sub/aud/exp/iat`, issuer and audience.
- `docs/04-authorization.md`: role, scope, ownership, 401 vs 403.
- `docs/05-security-experiments.md`: safe reproducible negative tests.
- `docs/06-client-credentials.md`: service-to-service example.
- `docs/07-japanese-interview.md`: concise Japanese interview questions/answers for all topics.
- `docs/08-troubleshooting.md`: redirect URI, issuer mismatch, audience, CORS, cookies, clock skew, port conflicts.
- Include curl commands and expected status/response; never use real secrets in examples.

## 5. Security requirements
- Do not disable signature verification, issuer validation, audience validation, CSRF protection or TLS verification as a workaround.
- HTTP on localhost is acceptable **only** for local development; document HTTPS requirement for real deployment.
- Keycloak realm and OAuth clients must use exact redirect URIs and narrow web origins; avoid wildcard redirect URIs.
- Public clients cannot safely store a client secret; confidential clients must keep secret server-side.
- Keep secrets in environment variables; `.env` gitignored, `.env.example` committed.
- Limit access token lifetimes for demonstration; no raw tokens, verifier or passwords in logs, screenshots, or committed files.
- Token inspection UI must be authenticated and must show sanitized claims, not reusable credentials.
- Tests must cover CSRF/state, PKCE mismatch, ID Token misuse, wrong audience, missing role/scope and order ownership.

## 6. Non-functional requirements
- Startup and clean reset documented (`docker compose down -v` clearly labeled destructive).
- Health endpoints and clear logs; no sensitive logging.
- Code separated by responsibility; no unnecessary microservice complexity.
- Automated tests runnable locally and in CI; test suite should not depend on manual Keycloak configuration.
- Stable pinned versions, readable Java code and comments explaining non-obvious security choices.

## 7. Suggested repository layout
```text
keycloak-auth-lab/
  compose.yaml
  .env.example
  README.md
  keycloak/realm-export.json
  web-client/
  resource-api/
  service-client/             # optional CLI module
  docs/
  scripts/                    # curl examples and safe test helpers
  .github/workflows/ci.yml
```

## 8. Acceptance test matrix
| Scenario | Expected |
|---|---|
| Fresh install + login alice | Successful OIDC login |
| Authorization request | `response_type=code`, `state`, `code_challenge`, `code_challenge_method=S256` |
| Token request | POST form body includes code + `code_verifier` |
| Callback state altered | Login rejected |
| Wrong PKCE verifier | Token endpoint rejects exchange |
| Valid Access Token, correct audience | Protected API succeeds |
| ID Token sent to API | 401 |
| Expired/tampered/wrong-audience token | 401 |
| Authenticated without required scope/role | 403 |
| Alice reads another user's order | 403 or 404 (document chosen policy) |
| Admin accesses admin endpoint | 200 |
| Valid machine token calls internal endpoint | 200 |
| User token calls machine-only endpoint | 403 |
| Logout then protected web page | Login required |

## 9. Out of scope
- Social login, federation with external identity providers, enterprise SAML, MFA implementation, production deployment, real payments, real customer data, multi-tenant authorization and custom Keycloak SPI.

## 10. Delivery plan for Codex
1. Inspect current directory, propose concise implementation plan and file structure. Do not overwrite unrelated files.
2. Implement Compose + realm import + verified issuer/audience configuration.
3. Implement resource-api and its tests.
4. Implement web-client with PKCE and state; confirm using browser and HTTP inspection.
5. Implement role/scope/ownership examples and negative tests.
6. Implement optional refresh/logout and Client Credentials.
7. Write documentation and Japanese interview Q&A.
8. Run build, tests and Compose smoke checks; fix failures. Report what was actually verified vs what requires manual testing.

## 11. Definition of Done
- `README.md` gives copy-paste commands from fresh checkout to running system.
- Login and protected API scenarios work with no manual Keycloak configuration.
- Every P0 acceptance test passes automatically or has an explicitly documented manual verification procedure.
- At least one Mermaid sequence diagram and one HTTP request walkthrough are included.
- Security-sensitive examples are correct and default to safe handling of secrets.
- Codex summarizes architecture, commands, test results, known limitations and next learning exercises.

## 12. Ready-to-use Codex instruction
> Implement this repository according to `keycloak-auth-lab-PRD.md`. First read the PRD and inspect the current directory, then write a short plan. Prioritize a working, reproducible local environment and security correctness over visual design. Use supported pinned dependency versions. Configure Keycloak realm, audience, roles and scopes explicitly. Implement actual Authorization Code + PKCE S256, not a simulated flow. Add tests for both success and failure cases, document exact commands, run available checks, and clearly report any unverified behavior. Do not weaken security checks just to make demos pass. If any PRD requirement is ambiguous, choose the simplest secure implementation and document the decision.
