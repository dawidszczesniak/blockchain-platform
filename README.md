# Blockchain Platform

Projekt pracy magisterskiej: konfigurowalna platforma rywalizacji programistycznej z wykorzystaniem technologii blockchain.

Aplikacja pozwala tworzyć zadania algorytmiczne, zapisywać uczestników przez portfel kryptowalutowy, testować przesłany kod w odizolowanych węzłach sandbox oraz zapisywać zaakceptowane wyniki i rozliczenia w inteligentnym kontrakcie na sieci Ethereum Sepolia.

## Najważniejsze elementy

- `composeApp` - frontend webowy Compose Multiplatform, lokalnie `http://localhost:8081`.
- `server` - backend Ktor, lokalnie `http://localhost:8080`.
- `sandbox-runner` - węzeł wykonujący i mierzący kod uczestników.
- `solidity` - inteligentny kontrakt Solidity oraz skrypty Foundry.
- `docker-compose.yml` - lokalne PostgreSQL, Redis i trzy węzły sandbox.

## Wymagania

Przed uruchomieniem trzeba mieć zainstalowane:

- Java 21,
- Docker,
- Node.js z npm,
- Foundry (`forge`, `cast`),
- MetaMask z włączoną siecią Sepolia,
- adres RPC Sepolia, np. z Alchemy lub Infura,
- prywatny klucz testowego portfela Sepolia z niewielką ilością SepoliaETH.

## Uruchomienie od zera

### 0. Pobranie repozytorium

```bash
git clone git@github.com:dawidszczesniak/blockchain-platform.git
cd blockchain-platform
```

### 1. Pobranie zależności Solidity

```bash
npm install
```

### 2. Konfiguracja `.env.local`

W katalogu głównym projektu utwórz plik `.env.local`:

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

`ETH_PLATFORM_OPERATOR_PRIVATE_KEY` to prywatny klucz portfela testowego. Ten portfel zostanie właścicielem kontraktu, operatorem podpisującym wyniki i skarbcem prowizji.

Jeżeli ma być dostępny token ERC-20, pole `ETH_PLATFORM_SUPPORTED_ERC20_TOKENS` ma format:

```env
CODE|DISPLAY_NAME|SYMBOL|DECIMALS|ADDRESS
```

Dla prostego uruchomienia można zostawić je puste i korzystać tylko z SepoliaETH.

### 3. Uruchomienie bazy, Redis i sandboxów

```bash
docker compose --env-file .env.local up -d postgres redis sandbox-node-1 sandbox-node-2 sandbox-node-3
```

### 4. Wdrożenie kontraktu na Sepolii

```bash
set -a
source .env.local
set +a
forge clean
forge script solidity/scripts/DeployBlockchainTestContract.sol:DeployBlockchainTestContract \
  --rpc-url "$ETH_RPC_URL" \
  --broadcast
```

Po zakończeniu skrypt wypisze adres `Proxy`. Wstaw go w `.env.local`:

```env
ETH_PLATFORM_PROXY_ADDRESS=<PROXY_FROM_DEPLOY_LOG>
```

Opcjonalnie można sprawdzić wersję kontraktu:

```bash
cast call "$ETH_PLATFORM_PROXY_ADDRESS" "version()(string)" --rpc-url "$ETH_RPC_URL"
```

Oczekiwany wynik:

```text
1.0.0
```

### 5. Uruchomienie backendu

```bash
./gradlew :server:runLocalForce8080
```

Backend działa pod adresem:

```text
http://localhost:8080
```

Szybki test zdrowia:

```text
http://localhost:8080/health
```

### 6. Uruchomienie frontendu

W drugim terminalu:

```bash
./gradlew :composeApp:runLocalForce8081
```

Frontend działa pod adresem:

```text
http://localhost:8081
```

Po wejściu na stronę należy połączyć MetaMask z siecią Sepolia.

## Co można sprawdzić w aplikacji

1. Zalogowanie portfelem MetaMask.
2. Utworzenie zadania programistycznego z nagrodą i opłatą wpisową.
3. Dołączenie uczestnika do konkursu przez transakcję on-chain.
4. Wysłanie rozwiązania w Kotlinie i ocena przez węzły sandbox.
5. Zapis zaakceptowanego wyniku w kontrakcie.
6. Rozliczenie konkursu po terminie i podział środków.

## Testy projektu

Backend, frontend i sandbox:

```bash
./gradlew :server:test :composeApp:compileKotlinWasmJs :sandbox-runner:build
```

Kontrakt Solidity:

```bash
forge test
```

## Kontrakt

Aktualny kontrakt znajduje się w:

```text
solidity/contracts/BlockchainTestContract.sol
```

Kontrakt działa jako ERC-1967 UUPS proxy, obsługuje depozyty w ETH i opcjonalnych tokenach ERC-20, zapisuje zaakceptowane metryki zgłoszeń oraz rozlicza nagrodę, wpisowe i prowizję platformy. Prowizja platformy wynosi `200` punktów bazowych, czyli `2%`.
