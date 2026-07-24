FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system findguni \
    && useradd --system --gid findguni --home-dir /app findguni

WORKDIR /app
COPY --from=build --chown=findguni:findguni /workspace/target/findguni-1.0.0.jar /app/app.jar

ENV PORT=10000 \
    MALLOC_ARENA_MAX=2 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -Xss512k -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Djdk.nio.maxCachedBufferSize=262144"

USER findguni
EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
