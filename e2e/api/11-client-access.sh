#!/usr/bin/env bash
# Phase 3 — the client side of the workspace: the registry, both representative onboarding paths, and
# exactly how far a pure client can reach once they are in.
#
# The client is the only role in the product that belongs to somebody outside the firm. Everything a
# pure client can see is, by definition, disclosure to a third party — so the interesting assertions
# here are not "is it 403" but "what is in the body of the 200".
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
. "$RUN_DIR/cast.env"

roles_of() {
  sql "SELECT string_agg(r.name, ',' ORDER BY r.name)
       FROM app_lm_workspace_member m
         JOIN app_lm_user u ON u.id = m.user_id
         JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
         JOIN app_lm_role r ON r.id = mr.role_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}
member_id_of() {
  sql "SELECT m.id FROM app_lm_workspace_member m JOIN app_lm_user u ON u.id = m.user_id
       WHERE u.email = '$1' AND m.status = 'ACTIVE'"
}

section "C1  the registry"

post_json /clients "$(jq -nc --arg n "$CLIENT_NAME" '{customName:$n}')" -H "$(auth_header "$MEMBER_TOKEN")"
check_code C1.1 "a duplicate client name" 409 CLIENT_ALREADY_EXISTS

get "/clients/$CLIENT_ID" -H "$(auth_header "$MEMBER_TOKEN")"
check_status C1.2 "a plain member reads the client detail" 200
check C1.3 "which lists the representative's email in full" "1" \
  "$(json "[.representatives[] | select(.email == \"$CLIENT_EMAIL\")] | length")"

section "C2  the stranger path left the right trail"

check C2.1 "the invitation carried the client id" "1" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$CLIENT_EMAIL' AND client_id IS NOT NULL")"
check C2.2 "and the CLIENT role" "CLIENT" \
  "$(sql "SELECT r.name FROM app_lm_invitation i JOIN app_lm_role r ON r.id = i.role_id
          WHERE i.email = '$CLIENT_EMAIL'")"
check C2.3 "the portal invitation was the mail that went out" "true" \
  "$(test "$(email_count "portal on LightMove")" -ge 1 && echo true || echo false)"
check C2.4 "the representative row is ACTIVE and bound to a user" "1" \
  "$(sql "SELECT count(*) FROM app_lm_client_representative
          WHERE lower(email) = '$CLIENT_EMAIL' AND status = 'ACTIVE' AND user_id IS NOT NULL")"
check C2.5 "they hold exactly the CLIENT workspace role" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"
check C2.6 "and a CLIENT seat on the one mandate" "CLIENT" \
  "$(sql "SELECT r.name FROM app_lm_project_member p
            JOIN app_lm_project_member_role pr ON pr.project_member_id = p.id
            JOIN app_lm_role r ON r.id = pr.role_id
            JOIN app_lm_workspace_member m ON m.id = p.member_id
            JOIN app_lm_user u ON u.id = m.user_id
          WHERE u.email = '$CLIENT_EMAIL'")"

section "C3  the existing-member path does not send an invitation"

check C3.1 "no invitation row was minted for the staff member" "0" \
  "$(sql "SELECT count(*) FROM app_lm_invitation WHERE email = '$DUAL_EMAIL' AND client_id IS NOT NULL")"
check C3.2 "they were told informationally instead" "true" "$(test "$(email_count "You now represent")" -ge 1 && echo true || echo false)"
check C3.3 "and gained CLIENT alongside their staff role" "CLIENT,MEMBER" "$(roles_of "$DUAL_EMAIL")"

# The untested half: a dual-role member must keep every staff surface. The grid proved the endpoints;
# this proves the DB predicate behind them still calls them staff.
get /members -H "$(auth_header "$DUAL_TOKEN")"
check_status C3.4 "they still read the roster" 200
get /clients -H "$(auth_header "$DUAL_TOKEN")"
check_status C3.5 "and the client registry" 200
get /workspace -H "$(auth_header "$DUAL_TOKEN")"
check_status C3.6 "and workspace settings" 200
post_json /projects "$(jq -nc --arg c "$CLIENT_ID" '{clientId:$c, positionTitle:"Dual Created"}')" \
  -H "$(auth_header "$DUAL_TOKEN")"
check_status C3.7 "and can still create a mandate" 201
DUAL_PROJECT_ID=$(json '.id')

section "C4  how far the pure client reaches"

get "/projects/$PROJECT_ID/position" -H "$(auth_header "$CLIENT_TOKEN")"
check_status C4.1 "the brief of the mandate they are attached to" 200
get "/projects/$PROJECT_ID/strategy" -H "$(auth_header "$CLIENT_TOKEN")"
check_status C4.2 "its strategy" 200
get "/projects/$PROJECT_ID/sourcing" -H "$(auth_header "$CLIENT_TOKEN")"
check_status C4.3 "its sourcing" 200

get "/projects/$OTHER_PROJECT_ID/position" -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.4 "a mandate they are NOT attached to" 403 FORBIDDEN
get "/projects/$OTHER_PROJECT_ID/strategy" -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.5 "on either surface" 403 FORBIDDEN
get "/projects/$DUAL_PROJECT_ID/sourcing" -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.6 "nor a mandate created after they joined" 403 FORBIDDEN

http PATCH "/projects/$PROJECT_ID" -H 'Content-Type: application/json' -d '{"targetDate":"2027-01-01"}' \
  -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.7 "they cannot edit the mandate they can read" 403 FORBIDDEN
# A well-formed payload, deliberately: Bean Validation runs during argument resolution, before method
# security, so a malformed body answers 400 and never reaches the gate this case is about.
http PUT "/projects/$PROJECT_ID/strategy/sectors" -H 'Content-Type: application/json' \
  -d '{"direct":[],"adjacent":[],"inferred":[]}' -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.8 "nor write its strategy" 403 FORBIDDEN
http PUT "/projects/$PROJECT_ID/members/$(member_id_of "$MEMBER_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"LEAD"}' -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.9 "nor touch its team" 403 FORBIDDEN
http POST "/projects/$PROJECT_ID/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" -H "$(auth_header "$CLIENT_TOKEN")"
check_code C4.10 "nor attach another representative" 403 FORBIDDEN

