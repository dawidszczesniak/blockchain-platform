# Blockchain Platform

Kotlin full-stack platform for blockchain-backed algorithmic competitions. The app lets creators publish programming challenges, lets participants submit optimized solutions, validates code through isolated sandbox nodes, and records competition lifecycle data on-chain.

This repository is the master's thesis project of Dawid Szczesniak at the Military University of Technology.

This branch is the academic hand-in snapshot of the final platform implementation.

## What It Does

- Creators define a problem, reference solution, tests, prize, entry fee, participant limit, and deadlines.
- Participants join on-chain, submit code, and compete on correctness plus runtime and memory usage.
- The backend judges submissions with sandbox consensus before preparing any on-chain result.
- The Solidity contract escrows ETH/ERC-20 funds, tracks accepted result hashes and metrics, and handles settlement or refunds.

## Modules

- `composeApp` - Compose Multiplatform web frontend, served locally on `http://localhost:8081`.
- `server` - Ktor backend on `http://localhost:8080`; exposes auth, platform, dashboard, and problem APIs.
- `shared` - DTOs and domain models shared by frontend and backend.
- `sandbox-runner` - Kotlin/JVM HTTP sandbox node with `/run`, `/cancel`, `/health`, and `/attestation`.
- `solidity` - Foundry project containing the UUPS-upgradeable contract, scripts, and tests.

## Smart Contract

The active contract is `solidity/contracts/BlockchainTestContract.sol`.

- Deployed behind an ERC-1967 UUPS proxy.
- Proxy address is the stable runtime address used by frontend, backend, and users.
- Implementation logic can be upgraded while proxy storage is preserved.
- Inherits `Ownable2StepUpgradeable`, `UUPSUpgradeable`, and `ReentrancyGuardTransient`.
- Supports native ETH and owner-whitelisted ERC-20 payment tokens.
- Rejects unsupported or fee-on-transfer ERC-20 behavior to keep escrow accounting exact.
- Exposes `version()` returning `1.0.0`.

This branch contains only the final contract source used for the thesis hand-in.

## Requirements

- Java 21
- Docker
- Node.js and npm
- Foundry (`forge`, `cast`)
- Ethereum RPC endpoint for the configured chain

Node.js dependencies are required because OpenZeppelin Foundry upgrade validation resolves packages from `node_modules`.

## Install Dependencies

```bash
npm install
```

This installs `forge-std`, `@openzeppelin/foundry-upgrades`, `@openzeppelin/contracts`, and `@openzeppelin/contracts-upgradeable`. Foundry remappings are configured in `remappings.txt`.

## Local Configuration

Local development uses one ignored file in the repository root: `.env.local`.

Rules:

- `.env.local` must exist.
- `APP_ENV` must be `local`.
- Backend, frontend build, and Docker Compose read this file directly.
- Do not commit `.env.local`; it is ignored by git.

Minimum local keys used by the current app:

```env
APP_ENV=local
DATABASE_URL=jdbc:postgresql://localhost:5432/blockchain_platform
DB_NAME=blockchain_platform
DB_USER=...
DB_PASSWORD=...
REDIS_URL=redis://:password@localhost:6379/0
REDIS_PASSWORD=...
AUTH_DOMAIN=localhost:8081
AUTH_URI=http://localhost:8081
AUTH_SESSION_SIGN_KEY=...
ETH_RPC_URL=...
ETH_PUBLIC_EXPLORER_BASE_URL=https://sepolia.etherscan.io
ETH_PLATFORM_PROXY_ADDRESS=...
ETH_PLATFORM_OPERATOR_PRIVATE_KEY=...
ETH_PLATFORM_SUPPORTED_ERC20_TOKENS=USDC|USD Coin|USDC|6|0x...
SANDBOX_NODES=http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093
SANDBOX_IMAGE_HASH=...
SANDBOX_NODE_1_SECRET=...
SANDBOX_NODE_2_SECRET=...
SANDBOX_NODE_3_SECRET=...
```

`ETH_PLATFORM_SUPPORTED_ERC20_TOKENS` accepts semicolon-separated entries in this format:

```env
CODE|DISPLAY_NAME|SYMBOL|DECIMALS|ADDRESS
```

Example:

```env
ETH_PLATFORM_SUPPORTED_ERC20_TOKENS=USDC|USD Coin|USDC|6|0x...
```

If a backend value should come from Google Secret Manager, leave the target key blank and set `GCP_SECRET_<KEY>`. If the secret name is not a full `projects/...` path, also set `GCP_PROJECT_ID` or `GOOGLE_CLOUD_PROJECT`.

