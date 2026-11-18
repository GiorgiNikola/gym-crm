#!/bin/sh
# Mints a five minute service token (iss gym-crm, aud workload-service, scope workload:write) inside the
# gym-crm container, signed with the WORKLOAD_JWT_SECRET of that container. Pipe it, never print it:
#   scripts/messaging/mint.sh | scripts/messaging/produce.sh ...
set -eu
if [ -t 1 ]; then
  echo "mint.sh: refusing to write a token to a terminal; pipe it into produce.sh" >&2
  exit 1
fi
CONTAINER=${GYMCRM_CONTAINER:-gym-crm}
docker exec -i "$CONTAINER" sh -s <<'EOF'
b64url() { base64 | tr -d '=\n' | tr '/+' '_-'; }
now=$(date +%s)
h=$(printf '{"alg":"HS256"}' | b64url)
p=$(printf '{"sub":"gym-crm","iss":"gym-crm","aud":"workload-service","scope":"workload:write","iat":%d,"exp":%d}' "$now" "$((now + 300))" | b64url)
s=$(printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -hmac "$WORKLOAD_JWT_SECRET" -binary | b64url)
printf '%s.%s.%s\n' "$h" "$p" "$s"
EOF
