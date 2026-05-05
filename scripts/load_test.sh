#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# load_test.sh
#
# Simulates a realistic stream of event status updates against the running
# sports-live-events-service. Events are brought live and not live at random
# intervals so the app's scheduler starts/stops polling jobs, Kafka receives
# score messages, and Prometheus metrics accumulate — ready to explore at
# http://localhost:9090.
#
# Usage:
#   ./scripts/load_test.sh [BASE_URL] [DURATION_SECONDS]
#
# Defaults:
#   BASE_URL          http://localhost:8080
#   DURATION_SECONDS  120   (run for 2 minutes then stop)
#
# Requirements: curl, jq (optional — used only for pretty-printing responses)
# -----------------------------------------------------------------------------

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
BASE_URL="${1:-http://localhost:8080}"
DURATION="${2:-120}"

# Pool of event IDs the script will work with.
EVENT_IDS=("match-101" "match-202" "match-303" "game-404" "game-505"
           "race-606" "race-707" "bout-808")

# Minimum and maximum delay between requests, in seconds.
MIN_DELAY=1
MAX_DELAY=8

# Probability (0-100) that any given update sets the event LIVE vs NOT_LIVE.
# 70 means 70 % of updates are LIVE, keeping several events active at once.
LIVE_PROBABILITY=70

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ── Helpers ───────────────────────────────────────────────────────────────────
log()  { echo -e "$(date '+%H:%M:%S') $*"; }
ok()   { log "${GREEN}✓${RESET} $*"; }
warn() { log "${YELLOW}⚠${RESET}  $*"; }
err()  { log "${RED}✗${RESET} $*"; }
info() { log "${CYAN}ℹ${RESET}  $*"; }

# Pick a random element from an array.
random_pick() {
  local arr=("$@")
  echo "${arr[$(( RANDOM % ${#arr[@]} ))]}"
}

# Return "live" or "not live" based on LIVE_PROBABILITY.
random_status() {
  if (( RANDOM % 100 < LIVE_PROBABILITY )); then
    echo "live"
  else
    echo "not live"
  fi
}

# Random integer between MIN_DELAY and MAX_DELAY (inclusive).
random_delay() {
  echo $(( MIN_DELAY + RANDOM % (MAX_DELAY - MIN_DELAY + 1) ))
}

# Wait for the service to be healthy before starting the loop.
wait_for_service() {
  info "Checking service health at ${BASE_URL}/actuator/health …"
  local attempts=0
  until curl -sf "${BASE_URL}/actuator/health" | grep -q '"UP"' 2>/dev/null; do
    (( attempts++ ))
    if (( attempts >= 15 )); then
      err "Service did not become healthy after ${attempts} attempts. Is it running?"
      err "Start it with:  docker compose up --build -d"
      exit 1
    fi
    warn "Service not ready yet (attempt ${attempts}/15). Retrying in 3 s …"
    sleep 3
  done
  ok "Service is healthy."
}

# Send one POST /events/status request and print the outcome.
send_update() {
  local event_id="$1"
  local status="$2"

  # The API accepts both JSON booleans and plain strings.
  # Mix the formats randomly to exercise both code paths.
  local body
  if (( RANDOM % 2 == 0 )); then
    # String form: "live" or "not live"
    body=$(printf '{"eventId":"%s","status":"%s"}' "$event_id" "$status")
  else
    # Boolean form: true for live, false for not live
    local bool_val
    [[ "$status" == "live" ]] && bool_val="true" || bool_val="false"
    body=$(printf '{"eventId":"%s","status":%s}' "$event_id" "$bool_val")
  fi

  local http_code response
  response=$(curl -s -o /tmp/load_test_response.json -w "%{http_code}" \
    -X POST "${BASE_URL}/events/status" \
    -H "Content-Type: application/json" \
    -d "$body" 2>/dev/null)
  http_code="$response"

  local pretty_body
  if command -v jq &>/dev/null; then
    pretty_body=$(jq -c '.' /tmp/load_test_response.json 2>/dev/null || cat /tmp/load_test_response.json)
  else
    pretty_body=$(cat /tmp/load_test_response.json)
  fi

  if [[ "$http_code" == "200" ]]; then
    local status_icon
    [[ "$status" == "live" ]] && status_icon="${GREEN}▶ LIVE${RESET}" || status_icon="${RED}■ NOT LIVE${RESET}"
    ok "${BOLD}${event_id}${RESET}  →  ${status_icon}  ${CYAN}${pretty_body}${RESET}"
  else
    err "HTTP ${http_code} for ${event_id} → ${status}  body: ${pretty_body}"
  fi
}