section "C5  what the client's own project row discloses"

get /projects -H "$(auth_header "$CLIENT_TOKEN")"
check C5.1 "one mandate only" "1" "$(json 'length')"
CLIENT_ROW=$(printf '%s' "$LAST_BODY" | jq '.[0]')
check C5.2 "carrying no representative of any other client" "0" \
  "$(printf '%s' "$CLIENT_ROW" | jq "[.representatives[]? | select(.email != \"$CLIENT_EMAIL\")] | length")"
note C5.3 "the row exposes team members: $(printf '%s' "$CLIENT_ROW" | jq -r '[.team[]?.fullName] | join(", ")')"
note C5.4 "and these fields: $(printf '%s' "$CLIENT_ROW" | jq -r 'keys | join(", ")')"
get /auth/me -H "$(auth_header "$CLIENT_TOKEN")"
check C5.5 "/me names the firm's workspace to the outside client" "$WORKSPACE_NAME" "$(json '.workspace.name')"
check C5.6 "and reports their role honestly" "CLIENT" "$(json '.workspace.roles[0]')"

# The brand is theirs to see; emailDomain is not. It describes the firm's own colleagues' addresses
# and a hiring-company contact has no use for it.
check C5.7 "but withholds the firm's email domain" "null" "$(json '.workspace.emailDomain')"
check C5.8 "while keeping the brand they are dealing with" "true" \
  "$(test -n "$(json '.workspace.slug')" -a -n "$(json '.workspace.logoMark')" && echo true || echo false)"

get /auth/me -H "$(auth_header "$MEMBER_TOKEN")"
check C5.9 "a staff member still receives it" "${CLIENT_EMAIL#*@}" "$(json '.workspace.emailDomain')"
get /auth/me -H "$(auth_header "$DUAL_TOKEN")"
check C5.10 "and so does a member who also holds CLIENT — staff is staff" "${CLIENT_EMAIL#*@}" \
  "$(json '.workspace.emailDomain')"

section "C6  attacking the seam"

# A client-portal invitation is a CLIENT-role invitation. Redeeming it through the staff accept
# endpoint would be an upgrade, so requirePendingInvitation excludes anything with a client_id.
STAFF_ONLY_EMAIL=$(new_email portal)
post_json "/clients/$CLIENT_ID/representatives" \
  "$(jq -nc --arg e "$STAFF_ONLY_EMAIL" '{fullName:"Pat Portal", position:"CFO", email:$e}')" \
  -H "$(auth_header "$ADMIN_TOKEN")" >/dev/null
PORTAL_TOKEN_VALUE=$(token_for "$STAFF_ONLY_EMAIL" accept-invite)

# Verified, so the attempt fails on the invitation's own guard rather than on the email gate in front
# of it — otherwise the case proves nothing about portal tokens.
BYSTANDER_EMAIL=$(new_email bystander)
post_json /auth/signup "$(jq -nc --arg e "$BYSTANDER_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"By Stander", email:$e, password:$p, termsAccepted:true}')" >/dev/null
http POST "/auth/verify?token=$(token_for "$BYSTANDER_EMAIL" verify)" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$BYSTANDER_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
BYSTANDER_TOKEN=$(json '.accessToken')

