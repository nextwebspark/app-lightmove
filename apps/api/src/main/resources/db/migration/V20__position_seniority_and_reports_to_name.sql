-- Two Position screen fields the mockup has and the original V7 table didn't: the seniority tier
-- (C-suite/N-1/N-2/N-3) and the name of the person the role reports to (reports_to already held only
-- their title). No CHECK on seniority — following V10's move for employment_type, the value set is
-- owned by the Java Seniority enum (@Enumerated(STRING)) alone.

ALTER TABLE app_lm_position ADD COLUMN seniority varchar(16);
ALTER TABLE app_lm_position ADD COLUMN reports_to_name varchar(160);
