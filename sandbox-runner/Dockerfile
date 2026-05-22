FROM eclipse-temurin:21-jdk-jammy

ARG KOTLIN_VERSION=2.2.20

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/kotlin-compiler.zip "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
    && unzip /tmp/kotlin-compiler.zip -d /opt \
    && ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc \
    && ln -sf /opt/kotlinc/bin/kotlin /usr/local/bin/kotlin \
    && rm -f /tmp/kotlin-compiler.zip

RUN useradd --create-home --uid 10001 sandbox
WORKDIR /app
COPY src /app/src
RUN kotlinc $(find /app/src/main/kotlin -name '*.kt' -print) -include-runtime -d /app/sandbox-runner.jar
RUN chown -R sandbox:sandbox /app

USER sandbox

ENV SANDBOX_PORT=8080
ENV SANDBOX_HOST=0.0.0.0
ENV SANDBOX_NODE_ID=sandbox-node
ENV SANDBOX_IMAGE_HASH=unknown
ENV SANDBOX_VERSION=1

EXPOSE 8080

ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "-jar", "/app/sandbox-runner.jar"]
