#!/usr/bin/env bash
# The Outreach conversation workspace (docs/outreach-conversations.md) as far as a stack without a mail
# service reaches it: the action queue and one executive's conversation, a note landing in their history, the
# composer refusing to write without a mailbox — attachments included, leaving nothing behind — the drawer's
# outreach read, and a client seat kept out of all of it. Sends, replies and the files' journey through a mail
# service need a gateway this stack does not have; @IntegrationTest covers them with a recording one.
#
# Builds its own cast: a lead, and a client representative attached to the mandate.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

section "21 — the Outreach conversation workspace"

login_as() { # login_as EMAIL -> access token
  post_json /auth/login "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
  json '.accessToken'
}

signup_verified() { # signup_verified EMAIL FULLNAME -> access token, verified, no workspace yet
  post_json /auth/signup "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" \
    '{fullName:$n, email:$e, password:$p, termsAccepted:true}')" >/dev/null
  post_json /auth/verify "$(jq -nc --arg t "$(token_for "$1" verify)" '{token:$t}')" >/dev/null
  login_as "$1"
}

accept_invite_signup() { # accept_invite_signup EMAIL FULLNAME
  post_json /onboarding/accept-invitation-signup \
    "$(jq -nc --arg t "$(token_for "$1" accept-invite)" --arg n "$2" --arg p "$PASSWORD" \
       '{token:$t, fullName:$n, password:$p}')" >/dev/null
}

# --- the cast -------------------------------------------------------------------

LEAD_EMAIL=$(new_email convlead)
REP_EMAIL=$(new_email convrep)
FATIMA_EMAIL=$(new_email fatima)

LEAD_TOKEN=$(signup_verified "$LEAD_EMAIL" "Yara Haddad")
post_json /onboarding/workspace "$(jq -nc --arg n "Conversation UAT $(date +%s)$RANDOM" '{mode:"COMPANY", name:$n}')" \
  -H "$(auth_header "$LEAD_TOKEN")" >/dev/null
# A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
LEAD_TOKEN=$(login_as "$LEAD_EMAIL")
LEAD="$(auth_header "$LEAD_TOKEN")"
LEAD_ID=$(sql "SELECT id FROM app_lm_user WHERE email = '$LEAD_EMAIL'")

post_json /clients '{"customName":"Conversation Holding"}' -H "$LEAD" >/dev/null
CLIENT_ID=$(json '.id')
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Chief Financial Officer"}')" -H "$LEAD"
check_status 21.0 "the lead creates the mandate" 201
PROJECT=$(json '.id')

post_json "/projects/$PROJECT/representatives/invitations" \
  "$(jq -nc --arg e "$REP_EMAIL" '{fullName:"Cora Client", position:"Group CFO", email:$e}')" -H "$LEAD" >/dev/null
accept_invite_signup "$REP_EMAIL" "Cora Client"
REP="$(auth_header "$(login_as "$REP_EMAIL")")"

post_json "/projects/$PROJECT/candidates" "$(jq -nc --arg e "$FATIMA_EMAIL" \
  '{fullName:"Fatima Al Mansoori", title:"Group CFO", employerName:"Aurora Holdings", email:$e}')" -H "$LEAD"
check_status 21.0a "an executive with an email on file is mapped" 201
FATIMA=$(json '.id')

# --- the queue ------------------------------------------------------------------

section "21.1  the action queue"

post_json "/projects/$PROJECT/candidates/$FATIMA/tasks" "$(jq -nc --arg a "$LEAD_ID" \
  '{title:"Prepare the first approach", kind:"todo", assigneeUserId:$a, dueAt:"2030-01-06T09:00:00Z"}')" -H "$LEAD"
check_status 21.1 "a task is filed on her" 201

