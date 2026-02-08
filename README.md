### Environments (local / staging / prod)

The web app reads environment config from a generated Kotlin file at build time.

Run backend + frontend together (two terminals):

- local (default) backend: `./gradlew :server:run -PappEnv=local -PcorsAllowedHosts=localhost:8080,127.0.0.1:8080`
- local (default) frontend: `./gradlew :composeApp:jsBrowserDevelopmentRun -PappEnv=local -PapiBaseUrl=http://localhost:8081`
- staging backend: `./gradlew :server:run -PappEnv=staging -PcorsAllowedHosts=localhost:8080,127.0.0.1:8080`
- staging frontend: `./gradlew :composeApp:jsBrowserProductionWebpack -PappEnv=staging -PapiBaseUrl=https://staging-api.your-domain.com`
- prod backend: `./gradlew :server:run -PappEnv=prod -PcorsAllowedHosts=localhost:8080,127.0.0.1:8080`
- prod frontend: `./gradlew :composeApp:jsBrowserProductionWebpack -PappEnv=prod -PapiBaseUrl=https://api.your-domain.com`

### Trunk-based workflow (recommended)

- `main` is the trunk.
- Short-lived feature branches are merged quickly into `main`.
- Staging deploys from `main`.
- Production deploys from tags (e.g., `v1.2.3`) on `main`.
- PR preview environments can be added later as a CI/CD step.
