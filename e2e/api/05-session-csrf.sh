#!/usr/bin/env bash
# Phase 2d — session mechanics: CSRF double-submit, refresh rotation and theft detection, JWT
# integrity, and the verified-email gate. This is the least test-covered part of the auth surface:
# the Java suite exercises rotation and reuse, but nothing exercises CSRF enforcement or logout.
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

b64url() { base64 | tr '+/' '-_' | tr -d '=\n'; }

USER=$(new_email sess)
post_json /auth/signup "$(jq -nc --arg e "$USER" --arg p "$PASSWORD" \
  '{fullName:"Sess Ion", email:$e, password:$p, termsAccepted:true}')" -c "$(jar sess)" >/dev/null
http POST "/auth/verify?token=$(token_for "$USER" verify)" >/dev/null
post_json /auth/login "$(jq -nc --arg e "$USER" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar sess)"
ACCESS=$(json '.accessToken')

section "N21  CSRF double-submit"

get /auth/csrf -c "$(jar csrf)"
check_status N21.1 "GET /auth/csrf" 204
check_contains N21.2 "it writes the XSRF-TOKEN cookie (the lazy-token trap)" "XSRF-TOKEN" "$(header 'set-cookie')"
CSRF_TOKEN=$(awk '/XSRF-TOKEN/{print $7}' "$(jar csrf)" | tail -1)
check N21.3 "the cookie is readable by JavaScript (not HttpOnly)" "false" \
  "$(printf '%s' "$(header 'set-cookie')" | grep -qi httponly && echo true || echo false)"

REFRESH=$(refresh_cookie sess)
http POST /auth/refresh -b "lm_refresh=$REFRESH"
check_code N21.4 "refresh with no CSRF header at all" 403 CSRF_TOKEN_INVALID

http POST /auth/refresh -b "lm_refresh=$REFRESH; XSRF-TOKEN=$CSRF_TOKEN" -H "X-XSRF-TOKEN: not-the-token"
check_code N21.5 "refresh with a header that does not match the cookie" 403 CSRF_TOKEN_INVALID

http POST /auth/refresh -b "lm_refresh=$REFRESH" -H "X-XSRF-TOKEN: $CSRF_TOKEN"
check_code N21.6 "refresh with the header but no cookie" 403 CSRF_TOKEN_INVALID

http POST /auth/logout -b "lm_refresh=$REFRESH"
check_code N21.7 "logout with no CSRF header" 403 CSRF_TOKEN_INVALID

# The exempt list: these must NOT require the header, or the SPA cannot sign anybody in.
post_json /auth/login "$(jq -nc --arg e "$USER" --arg p "wrong" '{email:$e, password:$p}')"
check_code N21.8 "login is CSRF-exempt" 401 INVALID_CREDENTIALS
http POST /auth/verify/resend -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$USER" '{email:$e}')"
check_status N21.9 "resend is CSRF-exempt" 202

check N21.10 "the refresh cookie survived every rejected attempt" "$REFRESH" "$(refresh_cookie sess)"

section "N22  refresh rotation and theft detection"

XSRF=$(csrf_value sess)
http POST /auth/refresh -b "lm_refresh=$REFRESH; XSRF-TOKEN=$XSRF" -c "$(jar sess)" -H "X-XSRF-TOKEN: $XSRF"
check_status N22.1 "rotate once" 200
ROTATED=$(refresh_cookie sess)

# The load-bearing half of the logout fix: a ROTATED token replayed IS the attack signature, because
# the legitimate client has already moved on to its successor. Narrowing "revoked means theft" must
# never have widened into "no revoked token is theft".
XSRF=$(csrf_value sess)
http POST /auth/refresh -b "lm_refresh=$REFRESH; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_code N22.2 "replaying the superseded token is still caught as theft" 401 REFRESH_TOKEN_REUSED

XSRF=$(csrf_value sess)
http POST /auth/refresh -b "lm_refresh=$ROTATED; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_status N22.3 "the successor is revoked too — the whole family dies" 401
note N22.4 "the successor answers $(ecode)"

check N22.5 "the reuse is audited" "true" \
  "$(test "$(sql "SELECT count(*) FROM app_lm_audit_event a JOIN app_lm_user u ON u.id = a.actor_user_id
                  WHERE u.email = '$USER' AND a.event_type = 'TOKEN_REUSE_DETECTED'")" -ge 1 && echo true || echo false)"

# Scoped to the compromised family: the token minted at signup belongs to a different family and is
# expected to survive, which is the point of keying revocation by family rather than by user.
check N22.6 "every token in the compromised family is revoked" "0" \
  "$(sql "SELECT count(*) FROM app_lm_refresh_token r JOIN app_lm_user u ON u.id = r.user_id
          WHERE u.email = '$USER' AND r.revoked_at IS NULL
            AND r.family_id = (SELECT family_id FROM app_lm_refresh_token r2 JOIN app_lm_user u2 ON u2.id = r2.user_id
                               WHERE u2.email = '$USER' AND r2.revoked_reason = 'REUSE_DETECTED' LIMIT 1)")"
check N22.6b "a session from a different family survives the revocation" "true" \
  "$(test "$(sql "SELECT count(*) FROM app_lm_refresh_token r JOIN app_lm_user u ON u.id = r.user_id
                  WHERE u.email = '$USER' AND r.revoked_at IS NULL")" -ge 1 && echo true || echo false)"

XSRF=$(csrf_value nocookie)
http POST /auth/refresh -b "XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_code N22.7 "refresh with no refresh cookie" 401 REFRESH_TOKEN_INVALID

