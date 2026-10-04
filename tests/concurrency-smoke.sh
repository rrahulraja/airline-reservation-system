#!/usr/bin/env bash
#
# Proves the double-booking guarantee against a running stack, with no Java toolchain
# required. Fires N parallel requests for the SAME seat and asserts exactly one 201 and
# N-1 409s.
#
# Usage: bash tests/concurrency-smoke.sh [parallelism] [seat]

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PARALLEL="${1:-20}"
SEAT="${2:-29F}"

echo "Logging in as customer..."
TOKEN=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"customer123"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "FAILED: could not obtain a token. Is the stack running at $BASE_URL?" >&2
  exit 1
fi

# XY101 operates Monday, Wednesday and Friday.
FLIGHT_DATE=$(date -u -d 'next Wednesday' +%Y-%m-%d)
echo "Target: XY101 on $FLIGHT_DATE, seat $SEAT, $PARALLEL parallel attempts"

PAYLOAD=$(printf '{"flightNumber":"XY101","flightDate":"%s","contactName":"Smoke","passengers":[{"fullName":"Smoke Test","seatLabel":"%s"}]}' "$FLIGHT_DATE" "$SEAT")

STATUS_FILE=$(mktemp)
trap 'rm -f "$STATUS_FILE"' EXIT

seq 1 "$PARALLEL" | xargs -P "$PARALLEL" -I{} sh -c "
  curl -sS -o /dev/null -w '%{http_code}\n' \
    -X POST '$BASE_URL/api/bookings' \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer $TOKEN' \
    -d '$PAYLOAD'
" >> "$STATUS_FILE"

CREATED=$(grep -c '^201$' "$STATUS_FILE" || true)
CONFLICT=$(grep -c '^409$' "$STATUS_FILE" || true)
OTHER=$(grep -vc -e '^201$' -e '^409$' "$STATUS_FILE" || true)

echo
echo "  201 Created        : $CREATED"
echo "  409 Conflict       : $CONFLICT"
echo "  other status codes : $OTHER"
echo

if [ "$CREATED" -eq 1 ] && [ "$CONFLICT" -eq $((PARALLEL - 1)) ] && [ "$OTHER" -eq 0 ]; then
  echo "PASS: exactly one booking won the seat; $CONFLICT lost cleanly with 409."
  exit 0
fi

# OTHER must be zero, not merely small: a 500 there is the flush-timing bug surfacing in
# production rather than in a test.
echo "FAIL: expected exactly 1x201 and $((PARALLEL - 1))x409 with no other codes." >&2
echo "A 500 here means the explicit flush() in SeatClaimService is missing." >&2
sort "$STATUS_FILE" | uniq -c >&2
exit 1
