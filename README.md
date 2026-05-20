# Blockchain Platform

Kotlin full-stack coding competition platform with one on-chain application contract deployed behind an OpenZeppelin UUPS proxy.

The runtime contract surface is still one address: the `BlockchainTestContract` proxy. The implementation contract contains logic and can be replaced. The proxy keeps the persistent storage under ERC-1967, so competition state, winner history, and recorded submission results survive upgrades.

OpenZeppelin references used for this setup:

- UUPS overview: https://docs.openzeppelin.com/upgrades
- Foundry upgrades workflow: https://docs.openzeppelin.com/upgrades-plugins/foundry-upgrades
- Foundry upgrades API: https://docs.openzeppelin.com/upgrades-plugins/api-foundry-upgrades

## Architecture

- `contracts/BlockchainTestContract.sol`
  - OpenZeppelin upgradeable implementation contract
  - inherits `Ownable2StepUpgradeable`, `UUPSUpgradeable`, `ReentrancyGuardTransient`
  - initializer replaces constructor
  - supports native ETH plus whitelisted ERC-20 payment tokens such as USDC
  - rejects fee-on-transfer ERC-20s, so escrow math stays exact
  - exposes `version()` returning `2.0.0`
- UUPS proxy
  - deployed as ERC-1967 proxy
  - stores all persistent state
  - keeps the user-facing contract address stable across upgrades
- `server`
  - Ktor backend exposing auth, platform, dashboard, and problem APIs
  - prepares and confirms on-chain create/join flows
  - judges submissions through sandbox consensus
  - records accepted submission results on-chain
  - runs background settlement/cancellation workers
- `composeApp`
  - Compose Multiplatform web frontend
- `sandbox-runner`
  - isolated code execution nodes used for judging consensus

## UUPS Upgrade Model

This is the exact model you asked for:

- implementation contract
  - contains logic
  - can be replaced with a new implementation
- proxy contract
  - keeps storage forever
  - delegates calls to the current implementation
  - keeps the same public address for frontend, backend, and users

In practice:

1. deploy implementation + UUPS proxy
2. initialize through proxy
3. users and backend always use the proxy address
4. when you need a new version, deploy a new implementation and call `upgradeToAndCall(...)` through the proxy
5. storage remains in proxy, so values are preserved if storage layout stays compatible

Current scope of the on-chain contract:

- native chain currency, currently ETH on Sepolia
- ERC-20 tokens on the same chain, currently USDC

Future scope:

- more ERC-20 tokens can be enabled without upgrading the proxy, by whitelisting them
- non-EVM or cross-chain rails like BTC, Arbitrum bridged settlement, or other L2-native payment routes need a new backend payment rail abstraction and separate settlement integration; they are not something a single Ethereum contract can natively execute on its own

The frontend and backend were refactored around a generic `paymentAsset` model, so extending beyond `ETH + USDC` later does not require another domain-model rewrite.

## Foundry Tooling

The project now uses Foundry instead of Hardhat for contract work.

Added:

- [foundry.toml](/Users/computer.account/Desktop/blockchain-platform/foundry.toml)
- [remappings.txt](/Users/computer.account/Desktop/blockchain-platform/remappings.txt)
- [DeployBlockchainTestContract.s.sol](/Users/computer.account/Desktop/blockchain-platform/script/DeployBlockchainTestContract.s.sol)
- [UpgradeBlockchainTestContract.s.sol](/Users/computer.account/Desktop/blockchain-platform/script/UpgradeBlockchainTestContract.s.sol)
- [ValidateBlockchainTestContract.s.sol](/Users/computer.account/Desktop/blockchain-platform/script/ValidateBlockchainTestContract.s.sol)

Removed:

- project-root Hardhat config
- project-root Hardhat JS deploy/upgrade/validate scripts

## Requirements

- Java 21
- Foundry
- Node.js
- Docker
- Ethereum RPC endpoint

Node.js is still needed for OpenZeppelin Foundry upgrade safety validation. OpenZeppelin documents this explicitly for `openzeppelin-foundry-upgrades`.

## Install Contract Dependencies

Run:

```bash
forge install foundry-rs/forge-std
forge install OpenZeppelin/openzeppelin-foundry-upgrades
forge install OpenZeppelin/openzeppelin-contracts-upgradeable
```

The repo is already configured for the remappings OpenZeppelin requires for Contracts v5.

## Local App Development

1. Edit [`.env.local`](/Users/computer.account/Desktop/blockchain-platform/.env.local).

2. Start local infrastructure:

```bash
docker compose --env-file .env.local up -d postgres redis sandbox-node-1 sandbox-node-2 sandbox-node-3
```

3. Start backend:

```bash
./gradlew :server:runLocalForce8080
```

4. Start frontend:

