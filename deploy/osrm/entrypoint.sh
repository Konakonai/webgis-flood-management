#!/usr/bin/env sh
set -eu

log() {
    printf '[floodgis-osrm] %s\n' "$*"
}

DATA_DIR=/data
DATA_FILE=${OSRM_DATA_FILE:-jiangsu-latest.osm.pbf}
DATA_URL=${OSRM_DATA_URL:-https://download.geofabrik.de/asia/china/jiangsu-latest.osm.pbf}
PROFILE=${OSRM_PROFILE:-car}
THREADS=${OSRM_THREADS:-2}
FORCE_REBUILD=${OSRM_FORCE_REBUILD:-false}

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$DATA_DIR"
    chown osrm:osrm "$DATA_DIR"
    exec gosu osrm "$0" "$@"
fi

case "$DATA_FILE" in
    *.osm.pbf) ;;
    *)
        log "OSRM_DATA_FILE must end with .osm.pbf (received: $DATA_FILE)"
        exit 2
        ;;
esac

case "$PROFILE" in
    car|bicycle|foot) ;;
    *)
        log "OSRM_PROFILE must be one of: car, bicycle, foot"
        exit 2
        ;;
esac

case "$THREADS" in
    ''|*[!0-9]*|0)
        log "OSRM_THREADS must be a positive integer"
        exit 2
        ;;
esac

PBF_PATH="$DATA_DIR/$DATA_FILE"
BASE_PATH=${PBF_PATH%.osm.pbf}
PROFILE_PATH="/opt/$PROFILE.lua"
MARKER="$DATA_DIR/.floodgis-osrm-prepared"

mkdir -p "$DATA_DIR"

if [ ! -s "$PBF_PATH" ]; then
    if [ -z "$DATA_URL" ]; then
        log "No PBF is present and OSRM_DATA_URL is empty"
        exit 2
    fi

    log "Downloading OSM extract from $DATA_URL"
    rm -f "$PBF_PATH.part"
    curl --fail --location --retry 5 --retry-delay 5 --connect-timeout 30 \
        --output "$PBF_PATH.part" "$DATA_URL"
    mv "$PBF_PATH.part" "$PBF_PATH"
fi

SIGNATURE="$PROFILE|mld|$(stat -c '%s:%Y' "$PBF_PATH")"
NEEDS_BUILD=false

if [ "$FORCE_REBUILD" = "true" ] || [ "$FORCE_REBUILD" = "1" ]; then
    NEEDS_BUILD=true
elif [ ! -f "$MARKER" ] || ! grep -Fqx "$SIGNATURE" "$MARKER"; then
    NEEDS_BUILD=true
elif [ ! -e "$BASE_PATH.osrm.partition" ] || [ ! -e "$BASE_PATH.osrm.cell_metrics" ]; then
    NEEDS_BUILD=true
fi

if [ "$NEEDS_BUILD" = "true" ]; then
    log "Preparing $DATA_FILE with the $PROFILE profile (MLD algorithm)"
    rm -f "$MARKER" "$BASE_PATH.osrm"*
    osrm-extract -p "$PROFILE_PATH" "$PBF_PATH"
    osrm-partition "$BASE_PATH.osrm"
    osrm-customize "$BASE_PATH.osrm"
    printf '%s\n' "$SIGNATURE" > "$MARKER"
    log "OSRM preprocessing completed"
else
    log "Reusing prepared OSRM data from the persistent volume"
fi

log "Starting OSRM on 0.0.0.0:5000 with $THREADS threads"
exec osrm-routed --algorithm mld --ip 0.0.0.0 --port 5000 \
    --threads "$THREADS" "$BASE_PATH.osrm"
