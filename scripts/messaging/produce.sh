#!/bin/sh
# Sends a raw message to trainer.workload with the artemis CLI inside the artemis container, using the
# broker credentials of that container. The service token, if any, is read from stdin and never echoed.
#   scripts/messaging/mint.sh | scripts/messaging/produce.sh <transactionId> <count> '<json body>'
#   scripts/messaging/produce.sh --no-token <transactionId> <count> '<body>'
set -eu
TOKEN=""
if [ "${1:-}" = "--no-token" ]; then
  shift
else
  if [ -t 0 ]; then
    echo "produce.sh: pipe a token from mint.sh, or pass --no-token" >&2
    exit 1
  fi
  read -r TOKEN || true
  if [ -z "$TOKEN" ]; then
    echo "produce.sh: no token on stdin" >&2
    exit 1
  fi
fi
if [ $# -ne 3 ]; then
  echo "usage: produce.sh [--no-token] <transactionId> <count> <body>" >&2
  exit 1
fi
GROUP=$(printf '%s' "$3" | sed -n 's/.*"trainerUsername" *: *"\([^"]*\)".*/\1/p')
printf '%s\n' "$TOKEN" | docker exec -i "${ARTEMIS_CONTAINER:-artemis}" sh -c '
read -r T
TX=$1; COUNT=$2; BODY=$3; GROUP=$4
set --
P="{\"type\":\"string\",\"key\":\"eventType\",\"value\":\"workloadChange\"},{\"type\":\"int\",\"key\":\"eventVersion\",\"value\":\"1\"},{\"type\":\"string\",\"key\":\"transactionId\",\"value\":\"$TX\"}"
[ -n "$T" ] && P="$P,{\"type\":\"string\",\"key\":\"authorization\",\"value\":\"$T\"}"
set -- --properties "[$P]"
[ -n "$GROUP" ] && set -- "$@" --group "$GROUP"
/var/lib/artemis-instance/bin/artemis producer --silent --url tcp://localhost:61616 \
  --user "$ARTEMIS_USER" --password "$ARTEMIS_PASSWORD" \
  --destination queue://trainer.workload --message-count "$COUNT" --message "$BODY" "$@" 2>&1 | tail -1
' sh "$1" "$2" "$3" "$GROUP"