XSRF=$(csrf_value garbage)
http POST /auth/refresh -b "lm_refresh=totally-made-up; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_code N22.8 "refresh with a garbage cookie" 401 REFRESH_TOKEN_INVALID
check_contains N22.9 "a rejected refresh still clears the cookie" "Max-Age=0" "$(header 'set-cookie')"

section "N23  a second user's refresh token"

OTHER=$(new_email other)
post_json /auth/signup "$(jq -nc --arg e "$OTHER" --arg p "$PASSWORD" \
  '{fullName:"Otto Other", email:$e, password:$p, termsAccepted:true}')" -c "$(jar other)" >/dev/null
OTHER_REFRESH=$(refresh_cookie other)

XSRF=$(csrf_value cross)
http POST /auth/refresh -b "lm_refresh=$OTHER_REFRESH; XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_status N23.1 "another user's live token refreshes into THEIR session, not ours" 200
check N23.2 "the returned identity is the cookie's owner" "$OTHER" "$(json '.user.email')"
note N23.3 "the refresh cookie alone is the whole session credential — no binding to device or IP"

section "N24  JWT integrity"

get /auth/me
check_status N24.1 "/auth/me with no Authorization header" 401

get /auth/me -H "Authorization: Bearer not.a.jwt"
check_status N24.2 "/auth/me with a garbage bearer token" 401

# Flip emailVerified to true and keep the original signature.
HEADER_SEG=$(printf '%s' "$ACCESS" | cut -d. -f1)
SIG_SEG=$(printf '%s' "$ACCESS" | cut -d. -f3)
TAMPERED_PAYLOAD=$(jwt_claims "$ACCESS" | jq -c '.emailVerified = true' | b64url)
get /auth/me -H "Authorization: Bearer $HEADER_SEG.$TAMPERED_PAYLOAD.$SIG_SEG"
check_status N24.3 "a payload edited under the original signature" 401

# alg:none, no signature.
NONE_HEADER=$(printf '{"alg":"none","typ":"JWT"}' | b64url)
NONE_PAYLOAD=$(jwt_claims "$ACCESS" | jq -c '.emailVerified = true' | b64url)
get /auth/me -H "Authorization: Bearer $NONE_HEADER.$NONE_PAYLOAD."
check_status N24.4 "an alg:none token" 401

# A far-future expiry, still signed with the original signature.
FUTURE_PAYLOAD=$(jwt_claims "$ACCESS" | jq -c '.exp = 9999999999' | b64url)
get /auth/me -H "Authorization: Bearer $HEADER_SEG.$FUTURE_PAYLOAD.$SIG_SEG"
check_status N24.5 "an extended expiry under the original signature" 401

# A forged wsId would be cross-tenant access if the claim were trusted.
FORGED_WS=$(jwt_claims "$ACCESS" | jq -c '.wsId = "00000000-0000-0000-0000-000000000001"' | b64url)
get /auth/me -H "Authorization: Bearer $HEADER_SEG.$FORGED_WS.$SIG_SEG"
check_status N24.6 "a forged workspace claim" 401

section "N25  the verified-email gate"

UNVERIFIED=$(new_email unver)
post_json /auth/signup "$(jq -nc --arg e "$UNVERIFIED" --arg p "$PASSWORD" \
  '{fullName:"Uma Unverified", email:$e, password:$p, termsAccepted:true}')" >/dev/null
UNVERIFIED_TOKEN=$(json '.accessToken')

get /auth/me -H "$(auth_header "$UNVERIFIED_TOKEN")"
check_status N25.1 "/auth/me is reachable while unverified (by design)" 200

get /workspaces/current -H "$(auth_header "$UNVERIFIED_TOKEN")"
check N25.2 "a tenant route is refused while unverified" "true" \
  "$(test "$LAST_STATUS" = "403" -o "$LAST_STATUS" = "404" && echo true || echo false)"
note N25.3 "tenant route while unverified -> $LAST_STATUS $(ecode)"

get /projects -H "$(auth_header "$UNVERIFIED_TOKEN")"
note N25.4 "GET /projects while unverified -> $LAST_STATUS $(ecode)"

# A workspace-less but verified user still has no tenant claim.
get /projects -H "$(auth_header "$ACCESS")"
note N25.5 "GET /projects as a verified user with no workspace -> $LAST_STATUS $(ecode)"

section "N26  logout"

post_json /auth/login "$(jq -nc --arg e "$USER" --arg p "$PASSWORD" '{email:$e, password:$p}')" -c "$(jar out)" >/dev/null
XSRF=$(csrf_value out)
http POST /auth/logout -b "$(jar out)" -c "$(jar out)" -H "X-XSRF-TOKEN: $XSRF"
check_status N26.1 "logout" 204

XSRF=$(csrf_value out)
http POST /auth/logout -b "$(jar out)" -c "$(jar out)" -H "X-XSRF-TOKEN: $XSRF"
check_status N26.2 "logout a second time" 204

XSRF=$(csrf_value out2)
http POST /auth/logout -b "XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF"
check_status N26.3 "logout with no refresh cookie at all" 204

# Logout revokes the refresh token; the access token it was paired with is not revocable.
get /auth/me -H "$(auth_header "$ACCESS")"
if [ "$LAST_STATUS" = "200" ]; then
  note N26.4 "the access token still works after logout ($LAST_STATUS) — stateless, valid up to 15 minutes"
else
  pass N26.4 "the access token stops working after logout"
fi

summary