post_json /onboarding/invitations/accept "$(jq -nc --arg t "$PORTAL_TOKEN_VALUE" '{token:$t}')" \
  -H "$(auth_header "$BYSTANDER_TOKEN")"
check_code C6.1 "somebody else's portal token, redeemed as a staff invitation" 400 INVITATION_INVALID

# The sharper case: the invited person redeeming their OWN portal token through the staff endpoint.
# The token is addressed to them, so the email binding cannot refuse it — only the client_id check
# stands between a read-only portal contact and a staff seat.
post_json /auth/signup "$(jq -nc --arg e "$STAFF_ONLY_EMAIL" --arg p "$PASSWORD" \
  '{fullName:"Pat Portal", email:$e, password:$p, termsAccepted:true}')" >/dev/null
http POST "/auth/verify?token=$(token_for "$STAFF_ONLY_EMAIL" verify)" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$STAFF_ONLY_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
PORTAL_SELF_TOKEN=$(json '.accessToken')

# It is accepted — the two endpoints converge on the same redeem() — and that is fine, because the
# role comes from the invitation row, not from the endpoint. The property worth pinning is that no
# staff role is granted by taking the staff door.
post_json /onboarding/invitations/accept "$(jq -nc --arg t "$PORTAL_TOKEN_VALUE" '{token:$t}')" \
  -H "$(auth_header "$PORTAL_SELF_TOKEN")"
note C6.1b "their own portal token through the staff endpoint answered $LAST_STATUS $(ecode)"
check C6.2b "and grants CLIENT only — the endpoint does not decide the role" "CLIENT" \
  "$(roles_of "$STAFF_ONLY_EMAIL")"
# On a token reissued after the accept, so the refusal is the staff fence and not the missing wsId
# claim on the older one.
post_json /auth/login "$(jq -nc --arg e "$STAFF_ONLY_EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" >/dev/null
PORTAL_SELF_TOKEN=$(json '.accessToken')
get /members -H "$(auth_header "$PORTAL_SELF_TOKEN")"
check_code C6.2b2 "no staff surface follows from it" 403 FORBIDDEN
check C6.2b3 "the representative row was activated by that accept, as the portal path would" "1" \
  "$(sql "SELECT count(*) FROM app_lm_client_representative
          WHERE lower(email) = '$STAFF_ONLY_EMAIL' AND user_id IS NOT NULL")"

