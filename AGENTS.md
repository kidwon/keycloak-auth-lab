# Repository Guidelines

## Project Structure & Module Organization

This repository currently contains `keycloak-auth-lab-PRD.md`, the implementation and acceptance reference. Application modules, build tooling, and tests have not been created. Follow the PRD’s planned structure:

- `web-client/`: server-side Spring Security OAuth2/OIDC client and learning UI.
- `resource-api/`: JWT validation, scope/role checks, and order ownership examples.
- `service-client/`: optional Client Credentials CLI or application.
- `keycloak/realm-export.json`: reproducible realm, clients, roles, scopes, and demo users.
- `compose.yaml`: local Keycloak and PostgreSQL services.
- `docs/` and `scripts/`: learning guides, curl examples, and safe experiment helpers.

Keep Java sources in each module’s `src/main/java`, tests in `src/test/java`, and templates/configuration in `src/main/resources`.

## Build, Test, and Development Commands

No build or runtime commands are executable yet. Choose Maven or Gradle, commit its wrapper, and document exact commands in `README.md`.

- Planned `docker compose up -d`: start configured infrastructure or the complete containerized lab.
- Once configured, `./mvnw verify` or `./gradlew build`: compile and run configured checks; document only the selected build tool.
- `docker compose down -v`: destructive reset that removes persisted database volumes; clearly label this in instructions.

Document IDE startup, health checks, and browser/container issuer networking.

## Coding Style & Naming Conventions

Target Java 21 and Spring Boot 3.x. Use four-space Java indentation, `UpperCamelCase` classes, `lowerCamelCase` methods/fields, and lowercase package names. Separate authentication, authorization, and business logic by responsibility. No formatter or linter is configured; document any introduced tooling. Pin dependency and container versions.

## Testing Guidelines

Use the PRD’s specified JUnit 5, Spring Security Test, and Testcontainers where appropriate. Name test classes `*Test`; document integration-test naming and execution when configured. Cover valid and invalid JWTs, audience checks, PKCE/state failures, ID Token misuse, scopes, roles, CSRF, and ownership. Use isolated fixtures. No numeric coverage threshold exists; prioritize the PRD acceptance matrix.

## Commit & Pull Request Guidelines

No Git history is available to establish conventions. Use concise, imperative commit subjects. PR descriptions should identify the implemented PRD requirements, configuration changes, validation commands/results, and unverified behavior. Link related issues when available; include sanitized screenshots for UI changes.

## Security & Configuration

Keep secrets in environment variables; ignore `.env` and commit `.env.example` with development-only placeholders. Never expose reusable tokens, passwords, authorization codes, or PKCE verifiers. Preserve signature, issuer, audience, CSRF, and TLS checks. Use exact redirect URIs and narrow origins; allow HTTP only for localhost development.
