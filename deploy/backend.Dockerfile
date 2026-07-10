FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline

COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml -Dmaven.test.skip=true package \
    && cp "$(find backend/target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 floodgis \
    && useradd --system --uid 10001 --gid floodgis --home-dir /app --shell /usr/sbin/nologin floodgis \
    && mkdir -p /app/data/uploads \
    && chown -R floodgis:floodgis /app

WORKDIR /app
COPY --from=build --chown=floodgis:floodgis /workspace/app.jar /app/app.jar

USER floodgis
EXPOSE 8080

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
