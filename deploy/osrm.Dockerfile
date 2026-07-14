FROM ghcr.io/project-osrm/osrm-backend:v5.27.1

USER root
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl ca-certificates gosu \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10002 osrm \
    && useradd --system --uid 10002 --gid osrm --home-dir /data --shell /usr/sbin/nologin osrm

COPY deploy/osrm/entrypoint.sh /usr/local/bin/floodgis-osrm-entrypoint
RUN sed -i 's/\r$//' /usr/local/bin/floodgis-osrm-entrypoint \
    && chmod 0755 /usr/local/bin/floodgis-osrm-entrypoint

EXPOSE 5000
ENTRYPOINT ["/usr/local/bin/floodgis-osrm-entrypoint"]