# Print a summary of Prometheus metrics that are relevant to this service.
print_metrics_summary() {
  info "Fetching metrics snapshot …"
  local metrics
  metrics=$(curl -sf "${BASE_URL}/actuator/prometheus" 2>/dev/null || true)
  if [[ -z "$metrics" ]]; then
    warn "Could not reach ${BASE_URL}/actuator/prometheus"
    return
  fi

  echo ""
  echo -e "${BOLD}  ── Metrics snapshot ────────────────────────────────────${RESET}"
  for metric in live_events_count score_poll_success_total score_poll_failure_total \
                score_publish_success_total score_publish_failure_total; do
    local val
    val=$(echo "$metrics" | grep "^${metric}" | grep -v "#" | awk '{print $2}' | head -1)
    [[ -z "$val" ]] && val="–"
    printf "  %-38s %s\n" "${metric}" "${val}"
  done
  echo -e "${BOLD}  ─────────────────────────────────────────────────────────${RESET}"
  echo ""
}

# ── Cleanup ───────────────────────────────────────────────────────────────────
# On exit (Ctrl+C or normal end) set all events NOT_LIVE so the app is clean.
cleanup() {
  echo ""
  info "Shutting down — setting all events to NOT_LIVE …"
  for event_id in "${EVENT_IDS[@]}"; do
    curl -sf -X POST "${BASE_URL}/events/status" \
      -H "Content-Type: application/json" \
      -d "{\"eventId\":\"${event_id}\",\"status\":\"not live\"}" \
      -o /dev/null 2>/dev/null || true
  done
  print_metrics_summary
  info "Done. Explore full metrics: ${CYAN}${BASE_URL}/actuator/prometheus${RESET}"
  info "Explore in Prometheus UI:  ${CYAN}http://localhost:9090${RESET}"
  info "Explore Swagger UI:        ${CYAN}${BASE_URL}/swagger-ui.html${RESET}"
}
trap cleanup EXIT

# ── Main ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║         Sports Live Events — Load Test Script            ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${RESET}"
echo ""
info "Target:       ${BASE_URL}"
info "Duration:     ${DURATION}s"
info "Events pool:  ${EVENT_IDS[*]}"
info "Live chance:  ${LIVE_PROBABILITY}%"
info "Delay range:  ${MIN_DELAY}–${MAX_DELAY}s"
echo ""

wait_for_service

echo ""
info "Starting load test. Press ${BOLD}Ctrl+C${RESET} to stop early."
echo ""

# Bring a few events LIVE immediately so polling starts right away.
info "Warming up — setting first 3 events LIVE …"
for event_id in "${EVENT_IDS[@]:0:3}"; do
  send_update "$event_id" "live"
done
echo ""

# Main loop — run until DURATION seconds have elapsed.
start_time=$(date +%s)
request_count=0

while true; do
  elapsed=$(( $(date +%s) - start_time ))
  if (( elapsed >= DURATION )); then
    info "Duration of ${DURATION}s reached (${request_count} requests sent)."
    break
  fi

  event_id=$(random_pick "${EVENT_IDS[@]}")
  status=$(random_status)
  delay=$(random_delay)

  send_update "$event_id" "$status"
  (( request_count++ ))

  # Print a metrics snapshot every 30 seconds.
  if (( request_count % 10 == 0 )); then
    print_metrics_summary
  fi

  info "Next request in ${delay}s  (${elapsed}/${DURATION}s elapsed, ${request_count} sent)"
  sleep "$delay"
done
