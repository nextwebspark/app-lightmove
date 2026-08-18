#!/usr/bin/env bash
# Phase 2g — what a signup does when the mail provider is down.
#
# Every other script runs with provider=log, which cannot fail, so the entire failure branch was
# unexercised. Here the sender is pointed at a dead port: EmailSender's contract says implementations
# must swallow delivery failures, so signup is expected to succeed while the mail never arrives. The
# question this answers is what happens to the user *after* that. Verification is step 2 now, so the
# answer got sharper and worse: they are stopped at the gate rather than carried through a wizard that
# is honoured later. This script pins that, and the one route out.
#
# REQUIRES the API booted against a black hole rather than Resend:
#   LIGHTMOVE_EMAIL_PROVIDER=resend \
#   LIGHTMOVE_EMAIL_RESEND_API_KEY=re_e2e_bogus_key_not_a_real_credential \
#   LIGHTMOVE_EMAIL_RESEND_BASE_URL=http://127.0.0.1:9
#
# Port 9 is discard and nothing listens, so the connection is refused locally. That is deliberate: a
# bogus key alone would still send the request to Resend's servers. This cannot leave the machine.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# Guard: refuse to run against a provider that could actually deliver.
if grep -q "Email provider is 'log'" "$API_LOG" 2>/dev/null; then
  printf '\033[31mThis script needs the API booted with the failing-sender variant — see the header.\033[0m\n'
  exit 1
fi

VICTIM=$(new_email outage)
WS="Outage Co $(date +%s)$RANDOM"

section "N41  signup survives a mail provider that is down"

START=$(python3 -c 'import time;print(int(time.time()*1000))')
post_json /auth/signup "$(jq -nc --arg e "$VICTIM" --arg p "$PASSWORD" \
  '{fullName:"Ollie Outage", email:$e, password:$p, termsAccepted:true}')"
ELAPSED=$(( $(python3 -c 'import time;print(int(time.time()*1000))') - START ))
check_status N41.1 "signup still succeeds when the send fails" 201
TOKEN=$(json '.accessToken')
check N41.2 "the account exists" "1" "$(sql "SELECT count(*) FROM app_lm_user WHERE email = '$VICTIM'")"
note N41.3 "signup took ${ELAPSED}ms with the provider refusing connections"

check N41.4 "the failure is logged" "true" \
  "$(test "$(grep -c "Failed to send" "$API_LOG")" -ge 1 && echo true || echo false)"
check N41.5 "and the log does not carry the verification link" "0" \
  "$(grep -c "auth/verify?token=" "$API_LOG")"

# A token was minted and stored. It exists, it is valid for 24 hours, and nobody has it.
check N41.6 "a verification token was issued even though nothing was delivered" "1" \
  "$(sql "SELECT count(*) FROM app_lm_verification_token t JOIN app_lm_user u ON u.id = t.user_id
          WHERE u.email = '$VICTIM' AND t.purpose = 'EMAIL_VERIFICATION' AND t.consumed_at IS NULL")"

# The audit trail is the record of what happened. Here it says a mail went out that did not.
AUDITED=$(sql "SELECT count(*) FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
               WHERE u.email = '$VICTIM' AND a.event_type = 'EMAIL_VERIFICATION_SENT'")
if [ "$AUDITED" = "0" ]; then
  pass N41.7 "no EMAIL_VERIFICATION_SENT event for a mail that never sent"
else
  note N41.7 "EMAIL_VERIFICATION_SENT recorded ($AUDITED) although delivery failed — see findings"
fi
note N41.8 "audit outcome: $(sql "SELECT outcome FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
                                  WHERE u.email = '$VICTIM' AND a.event_type = 'EMAIL_VERIFICATION_SENT' LIMIT 1")"

section "N42  the wizard stops dead at the gate"

# The trade the blocking verify step makes, stated plainly. When verification came last, this user
# could fill in the whole wizard and have it honoured whenever the mail finally arrived. Now they
# cannot start it, and what they would have typed is not captured anywhere.
post_json /onboarding/workspace "$(jq -nc --arg n "$WS" \
  '{name:$n, companySize:"11-50 people", primaryRegion:"GCC", teamFocus:"Executive search"}')" \
  -H "$(auth_header "$TOKEN")"
check_code N42.1 "the organisation step is refused" 403 EMAIL_NOT_VERIFIED

post_json /onboarding/invitations "$(jq -nc --arg a "$(new_email colleague)" '[{email:$a, role:"MEMBER"}]')" \
  -H "$(auth_header "$TOKEN")"
check_status N42.2 "the invite step too" 403

get /auth/me -H "$(auth_header "$TOKEN")"
check N42.3 "the user has no workspace" "null" "$(json '.workspace')"
check N42.4 "and is plainly unverified, which is what the SPA parks them on" "false" "$(json '.emailVerified')"
note N42.5 "nothing in the response says the email failed — the SPA cannot distinguish this from a mail in flight"

section "N43  every route out of the hole"

http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$VICTIM" '{email:$e}')"
check_status N43.1 "resend answers 202" 202
check N43.2 "but nothing was delivered by it either" "true" \
  "$(test "$(grep -c "Failed to send" "$API_LOG")" -ge 2 && echo true || echo false)"

http POST /auth/password/forgot -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$VICTIM" '{email:$e}')"
check_status N43.3 "password reset — the other route past verification — also answers 202" 202
note N43.4 "and also delivers nothing, so the C3 escape hatch is shut too"

post_json /auth/login "$(jq -nc --arg e "$VICTIM" --arg p "$PASSWORD" '{email:$e, password:$p}')"
check_status N43.5 "the user can still log in" 200
LIVE=$(json '.accessToken')

get /projects -H "$(auth_header "$LIVE")"
check_code N43.6 "but reaches no workspace data" 403 EMAIL_NOT_VERIFIED

check N43.7 "the workspace they typed still does not exist" "0" \
  "$(sql "SELECT count(*) FROM app_lm_workspace WHERE name = '$WS'")"
# Scoped to this victim rather than counting the whole table. A bare count only means anything against
# a virgin database, and run-all.sh keeps the container between legs — so the ~30 invitations legs 1
# and 2 legitimately created read as a failure here. The claim is about THIS user's invitees: their
# colleague is invited by them, or not at all.
check N43.8 "their colleague was never invited" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation i
            JOIN app_lm_user u ON u.id = i.invited_by
           WHERE u.email = '$VICTIM'")"

# The one route out, and it is narrow: a resend mints a fresh 24h token whenever the provider comes
# back, and the account is otherwise untouched — so the user resumes at step 2 exactly where they
# stopped. What they had *typed* is gone, because with the gate in front of the wizard there is
# nothing to type yet.
check N43.9 "the account is intact and still awaiting its link" "1" \
  "$(sql "SELECT count(*) FROM app_lm_verification_token t JOIN app_lm_user u ON u.id = t.user_id
          WHERE u.email = '$VICTIM' AND t.purpose = 'EMAIL_VERIFICATION' AND t.consumed_at IS NULL")"
check N43.10 "and nothing was created on their firm's domain in the meantime" "0" \
  "$(sql "SELECT count(*) FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$VICTIM'")"
note N43.11 "so recovery is one resend once the provider is healthy — but nothing prompts the user, and nothing alerts an operator"

summary
