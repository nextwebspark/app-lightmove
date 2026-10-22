-- Notetaker 7 (#956): a bot on Cloud Run is no longer started with its run token in the execution's environment, where
-- anyone holding run.executions.get could read it while the call lasts. It exchanges its own service account's Google
-- ID token for the token on its first call, once: run_token_issued_at says the exchange happened, and a second one is
-- refused, so an ID token caught in flight cannot mint a second credential for the run.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN run_token_issued_at timestamptz;
