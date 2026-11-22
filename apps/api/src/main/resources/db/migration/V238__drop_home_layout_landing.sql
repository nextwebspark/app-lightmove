-- The contract half of V237: every revision now reads the start page from app_lm_start_page, so V233's landing
-- column (and its CHECK, app_lm_home_layout_landing_chk) goes.
ALTER TABLE app_lm_home_layout DROP COLUMN landing;
