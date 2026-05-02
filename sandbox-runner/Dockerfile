FROM eclipse-temurin:21-jdk-jammy

ARG KOTLIN_VERSION=2.2.20

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/kotlin-compiler.zip "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
    && unzip /tmp/kotlin-compiler.zip -d /opt \
    && ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc \
    && ln -sf /opt/kotlinc/bin/kotlin /usr/local/bin/kotlin \
    && rm -f /tmp/kotlin-compiler.zip

RUN useradd --create-home --uid 10001 sandbox
WORKDIR /app
COPY runner.py /app/runner.py
RUN chown -R sandbox:sandbox /app

USER sandbox

ENV SANDBOX_PORT=8080
ENV SANDBOX_NODE_ID=sandbox-node
ENV SANDBOX_IMAGE_HASH=unknown
ENV SANDBOX_VERSION=1

EXPOSE 8080

ENTRYPOINT ["python3", "/app/runner.py"]
