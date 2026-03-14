### Architecture (MVI + Clean Architecture)

- `shared`:
  domain layer (`model`, `repository` interfaces, `usecase`).
- `data`:
  data layer (`ProblemRepository` implementation and data configuration).
- `composeApp`:
  presentation/UI layer (MVI stores, Compose screens, routing shell, DI composition root).
- `server`:
  backend module (Ktor API).

Rules:

- UI talks only to presentation stores.
- stores talk to use cases.
- use cases talk to repository interfaces from `shared`.
- concrete repositories are provided by `data` in DI.

### Environments (local / staging / prod)

The web app reads environment config from a generated Kotlin file at build time.

JDK requirement for Gradle:

- use JDK `21` for all Gradle tasks (running with JDK `25.0.1` can fail during Gradle Kotlin DSL bootstrap)
- example: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :server:run -PappEnv=local`

Run backend + frontend together (two terminals):

- local (default) backend: `./gradlew :server:run -PappEnv=local`
- local (default) frontend: `./gradlew :composeApp:jsBrowserDevelopmentRun -PappEnv=local`
- local (wasm) frontend: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun -PappEnv=local`
- staging backend: `./gradlew :server:run -PappEnv=staging`
- staging frontend: `./gradlew :composeApp:jsBrowserProductionWebpack -PappEnv=staging -PapiBaseUrl=https://staging-api.your-domain.com`
- prod backend: `./gradlew :server:run -PappEnv=prod`
- prod frontend: `./gradlew :composeApp:jsBrowserProductionWebpack -PappEnv=prod -PapiBaseUrl=https://api.your-domain.com`

### PostgreSQL (3NF)

Backend endpoint `/problems` is now read from PostgreSQL.
`/problems` returns only rows with `problem_status = 'open'`.
Additional read-only endpoints:

- `/problems/created`
- `/problems/participation`
- `/dashboard/metrics?limit=30`
- `/dashboard/updates?limit=3` (latest problems from `problems`, ordered by `created_at DESC`)

Authentication endpoints:

- `POST /auth/challenge` (create SIWE challenge for wallet address + chain id)
- `POST /auth/verify` (verify signed challenge and create auth session cookie)
- `GET /auth/session` (returns current authenticated wallet)
- `POST /auth/logout` (clears auth cookie)

Auth challenge storage:

- SIWE nonces/challenges are stored in Redis with TTL and one-time consume semantics.

Database schema (3NF):

- `problems`: core problem attributes (`title`, `description`, `problem_status` = `open|closed`, `prize_amount`, `entry_fee_amount`, dates, participant limits, `created_by_user_id`).
- `users`: unique user identities (`registered_at`, `last_login_at`).
- `problem_participants`: mapping of participants (users) assigned/registered to a specific problem.
- `problem_tests`: ordered tests per problem (`input_data`, `expected_output`, `validator_code`, visibility flag, runtime limits).
- `problem_submissions`: submission attempts history (multiple tries per user/problem).
- `problem_submission_test_results`: verdict per test for each submission (`passed|failed|error|timeout` with runtime metrics).
- `problem_winners`: winner history per problem and winner user (`winner_user_id`, `payout_amount`, `won_at`).
- `dashboard_daily_metrics`: daily snapshot history (`metric_date`, `active_challenges`, `prize_pool_amount`, `submissions_count`).

Local startup:

- start DB + Redis: `docker compose up -d postgres redis`
- start backend: `./gradlew :server:run -PappEnv=local`

Backend auth startup requirement:

- backend now requires reachable Redis on startup (no fallback challenge store).

On startup, backend automatically:

- creates schema from `server/src/main/resources/db/schema.sql`.

Database environment variables:

- `DATABASE_URL` (optional, full JDBC URL, e.g. `jdbc:postgresql://localhost:5432/blockchain_platform`)
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `blockchain_platform`)
- `DB_USER` (default: `blockchain_user`)
- `DB_PASSWORD` (default: `blockchain_pass`)

Redis environment variables:

- `REDIS_URL` (optional, e.g. `redis://:password@localhost:6379/0` or `rediss://...`)
- `REDIS_HOST` (default: `localhost`, ignored when `REDIS_URL` is set)
- `REDIS_PORT` (default: `6379`, ignored when `REDIS_URL` is set)
- `REDIS_USERNAME` (optional)
- `REDIS_PASSWORD` (optional)
- `REDIS_DATABASE` (default: `0`)
- `REDIS_SSL` (`true|false`, default: `false`; ignored when `REDIS_URL` is set)

Auth environment variables:

- `AUTH_DOMAIN` (default: `localhost:8081`)
- `AUTH_URI` (default: `http://localhost:8081`)
- `AUTH_CHALLENGE_TTL_SECONDS` (default: `300`)
- `AUTH_MAX_ACTIVE_CHALLENGES_PER_WALLET` (default: `5`, range: `1..20`)
- `AUTH_SESSION_COOKIE` (default: `bp_auth_session`)
- `AUTH_SESSION_SIGN_KEY` (default: `local-dev-sign-key-change-me`; set strong value outside local)
- `AUTH_SESSION_SECURE` (`true|false`, default: `false`)
- `AUTH_SESSION_TTL_SECONDS` (default: `1209600` = 14 days)
- `AUTH_SESSION_SAME_SITE` (`Lax|Strict|None`, default: `Lax`; `None` requires secure cookie)
- `AUTH_TRUST_PROXY_HEADERS` (`true|false`, default: `false`; enable only behind trusted reverse proxy)
- `AUTH_RATE_LIMIT_CHALLENGE_PER_MIN` (default: `40`)
- `AUTH_RATE_LIMIT_VERIFY_PER_MIN` (default: `80`)
- `AUTH_RATE_LIMIT_SESSION_PER_MIN` (default: `120`)

CORS environment variables:

- `CORS_ALLOWED_HOSTS` (comma-separated, e.g. `https://app.example.com,https://admin.example.com`)
  - in `staging/prod`: required, and only `https://` origins are accepted

### Trunk-based workflow (recommended)

- `main` is the trunk.
- Short-lived feature branches are merged quickly into `main`.
- Staging deploys from `main`.
- Production deploys from tags (e.g., `v1.2.3`) on `main`.
- PR preview environments can be added later as a CI/CD step.
