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
- `POST /auth/logout-all` (invalidates all active sessions for authenticated user)

Auth storage model:

- SIWE nonces/challenges are stored in Redis with TTL and one-time consume semantics.
- auth sessions are server-side in Redis (`session_id` cookie only).
- `POST` auth/protected mutations enforce trusted request origin (`Origin` / `Referer`) to harden CSRF.
- EIP-1271 smart-contract wallet signature verification is supported when `ETH_RPC_URL` is configured.

Database schema (3NF):

- `problems`: core problem attributes (`title`, `description`, `problem_status` = `open|closed`, `prize_amount`, `entry_fee_amount`, dates, participant limits, `created_by_user_id`).
- `users`: unique user identities (`registered_at`, `last_login_at`).
- `problem_participants`: mapping of participants (users) assigned/registered to a specific problem.
- `problem_tests`: ordered tests per problem (`input_data`, `expected_output`, `validator_code`, visibility flag, runtime limits).
- `problem_submissions`: accepted submission history (multiple tries per user/problem).
- `problem_submission_test_results`: verdict per test for each accepted submission (`passed|failed|error|timeout` with runtime and memory metrics).
- `problem_submission_judge_jobs`: async judge queue/job state for submits (`queued|running|accepted|rejected|error`) including final payload or rejected preview.
- `problem_submissions.runtime_ms`: official submission runtime for the whole judge session; tests still run in isolation, but the stored runtime is measured across the full suite execution.
- `problem_submissions.memory_used_kb`: official submission memory usage (`max` across isolated tests from the consensus node result).
- `problem_winners`: winner history per problem and winner user (`winner_user_id`, `payout_amount`, `won_at`).
- `dashboard_daily_metrics`: daily snapshot history (`metric_date`, `active_challenges`, `prize_pool_amount`, `submissions_count`).

Local startup:

- optional: build sandbox image hash for attestation:
  - `docker build -t blockchain-platform-sandbox-runner:local sandbox-runner`
  - `docker save blockchain-platform-sandbox-runner:local | shasum -a 256`
  - copy hash to env `SANDBOX_IMAGE_HASH` (same value for all 3 nodes)
- optional: set node attestation secrets for backend verification:
  - `export SANDBOX_NODE_SECRETS="sandbox-node-1=local-dev-sandbox-secret-1,sandbox-node-2=local-dev-sandbox-secret-2,sandbox-node-3=local-dev-sandbox-secret-3"`
- when `APP_ENV=local` and `SANDBOX_NODE_SECRETS` is not set, backend falls back to the same default local secrets as `docker-compose.yml`
- start DB + Redis + 3 sandbox nodes:
  - `docker compose up -d postgres redis sandbox-node-1 sandbox-node-2 sandbox-node-3`
  - after changes in `sandbox-runner/runner.py`, rebuild nodes:
    - `docker compose build sandbox-node-1 sandbox-node-2 sandbox-node-3`
    - `docker compose up -d sandbox-node-1 sandbox-node-2 sandbox-node-3`
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
- in `staging/prod`: Redis password is required

Sandbox execution environment variables (backend):

- `SANDBOX_NODES` (comma-separated URLs; default: `http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093`)
- `SANDBOX_REQUEST_TIMEOUT_MS` (default: `20000`)
- `SANDBOX_CONNECT_TIMEOUT_MS` (default: `2500`)
- `SANDBOX_IMAGE_HASH` (optional; if set, backend verifies returned sandbox image hash and rejects mismatches)
- `SANDBOX_CONSENSUS_THRESHOLD` (default: `3`; minimum matching nodes required during `submit`)
- `SANDBOX_NODE_SECRETS` (comma-separated `nodeId=secret`; backend verifies node HMAC attestations during `submit`; in `APP_ENV=local` it defaults to the same local secrets as `docker-compose.yml`)
  - backend fails on startup in `staging/prod` when this variable is missing
  - backend also fails on startup when the value is malformed or when too few secrets are configured to satisfy `SANDBOX_CONSENSUS_THRESHOLD`

Sandbox runner environment variables (each docker node):

- `SANDBOX_NODE_ID` (e.g. `sandbox-node-1`)
- `SANDBOX_PORT` (default: `8080`)
- `SANDBOX_IMAGE_HASH` (same hash on all 3 nodes)
- `SANDBOX_VERSION` (arbitrary version label for attestation metadata)
- `SANDBOX_ATTESTATION_SECRET` (shared secret for node-level HMAC attestation signature)

Submission anchoring environment variables (backend):

- `ETH_ANCHOR_ENABLED` (`true|false`, default: `false`)
- `ETH_CHAIN_ID` (required when anchoring enabled)
- `ETH_ANCHOR_CONTRACT_ADDRESS` (required when anchoring enabled)
- `ETH_ANCHOR_PRIVATE_KEY` (required when anchoring enabled)
- `ETH_ANCHOR_METHOD_NAME` (default: `anchorSubmission`)
- `ETH_ANCHOR_GAS_LIMIT` (default: `350000`)
- `ETH_ANCHOR_GAS_PRICE_WEI` (optional; if omitted backend reads `eth_gasPrice`)
- `ETH_ANCHOR_RECEIPT_TIMEOUT_MS` (default: `90000`)
- `ETH_ANCHOR_RECEIPT_POLL_INTERVAL_MS` (default: `2000`)
- `ETH_ANCHOR_EXPLORER_TX_BASE_URL` (optional, e.g. `https://sepolia.etherscan.io/tx`)

