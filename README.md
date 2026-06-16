# Blockchain Platform

Master's thesis project: a configurable programming challenge platform using blockchain technology.

The application lets users create algorithmic challenges, join competitions through a browser crypto wallet, evaluate submitted code in isolated sandbox nodes, and store accepted results and settlements in a smart contract on Ethereum Sepolia.

## Main Components

- `composeApp` - Compose Multiplatform web frontend, available locally at `http://localhost:8081`.
- `server` - Ktor backend, available locally at `http://localhost:8080`.
- `sandbox-runner` - execution node responsible for running and measuring participant code.
- `solidity` - Solidity smart contract and Foundry deployment scripts.
- `docker-compose.yml` - local PostgreSQL, Redis, and three sandbox nodes.

## Requirements

Before running the project, install or prepare:

- Java 21,
- Docker,
- Node.js with npm,
- Foundry (`forge`, `cast`),
- MetaMask browser extension with the Sepolia network enabled,
- Sepolia RPC endpoint, for example from Alchemy or Infura,
- private key of a test Sepolia wallet with a small amount of SepoliaETH.

## Run From Scratch

### 0. Clone The Repository

```bash
git clone git@github.com:dawidszczesniak/blockchain-platform.git
cd blockchain-platform
```

### 1. Install Solidity Dependencies

```bash
npm install
```

### 2. Configure `.env.local`

Create `.env.local` in the project root:

```env
APP_ENV=local

DATABASE_URL=jdbc:postgresql://localhost:5432/blockchain_platform
DB_NAME=blockchain_platform
DB_USER=blockchain_user
DB_PASSWORD=blockchain_pass

REDIS_PASSWORD=blockchain_redis_pass
REDIS_URL=redis://:blockchain_redis_pass@localhost:6379/0

AUTH_DOMAIN=localhost:8081
AUTH_URI=http://localhost:8081
AUTH_SESSION_SIGN_KEY=local-dev-session-sign-key-2026-32chars

ETH_RPC_URL=https://sepolia.infura.io/v3/<PROJECT_ID>
ETH_PUBLIC_EXPLORER_BASE_URL=https://sepolia.etherscan.io
ETH_PLATFORM_OPERATOR_PRIVATE_KEY=<PRIVATE_KEY_TEST_WALLET>
ETH_PLATFORM_PROXY_ADDRESS=
ETH_PLATFORM_SUPPORTED_ERC20_TOKENS=

SANDBOX_NODES=http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093
SANDBOX_IMAGE_HASH=4c83f3133eeb40936fab0727a47660843ecad34bac2a2646660dac3b31df842d
SANDBOX_NODE_1_SECRET=local-dev-sandbox-secret-1
SANDBOX_NODE_2_SECRET=local-dev-sandbox-secret-2
SANDBOX_NODE_3_SECRET=local-dev-sandbox-secret-3
```

`ETH_PLATFORM_OPERATOR_PRIVATE_KEY` is the private key of the test wallet. This wallet becomes the contract owner, the result-signing operator, and the platform fee treasury.

If an ERC-20 token should be available, use the following format for `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS`:

```env
CODE|DISPLAY_NAME|SYMBOL|DECIMALS|ADDRESS
```

For a simple local run, leave this value empty and use SepoliaETH only.

### 3. Start PostgreSQL, Redis, And Sandbox Nodes

```bash
docker compose --env-file .env.local up -d postgres redis sandbox-node-1 sandbox-node-2 sandbox-node-3
```

### 4. Deploy The Contract To Sepolia

```bash
set -a
source .env.local
set +a
forge clean
forge script solidity/scripts/DeployBlockchainTestContract.sol:DeployBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --broadcast
```

After the script finishes, it prints the `Proxy` address. Put it into `.env.local`:

```env
ETH_PLATFORM_PROXY_ADDRESS=<PROXY_FROM_DEPLOY_LOG>
```

Optionally verify the deployed contract version:

```bash
cast call "$ETH_PLATFORM_PROXY_ADDRESS" "version()(string)" --rpc-url "$ETH_RPC_URL"
```

Expected result:

```text
1.0.0
```

### 5. Start The Backend

```bash
./gradlew :server:runLocalForce8080
```

The backend is available at:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/health
```

### 6. Start The Frontend

In a second terminal:

```bash
./gradlew :composeApp:runLocalForce8081
```

The frontend is available at:

```text
http://localhost:8081
```

Open the page in a browser with MetaMask installed, connect the wallet, and make sure MetaMask is using the Sepolia network.

## What To Check In The Application

1. Log in with MetaMask.
2. Create a programming challenge with a prize and an entry fee.
3. Join the competition through an on-chain transaction.
4. Submit a Kotlin solution and let the sandbox nodes evaluate it.
5. Store the accepted result in the smart contract.
6. Settle the competition after the deadline and distribute the funds.

## Project Tests

Backend, frontend, and sandbox:

```bash
./gradlew :server:test :composeApp:compileKotlinWasmJs :sandbox-runner:build
```

Solidity contract:

```bash
forge test
```

## Contract

The current contract is located at:

```text
solidity/contracts/BlockchainTestContract.sol
```

The contract uses an ERC-1967 UUPS proxy, supports deposits in ETH and optional ERC-20 tokens, stores accepted submission metrics, and settles the prize, entry fees, and platform fee. The platform fee is `200` basis points, which equals `2%`.
