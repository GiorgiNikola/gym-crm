#!/bin/bash
# Live demo helper for the messaging scenarios in the README, against the compose stack.
#   demo.sh setup                          register a demo trainer and trainee
#   demo.sh login                          log both in
#   demo.sh add <yyyy-mm-dd> <min> <tx>    POST /trainings as the trainee, with X-Transaction-Id
#   demo.sh read <yyyy> <m>                trainer's monthly workload, through gym-crm
#   demo.sh event <yyyy-mm-dd> <min>       a valid event body for the demo trainer, with a new eventId
#   demo.sh q                              message, consumer and delivery counts of both queues
#   demo.sh dlq                            headers of the dead-lettered messages, authorization dropped
#   demo.sh health                         broker parts of both /actuator/health
# Usernames, generated passwords and user tokens are kept in a mode 600 state file outside the repository
# and never printed. Broker credentials are used only inside the artemis container.
set -euo pipefail
BASE=${GYMCRM_URL:-http://localhost:8080}
STATE=${GYMCRM_DEMO_STATE:-$HOME/.cache/gym-crm/messaging-demo.state}
umask 077
mkdir -p "$(dirname "$STATE")"
[ -f "$STATE" ] && . "$STATE"

json() { python3 -c "import sys,json;print(json.load(sys.stdin)[\"$1\"])"; }
save() { grep -v "^$1=" "$STATE" > "$STATE.tmp" 2>/dev/null || true; printf '%s=%q\n' "$1" "$2" >> "$STATE.tmp"; mv "$STATE.tmp" "$STATE"; }
in_artemis() { docker exec artemis sh -c "$1"; }

case "${1:-}" in
  setup)
    trainer=$(curl -sf -X POST "$BASE/trainers" -H 'Content-Type: application/json' \
      -d '{"firstName":"Demo","lastName":"Coach","specialization":"YOGA"}')
    trainee=$(curl -sf -X POST "$BASE/trainees" -H 'Content-Type: application/json' \
      -d '{"firstName":"Demo","lastName":"Member","dateOfBirth":"1998-05-14","address":"1 Main St"}')
    : > "$STATE"
    save TRAINER "$(json username <<< "$trainer")"
    save TRAINER_PW "$(json password <<< "$trainer")"
    save TRAINEE "$(json username <<< "$trainee")"
    save TRAINEE_PW "$(json password <<< "$trainee")"
    . "$STATE"
    echo "trainer=$TRAINER trainee=$TRAINEE (state in $STATE)"
    ;;
  login)
    for who in TRAINEE TRAINER; do
      user=${!who}; pw_var=${who}_PW
      token=$(curl -sf -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$user\",\"password\":\"${!pw_var}\"}" | json token)
      save "${who}_TOKEN" "$token"
    done
    echo "logged in $TRAINEE and $TRAINER"
    ;;
  add)
    curl -s -o /dev/null -w "POST /trainings -> %{http_code} in %{time_total}s (tx $4)\n" -X POST "$BASE/trainings" \
      -H "Authorization: Bearer $TRAINEE_TOKEN" -H 'Content-Type: application/json' -H "X-Transaction-Id: $4" \
      -d "{\"traineeUsername\":\"$TRAINEE\",\"trainerUsername\":\"$TRAINER\",\"trainingName\":\"Demo\",\"trainingDate\":\"$2\",\"trainingDuration\":$3}"
    ;;
  read)
    curl -s -w " -> %{http_code}\n" -H "Authorization: Bearer $TRAINER_TOKEN" -H "X-Transaction-Id: read-$RANDOM" \
      "$BASE/trainers/$TRAINER/workload/$2/$3"
    ;;
  event)
    printf '{"eventId":"%s","occurredAt":"%s","trainerUsername":"%s","trainerFirstName":"Demo","trainerLastName":"Coach","isActive":true,"trainingDate":"%s","trainingDuration":%s,"actionType":"ADD"}\n' \
      "$(uuidgen | tr 'A-Z' 'a-z')" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$TRAINER" "$2" "$3"
    ;;
  q)
    in_artemis '/var/lib/artemis-instance/bin/artemis queue stat --silent --url tcp://localhost:61616 --user "$ARTEMIS_USER" --password "$ARTEMIS_PASSWORD" --queueName trainer.workload' \
      | grep -E "NAME|trainer.workload"
    ;;
  dlq)
    in_artemis 'curl -s -u "$ARTEMIS_USER:$ARTEMIS_PASSWORD" -H "Origin: http://localhost" -H "Content-Type: application/json" \
      -d "{\"type\":\"exec\",\"mbean\":\"org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\",component=addresses,address=\\\"DLQ.trainer.workload\\\",subcomponent=queues,routing-type=\\\"anycast\\\",queue=\\\"DLQ.trainer.workload\\\"\",\"operation\":\"browse()\"}" \
      http://localhost:8161/console/jolokia/' | python3 -c '
import sys, json
keep = ("dlqReason", "dlqDetail", "transactionId", "eventId", "originalQueue", "_AMQ_ORIG_QUEUE")
for m in json.load(sys.stdin)["value"]:
    props = m.get("StringProperties") or {}
    props.pop("authorization", None)
    print({**{k: props[k] for k in keep if k in props}, "text": m.get("text")})'
    ;;
  health)
    curl -s http://localhost:8080/actuator/health | python3 -c 'import sys,json;h=json.load(sys.stdin);print("gym-crm", h["status"], h["components"].get("broker"))'
    curl -s http://localhost:8081/actuator/health | python3 -c 'import sys,json;h=json.load(sys.stdin);print("workload-service", h["status"], h["components"].get("broker"))'
    ;;
  *)
    sed -n '2,12p' "$0"
    exit 1
    ;;
esac