Execution endpoints:

- `POST /problems/create/validate` - create-problem reference validation used by `Uruchom test` / `Uruchom wszystkie`; runs on a single sandbox node with failover
- `POST /problems/{problemId}/run` - single-node run with failover (preview)
- `POST /problems/{problemId}/submit` - enqueue async judge job; accepted submissions are persisted only after the worker confirms all tests passed
- `GET /problems/submission-jobs/{jobId}` - poll async submit status/result

Judge execution model:

- tests always run in isolation for correctness and sandbox safety
- create-problem validation (`POST /problems/create/validate`) uses one sandbox node; displayed per-test runtime and memory come from that node
- participant `RUN` (`POST /problems/{problemId}/run`) also uses one sandbox node; displayed runtime and memory come from that node
- `SUBMIT` executes on all configured sandbox nodes
- `SUBMIT` consensus is based on identical `resultHash` + `imageHash`, not on averages; with the current default `SANDBOX_CONSENSUS_THRESHOLD=3`, this means strict `3/3` agreement for the local 3-node cluster
- official submission `runtime_ms` is measured conservatively as the highest suite runtime reported by the matching consensus nodes
- official submission `memory_used_kb` is measured conservatively as the highest per-test memory usage reported by the matching consensus nodes
- accepted submit test-by-test details shown to the user are taken from the first node inside the winning consensus group; official runtime/memory still use the conservative aggregation above
- current web solver UI submits `kotlin`; backend judge profiles also define `java`
- language-specific execution profiles are applied before sending tests to sandbox, so timeout/memory policy can differ per language

Create problem validation contract (production flow):

- `testCases` are the only source of cases for judge.
- at least `1` test must be public (`isHidden=false`).
- `referenceSolutionCode` is required and is executed in sandbox during create.
- backend computes `expectedOutput` for every test directly from `referenceSolutionCode`.
- Problem is persisted only if reference solution passes:
  - all `testCases`,
  - and repeated runs produce deterministic output set.

Minimal payload example (`POST /problems`):

```json
{
  "title": "Square Number",
  "description": "Given integer n, return n*n.",
  "constraints": "1 <= n <= 10^6",
  "referenceSolutionCode": "fun solve(input: String): String { val n = input.trim().toLong(); return (n * n).toString() }",
  "referenceSolutionLanguage": "kotlin",
  "prizeAmount": 1000,
  "entryFeeAmount": 10,
  "requiredParticipants": 1,
  "joinUntilDate": "2026-04-10",
  "submitUntilDate": "2026-04-20",
  "testCases": [
    {
      "inputData": "5",
      "isHidden": false,
      "timeoutMs": 1000,
      "memoryLimitMb": 256
    },
    {
      "inputData": "7",
      "isHidden": false,
      "timeoutMs": 1000,
      "memoryLimitMb": 256
    },
    {
      "inputData": "10",
      "isHidden": false,
      "timeoutMs": 1000,
      "memoryLimitMb": 256
    }
  ]
}
```

Minimal participant code that passes the example above:

```kotlin
fun solve(input: String): String {
    val n = input.trim().toLong()
    return (n * n).toString()
}
```

Reference contract:

- `contracts/SubmissionAnchorRegistry.sol`

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
- `AUTH_TRUSTED_ORIGINS` (optional comma-separated origin list, e.g. `https://app.example.com,https://admin.example.com`; defaults to origin from `AUTH_URI`)
- `AUTH_RATE_LIMIT_CHALLENGE_PER_MIN` (default: `40`)
- `AUTH_RATE_LIMIT_VERIFY_PER_MIN` (default: `80`)
- `AUTH_RATE_LIMIT_SESSION_PER_MIN` (default: `120`)

Blockchain environment variables:

- `ETH_RPC_URL` (required in `staging/prod`; enables smart-contract wallet signature verification via EIP-1271)
- chain policy is environment-bound:
  - `APP_ENV=prod` => mainnet only (`chainId=1`)
  - `APP_ENV=local|staging` => testnet only (`chainId != 1`)
  - this policy is enforced for login challenges (`/auth/challenge`) and submit anchoring (`ETH_CHAIN_ID`)

CORS environment variables:

- `CORS_ALLOWED_HOSTS` (comma-separated, e.g. `https://app.example.com,https://admin.example.com`)
  - in `staging/prod`: required, and only `https://` origins are accepted

### Trunk-based workflow (recommended)

- `main` is the trunk.
- Short-lived feature branches are merged quickly into `main`.
- Staging deploys from `main`.
- Production deploys from tags (e.g., `v1.2.3`) on `main`.
- PR preview environments can be added later as a CI/CD step.
