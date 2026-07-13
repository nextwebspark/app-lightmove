# LightMove

Multi-tenant SaaS for executive search and talent mapping.

## Quick start

```bash
# 1. Database (creates it, the app user, and IAM read access)
./ops/cloudsql/create-database.sh

# 2. Local config
cp apps/api/src/main/resources/application-local.yml{.example,}
#    …fill in the DB password it printed

# 3. Cloud SQL authenticates as you
gcloud auth application-default login

# 4. Run
npm install
npm run dev          # api :8080 · web :5173
```

Sign up at http://localhost:5173/signup with a **work email** (gmail and friends are refused — the
domain is how colleagues find each other). The verification link is printed to the API console; no
email provider is needed to run the whole flow.

## Tests

```bash
npm test                      # both suites
cd apps/api && ./mvnw test    # 25 tests — needs Docker (Testcontainers spins up Postgres 16)
cd apps/web && npx vitest     # 11 tests
```

## What exists

Signup (account → workspace → invites), login, Google OAuth, email verification, refresh-token rotation
with theft detection, account lockout, rate limiting, and an append-only audit trail. Plus a placeholder
screen behind the login wall.

Projects — the actual product — are designed in `claude-design/` and not yet built.

See [CLAUDE.md](CLAUDE.md) for the rules that shape the code.
