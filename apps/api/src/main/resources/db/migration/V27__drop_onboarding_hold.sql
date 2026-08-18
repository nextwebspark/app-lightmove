-- The signup wizard no longer has anything to hold.
--
-- app_lm_pending_onboarding existed because verification came last: an unverified user could fill in
-- the organisation and invite steps, and nothing they asked for was allowed to exist until they
-- clicked the link in their inbox — so their answers were parked here and materialised at
-- verification. The wizard now asks for that link at step 2, and SecurityConfig refuses
-- /onboarding/** to an unverified session, so the row can never be written.

DROP TABLE app_lm_pending_onboarding;
