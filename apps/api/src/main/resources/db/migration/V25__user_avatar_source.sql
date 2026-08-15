-- Which identity supplied app_lm_user.avatar_url: an uppercased OAuth registration id today, and
-- 'USER' once the profile screen lets someone upload their own.
--
-- Without it the last provider used always won, so signing in with an account that has no photo
-- replaced a real one with a generated monogram. A provider may now fill an empty avatar, and
-- refresh the one it supplied — LinkedIn's URLs expire within weeks — but never replace another's.
--
-- NULL means "supplied before this column existed": the next sign-in claims it, once.

ALTER TABLE app_lm_user ADD COLUMN avatar_source varchar(32);
