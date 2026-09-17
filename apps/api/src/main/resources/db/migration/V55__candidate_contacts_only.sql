-- The ledger is the only place a contact lives.
--
-- V54 copied every row's email and phone into app_lm_candidate_contact and kept the two columns as
-- "the one value the grid shows". Then the drawer learned to edit the ledger line by line — add a
-- second address, retag one, remove a wrong number — and a second store of "the" address would drift
-- from the list the moment anyone touched it. The grid lists them all now, the importer matches a
-- person on any address they hold, and nothing else read the columns, so they go.
--
-- Nothing else references them: V36's unique indexes are on name and company, and V54 already
-- carried the values across.

ALTER TABLE app_lm_project_candidate
    DROP COLUMN email,
    DROP COLUMN phone;