```bash
./gradlew :composeApp:runLocalForce8081
```

Frontend runs on `http://localhost:8081`, backend on `http://localhost:8080`.

## Single `.env.local`

Backend, frontend build, Docker Compose, and Foundry scripts use one configuration source: [`.env.local`](/Users/computer.account/Desktop/blockchain-platform/.env.local).

`.env.local` is mandatory for local development, must keep `APP_ENV=local`, and must define the local DB, Redis, auth, Ethereum, and sandbox settings the backend loads at startup.

Before running `forge script`, export that file into your shell:

```bash
set -a
source .env.local
set +a
```

Do not use a second env file, Gradle properties, or process env overrides for project configuration. Keep all configurable values in `.env.local`.

If a value should come from Google Secret Manager, keep the target key blank and provide `GCP_SECRET_<KEY>`. When the secret reference is not a fully qualified `projects/...` path, also set `GCP_PROJECT_ID` or `GOOGLE_CLOUD_PROJECT`.

For `ETH + USDC` the important blockchain entries are:

- `ETH_RPC_URL`
- `ETH_PLATFORM_PROXY_ADDRESS`
- `ETH_PLATFORM_OPERATOR_PRIVATE_KEY`
- `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS`
- `ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN`

Recommended Sepolia USDC format:

```env
ETH_PLATFORM_SUPPORTED_ERC20_TOKENS=USDC|USD Coin|USDC|6|0x...
ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN=0x...
```

`ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN` is optional. If you leave it empty and `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS` contains a `USDC|...|ADDRESS` entry, the deploy script will automatically whitelist that USDC token during initialization. The backend/frontend asset catalog reads `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS`.

## Validate, Deploy, Upgrade

Validate current implementation:

```bash
npm install
set -a
source .env.local
set +a
forge clean
env -u ETH_RPC_URL forge script script/ValidateBlockchainTestContract.s.sol:ValidateBlockchainTestContract
```

Deploy UUPS proxy:

The deploy script uses platform fee `500` basis points by default and initializes owner, operator, and treasury to the wallet behind `ETH_PLATFORM_OPERATOR_PRIVATE_KEY`. If `ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN` is set, that ERC-20 is whitelisted from the first block of the proxy.

Command:

```bash
set -a
source .env.local
set +a
forge clean
forge script script/DeployBlockchainTestContract.s.sol:DeployBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --broadcast
```

Upgrade proxy:

Command:

```bash
set -a
source .env.local
set +a
forge clean
forge script script/UpgradeBlockchainTestContract.s.sol:UpgradeBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --sender "$(cast wallet address --private-key "$ETH_PLATFORM_OPERATOR_PRIVATE_KEY")" \
  --broadcast
```

The `--sender` flag matters for upgrades because the proxy owner must authorize the UUPS upgrade.

## Recommended Upgrade Discipline

For future implementation versions:

1. keep proxy address unchanged
2. preserve storage layout order
3. only append new storage variables
4. do not remove or reorder storage fields
5. if you introduce a new implementation file, annotate it with `@custom:oz-upgrades-from <reference>`

That is the OpenZeppelin-safe path for preserving proxy storage across upgrades.

## Backend Flow

Current on-chain create flow (`/problems/create/prepare` -> `/problems/create/confirm`):

1. frontend calls backend prepare endpoint
2. backend returns prepared wallet tx for `createCompetition(...)`
3. if asset is ERC-20, backend may also return an approval tx that must be signed first
4. user signs wallet transaction(s)
5. backend confirm step verifies calldata, transferred value, and `CompetitionCreated`
6. backend persists on-chain competition id

Current on-chain join flow (`/problems/{problemId}/join/prepare` -> `/problems/{problemId}/join/confirm`):

1. frontend calls backend prepare endpoint
2. backend returns prepared wallet tx for `joinCompetition(...)`
3. if asset is ERC-20, backend may also return an approval tx that must be signed first
4. user signs wallet transaction(s)
5. backend confirm step verifies `CompetitionJoined`
6. backend records participation

Accepted submission:

1. sandbox nodes run code
2. backend requires consensus
3. backend persists accepted submission
4. backend operator records it on-chain with `recordSubmissionResult(...)`

Settlement:

1. backend worker finds competitions ready for settlement
2. operator calls `settleCompetition(...)`
3. if no winner can be produced, operator calls `cancelCompetition(...)`
4. refunds stay claimable from the same proxy address

## Verification

Backend tests:

```bash
./gradlew test
```

Frontend compile:

```bash
./gradlew :composeApp:compileKotlinWasmJs
```

Foundry compile:

```bash
forge build
```

OpenZeppelin validation:

```bash
npm install
forge clean
env -u ETH_RPC_URL forge script script/ValidateBlockchainTestContract.s.sol:ValidateBlockchainTestContract
```