get "/projects/$PROJECT/outreach/conversations?view=needs&who=mine" -H "$LEAD"
check_status 21.2 "the lead reads Needs action" 200
check 21.3 "she waits in Preparation: an open task, and nobody has written to her yet" "PREPARATION" \
  "$(json "[.rows[] | select(.candidateId == \"$FATIMA\") | .group] | first // empty")"
check 21.4 "the tab counts her" "1" "$(json '.counts.needs')"

get "/projects/$PROJECT/outreach/conversations?view=needs&who=mine&q=Aurora" -H "$LEAD"
check 21.5 "a search over her employer still finds her" "1" "$(json '.rows | length')"
get "/projects/$PROJECT/outreach/conversations?view=needs&who=mine&q=Nobody%20Like%20This" -H "$LEAD"
check 21.6 "a search over nothing she is finds nobody" "0" "$(json '.rows | length')"

# --- one conversation ---------------------------------------------------------------

section "21.2  her conversation"

get "/projects/$PROJECT/outreach/conversations/$FATIMA" -H "$LEAD"
check_status 21.10 "the lead opens her conversation" 200
check 21.11 "it carries her address as the composer offers it" "$FATIMA_EMAIL" "$(json '.contact.emails[0].address')"
check 21.12 "there is no thread to reply in yet" "null" "$(json '.composer.replySubject')"
check 21.13 "the composer is told what one email may carry" "5" "$(json '.composer.attachmentLimits.maxFiles')"

post_json "/projects/$PROJECT/candidates/$FATIMA/notes" \
  '{"kind":"call","body":"Spoke briefly; she asked for the brief in writing.","format":"plain"}' -H "$LEAD"
check_status 21.14 "a call is logged on her" 201
get "/projects/$PROJECT/outreach/conversations/$FATIMA" -H "$LEAD"
check 21.15 "the call note is a line of her history" "call" \
  "$(json '[.events[] | select(.type == "NOTE") | .note.kind] | first // empty')"

get "/projects/$PROJECT/outreach/candidates/$FATIMA" -H "$LEAD"
check_status 21.16 "the drawer's outreach read answers" 200
check 21.17 "…with no run and no reply yet" "null/null" "$(json '.run')/$(json '.latestReply')"

# --- the composer without a mailbox ------------------------------------------------------

section "21.3  writing without a mailbox"

post_json "/projects/$PROJECT/outreach/conversations/$FATIMA/messages" "$(jq -nc --arg e "$FATIMA_EMAIL" \
  '{toAddress:$e, subject:"A confidential conversation", body:"Hello", replyInThread:false}')" -H "$LEAD"
check_code 21.20 "an email is refused before anything is written" 409 MAILBOX_NOT_CONNECTED

BRIEF_FILE="$RUN_DIR/brief.pdf"
MESSAGE_FILE="$RUN_DIR/message.json"
printf '%%PDF-1.7\nthe brief\n' > "$BRIEF_FILE"
jq -nc --arg e "$FATIMA_EMAIL" '{toAddress:$e, subject:"The brief", body:"Attached.", replyInThread:false}' \
  > "$MESSAGE_FILE"
http POST "/projects/$PROJECT/outreach/conversations/$FATIMA/messages" -H "$LEAD" \
  -F "message=@$MESSAGE_FILE;type=application/json" -F "files=@$BRIEF_FILE;type=application/pdf"
check_code 21.21 "…and so is one carrying a file" 409 MAILBOX_NOT_CONNECTED
check 21.22 "…which leaves no line and no file row behind" "0/0" \
  "$(sql "SELECT count(*) FROM app_lm_outreach_message WHERE candidate_id = '$FATIMA'")/$(sql "SELECT count(*)
          FROM app_lm_outreach_message_attachment a JOIN app_lm_outreach_message m ON m.id = a.message_id
          WHERE m.project_id = '$PROJECT'")"

get "/projects/$PROJECT/outreach/messages/$(new_uuid)/attachments/$(new_uuid)" -H "$LEAD"
check_status 21.23 "a file of an email that does not exist is not found" 404

# --- the client seat ---------------------------------------------------------------------

section "21.4  a client seat reads none of it"

get "/projects/$PROJECT/outreach/conversations?view=all&who=team" -H "$REP"
check_status 21.30 "the queue is refused to a client representative" 403
get "/projects/$PROJECT/outreach/conversations/$FATIMA" -H "$REP"
check_status 21.31 "…and so is her conversation" 403
get "/projects/$PROJECT/outreach/candidates/$FATIMA" -H "$REP"
check_status 21.32 "…and the drawer's outreach read" 403
post_json "/projects/$PROJECT/outreach/conversations/$FATIMA/messages" "$(jq -nc --arg e "$FATIMA_EMAIL" \
  '{toAddress:$e, subject:"Hi", body:"Hello", replyInThread:false}')" -H "$REP"
check_status 21.33 "…and writing to her" 403

summary