# The token-less path is the other way in: /me carries pendingInvitation and a signed-in user redeems
# it without a token. A portal invitation must not surface there either.
get /auth/me -H "$(auth_header "$PORTAL_SELF_TOKEN")"
check C6.2c "a portal invitation is not offered on /me as a pending staff invitation" "null" \
  "$(json '.pendingInvitation')"
http POST /onboarding/accept-invitation -H "$(auth_header "$PORTAL_SELF_TOKEN")"
check C6.2d "and the token-less accept finds nothing to redeem" "true" \
  "$(test "$LAST_STATUS" != "200" && echo true || echo false)"
note C6.2e "token-less accept answered $LAST_STATUS $(ecode)"

# A representative belongs to one client. Attaching them to another client's mandate would hand a
# third party a competitor's search.
http POST "/projects/$OTHER_PROJECT_ID/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" -H "$(auth_header "$ADMIN_TOKEN")"
check_code C6.3 "a representative of one client attached to another's mandate" 400 VALIDATION_FAILED

# The staff team table must not be a back door to seating a client.
http PUT "/projects/$PROJECT_ID/members/$(member_id_of "$CLIENT_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"RESEARCHER"}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code C6.4 "a pure client named as a staff seat" 403 FORBIDDEN

http PUT "/projects/$PROJECT_ID/members/$(member_id_of "$MEMBER_EMAIL")" \
  -H 'Content-Type: application/json' -d '{"role":"CLIENT"}' -H "$(auth_header "$ADMIN_TOKEN")"
check_code C6.5 "CLIENT requested through the staff team table" 400 VALIDATION_FAILED

# A client already belongs to the workspace, so a staff invitation cannot re-home them.
post_json /invitations "$(jq -nc --arg e "$CLIENT_EMAIL" '[{email:$e, role:"MEMBER"}]')" \
  -H "$(auth_header "$ADMIN_TOKEN")"
note C6.6 "inviting an existing pure client as staff -> $LAST_STATUS, sent=$(json '.sent')"
check C6.7 "and they are still only a client" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"

# A revoked representative must not be re-seatable.
sql_run "UPDATE app_lm_client_representative SET status = 'REVOKED' WHERE lower(email) = '$STAFF_ONLY_EMAIL'"
REVOKED_ID=$(sql "SELECT id FROM app_lm_client_representative WHERE lower(email) = '$STAFF_ONLY_EMAIL'")
http POST "/projects/$PROJECT_ID/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$REVOKED_ID" '{representativeId:$r}')" -H "$(auth_header "$ADMIN_TOKEN")"
check_code C6.8 "a revoked representative re-attached" 400 VALIDATION_FAILED

section "C7  detaching a representative"

http DELETE "/projects/$PROJECT_ID/representatives/$REPRESENTATIVE_ID" -H "$(auth_header "$ADMIN_TOKEN")"
check_status C7.1 "the admin detaches them" 200
get /projects -H "$(auth_header "$CLIENT_TOKEN")"
check C7.2 "the client's project list empties at once" "0" "$(json 'length')"
get "/projects/$PROJECT_ID/position" -H "$(auth_header "$CLIENT_TOKEN")"
check_code C7.3 "and the brief they could read a moment ago is closed" 403 FORBIDDEN
check C7.4 "they remain a workspace member, just seated nowhere" "CLIENT" "$(roles_of "$CLIENT_EMAIL")"

# Put it back, so the SPA phase still has a client with something to look at.
http POST "/projects/$PROJECT_ID/representatives" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg r "$REPRESENTATIVE_ID" '{representativeId:$r}')" -H "$(auth_header "$ADMIN_TOKEN")"
check_status C7.5 "re-attaching restores it" 200

summary