Foundry scripts do not resolve `GCP_SECRET_*` references. Before deploy or upgrade, make sure concrete `ETH_RPC_URL` and `ETH_PLATFORM_OPERATOR_PRIVATE_KEY` values are exported in the shell.

Before running Foundry scripts, export `.env.local` into the shell:

```bash
set -a
source .env.local
set +a
```

## Run Locally

Start infrastructure:

```bash
docker compose --env-file .env.local up -d postgres redis sandbox-node-1 sandbox-node-2 sandbox-node-3
```

Start backend:

```bash
./gradlew :server:runLocalForce8080
```

Start frontend:

```bash
./gradlew :composeApp:runLocalForce8081
```

Open `http://localhost:8081`. Backend health is available at `http://localhost:8080/health`.

## Contract Commands

Validate current implementation:

```bash
npm install
set -a
source .env.local
set +a
forge clean
env -u ETH_RPC_URL forge script solidity/scripts/ValidateBlockchainTestContract.sol:ValidateBlockchainTestContract
```

Deploy a fresh UUPS proxy:

```bash
set -a
source .env.local
set +a
forge clean
forge script solidity/scripts/DeployBlockchainTestContract.sol:DeployBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --broadcast
```

The deploy script initializes owner, operator, and treasury to the wallet behind `ETH_PLATFORM_OPERATOR_PRIVATE_KEY`, uses `200` bps platform fee, approves `SANDBOX_IMAGE_HASH`, and can whitelist an initial ERC-20 token from `ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN` or the first `USDC` entry in `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS`.

Upgrade an existing proxy to the current implementation:

```bash
set -a
source .env.local
set +a
forge clean
forge script solidity/scripts/UpgradeBlockchainTestContract.sol:UpgradeBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --sender "$(cast wallet address --private-key "$ETH_PLATFORM_OPERATOR_PRIVATE_KEY")" \
  --broadcast
```

Upgrades keep proxy storage unchanged. If an existing proxy was initialized with another platform fee, update it separately by calling `setPlatformFeeBps(200)` from the owner wallet.

Check deployed version:

```bash
cast call "$ETH_PLATFORM_PROXY_ADDRESS" "version()(string)" --rpc-url "$ETH_RPC_URL"
```

Expected result:

```text
1.0.0
```

For future upgrades, preserve storage layout order and append new storage fields only. Deployment addresses for this branch should be recorded in `local-instructions/sepolia_contract_addresses.txt` after a fresh deploy or upgrade that matches this branch.

## Main Runtime Flow

Create competition:

1. Frontend calls `POST /problems/create/prepare`.
2. Backend validates the problem, stores a short-lived intent, and returns wallet calldata for `createCompetition(...)` plus an optional ERC-20 approval transaction.
3. User signs the wallet transaction.
4. Frontend calls `POST /problems/create/confirm`.
5. Backend verifies calldata, payment value, receipt, and `CompetitionCreated`, then persists the problem.

Join competition:

1. Frontend calls `POST /problems/{problemId}/join/prepare`.
2. Backend returns wallet calldata for `joinCompetition(...)` plus optional ERC-20 approval.
3. User signs the wallet transaction.
4. Frontend calls `POST /problems/{problemId}/join/confirm`.
5. Backend verifies `CompetitionJoined` and records participation.

Submit solution:

1. Frontend calls `POST /problems/{problemId}/submit`.
2. Backend creates a Redis-backed judge job.
3. Sandbox nodes execute the code; backend requires configured consensus and valid attestations.
4. If accepted, backend stores the submission and returns a signed `recordSubmissionResult(...)` transaction payload.
5. Participant sends that transaction from their wallet.
6. Frontend calls `POST /problems/submissions/{submissionId}/confirm`.
7. Backend verifies the receipt and marks the result as recorded on-chain.

Settle or cancel competition:

1. `CompetitionSettlementWorker` schedules deadline jobs and marks competitions ready for user-triggered finalization.
2. Settlement uses `POST /problems/{problemId}/settle/prepare` and `POST /problems/{problemId}/settle/confirm`.
3. Cancellation uses `POST /problems/{problemId}/cancel/prepare` and `POST /problems/{problemId}/cancel/confirm`.
4. Backend verifies `CompetitionSettled` or `CompetitionCancelled` and persists the final state.
5. Refunds remain claimable from the same proxy address.

## Verification

Backend tests:

```bash
./gradlew :server:test
```

Frontend compile:

```bash
./gradlew :composeApp:compileKotlinWasmJs
```

Sandbox runner build:

```bash
./gradlew :sandbox-runner:build
```

Foundry compile and tests:

```bash
forge build
forge test
```

Full local verification pass:

```bash
./gradlew :server:test :composeApp:compileKotlinWasmJs :sandbox-runner:build
forge build
forge test
```
