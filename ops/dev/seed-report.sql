-- A mandate's worth of demo data for the Reports tab, for the LOCAL database only: `npm run dev:db:seed-report`.
--
-- A fresh local mandate is a day old and holds a handful of captures, so the report's four chapters
-- draw almost nothing. This files 42 companies and 116 fictional executives — levels, countries, the
-- nine nationality groups, gender, statuses, disclosed packages — spread over eight weeks, and moves
-- the mandate's kickoff back so the progress chapter has a curve to draw.
--
-- Re-runnable: every row it writes has an id derived from the project's, so a second run replaces the
-- first. Deterministic: index arithmetic, never random(), so two developers see the same report.
-- Everyone in it is invented; the employers are real names the mockups already use.
\set ON_ERROR_STOP on
\if :{?project}
\else
  \set project ''
\endif

SELECT p.id AS project_id,
       p.created_by AS seeded_by,
       p.position_title AS mandate,
       EXISTS (SELECT 1 FROM app_lm_project_candidate c
               WHERE c.id = md5('seed-report:' || p.id || ':executive:1')::uuid) AS was_seeded
FROM app_lm_project p
WHERE :'project' = '' OR p.id = NULLIF(:'project', '')::uuid
ORDER BY 4 DESC, p.created_at DESC
LIMIT 1
\gset

\if :{?project_id}
\else
  \echo 'seed-report: no mandate to seed — create one in the app first'
  \quit
\endif

-- The Sunday on or before seven weeks ago, so week 0 starts a GCC working week and today is in week 7.
SELECT (current_date - 49 - extract(dow FROM current_date - 49)::int) AS kickoff \gset
SELECT COALESCE((SELECT currency FROM app_lm_position WHERE project_id = :'project_id'), 'USD') AS brief_currency \gset

BEGIN;

DELETE FROM app_lm_project_candidate
WHERE project_id = :'project_id'
  AND id IN (SELECT md5('seed-report:' || :'project_id' || ':executive:' || n)::uuid FROM generate_series(1, 200) n);
DELETE FROM app_lm_project_triage_company
WHERE project_id = :'project_id'
  AND id IN (SELECT md5('seed-report:' || :'project_id' || ':company:' || n)::uuid FROM generate_series(1, 200) n);

UPDATE app_lm_project
SET created_at = CASE WHEN :'was_seeded'::boolean OR created_at > :'kickoff'::date
                      THEN :'kickoff'::date + time '06:00' ELSE created_at END,
    target_date = :'kickoff'::date + 42
WHERE id = :'project_id';

UPDATE app_lm_position
SET salary_min = 560000,
    salary_max = 720000,
    bonus_value = COALESCE(bonus_value, 40),
    bonus_basis = COALESCE(bonus_basis, 'PERCENT_OF_BASE')
WHERE project_id = :'project_id' AND salary_min IS NULL AND salary_max IS NULL;

CREATE TEMP TABLE seed_company ON COMMIT DROP AS
SELECT md5('seed-report:' || :'project_id' || ':company:' || n)::uuid AS id, *
FROM (VALUES
    (1,  'Almarai',                 'consumer goods',   'Saudi Arabia',         'Riyadh',      'SHORTLISTED', true),
    (2,  'Agthia Group',            'consumer goods',   'United Arab Emirates', 'Abu Dhabi',   'SHORTLISTED', true),
    (3,  'IFFCO',                   'consumer goods',   'United Arab Emirates', 'Sharjah',     'SHORTLISTED', true),
    (4,  'Unilever Gulf',           'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (5,  'PepsiCo AMEA',            'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (6,  'Nestlé Middle East',      'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (7,  'Mondelez MENA',           'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (8,  'Kraft Heinz MENA',        'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (9,  'Coca-Cola MENA',          'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (10, 'Savola Group',            'consumer goods',   'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', true),
    (11, 'Mars GCC',                'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', false),
    (12, 'Procter & Gamble Arabia', 'consumer goods',   'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', false),
    (13, 'Reckitt MENA',            'consumer goods',   'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', false),
    (14, 'Binzagr Company',         'consumer goods',   'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', false),
    (15, 'Savola Foods',            'food & beverages', 'Saudi Arabia',         'Jeddah',      'SHORTLISTED', true),
    (16, 'NADEC',                   'food & beverages', 'Saudi Arabia',         'Riyadh',      'IN_UNIVERSE', true),
    (17, 'Halwani Bros',            'food & beverages', 'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', true),
    (18, 'Al Ain Farms',            'food & beverages', 'United Arab Emirates', 'Al Ain',      'IN_UNIVERSE', true),
    (19, 'Americana Foods',         'food & beverages', 'Kuwait',               'Kuwait City', 'IN_UNIVERSE', true),
    (20, 'Bel Group MENA',          'food & beverages', 'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (21, 'Juhayna',                 'food & beverages', 'Egypt',                'Cairo',       'IN_UNIVERSE', true),
    (22, 'Sunbulah Group',          'food & beverages', 'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', false),
    (23, 'Al Islami Foods',         'food & beverages', 'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', false),
    (24, 'Panda Retail',            'retail',           'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', true),
    (25, 'LuLu Group',              'retail',           'United Arab Emirates', 'Abu Dhabi',   'IN_UNIVERSE', true),
    (26, 'Carrefour MAF',           'retail',           'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (27, 'BinDawood Holding',       'retail',           'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', true),
    (28, 'Sultan Center',           'retail',           'Kuwait',               'Kuwait City', 'IN_UNIVERSE', true),
    (29, 'Spinneys',                'retail',           'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', false),
    (30, 'Danube',                  'retail',           'Saudi Arabia',         'Jeddah',      'IN_UNIVERSE', false),
    (31, 'Al Dahra',                'farming',          'United Arab Emirates', 'Abu Dhabi',   'IN_UNIVERSE', true),
    (32, 'Tanmiah Food',            'farming',          'Saudi Arabia',         'Riyadh',      'IN_UNIVERSE', true),
    (33, 'Al Watania Poultry',      'farming',          'Saudi Arabia',         'Buraydah',    'IN_UNIVERSE', true),
    (34, 'Elite Agro',              'farming',          'United Arab Emirates', 'Abu Dhabi',   'IN_UNIVERSE', true),
    (35, 'Jenaan',                  'farming',          'United Arab Emirates', 'Abu Dhabi',   'IN_UNIVERSE', false),
    (36, 'ARASCO',                  'farming',          'Saudi Arabia',         'Riyadh',      'IN_UNIVERSE', false),
    (37, 'Kudu',                    'restaurants',      'Saudi Arabia',         'Riyadh',      'IN_UNIVERSE', true),
    (38, 'Herfy',                   'restaurants',      'Saudi Arabia',         'Riyadh',      'IN_UNIVERSE', true),
    (39, 'Kout Food Group',         'restaurants',      'Kuwait',               'Kuwait City', 'IN_UNIVERSE', true),
    (40, 'Alshaya Food',            'restaurants',      'Kuwait',               'Kuwait City', 'IN_UNIVERSE', false),
    (41, 'Al Rawabi Dairy',         'dairy',            'United Arab Emirates', 'Dubai',       'IN_UNIVERSE', true),
    (42, 'Baladna',                 'dairy',            'Qatar',                'Doha',        'IN_UNIVERSE', true)
) AS company(n, name, industry, country, city, status, staffed);

-- A company the mandate already holds under the same name keeps its own row; the people below are
-- mapped to whichever row carries the name.
INSERT INTO app_lm_project_triage_company
    (id, project_id, status, company_name, industry, company_country, company_city, source, added_by, created_at, updated_at)
SELECT c.id, :'project_id', c.status, c.name, c.industry, c.country, c.city, 'MANUAL', :'seeded_by',
       :'kickoff'::date + (c.n % 10) + time '07:00', :'kickoff'::date + (c.n % 10) + time '07:00'
FROM seed_company c
WHERE NOT EXISTS (SELECT 1 FROM app_lm_project_triage_company held
                  WHERE held.project_id = :'project_id' AND lower(held.company_name) = lower(c.name));

CREATE TEMP TABLE seed_package ON COMMIT DROP AS
SELECT * FROM (VALUES
    (1,  true,   380000,  70000, 120000,  50000, 'INTERESTED',     'Smaller-company budget, comp was not a constraint.'),
    (2,  true,   420000,  80000, 150000,  50000, 'INTERESTED',     'Within band.'),
    (3,  true,   470000,  80000, 170000,  60000, 'INTERESTED',     'At our floor.'),
    (4,  true,   500000,  80000, 200000,  70000, 'ENGAGED',        'Within band, reference checks underway.'),
    (5,  true,   540000,  85000, 215000,  80000, 'ENGAGED',        'Within band.'),
    (6,  true,   590000,  95000, 215000,  80000, 'ENGAGED',        'Within band, second interview scheduled.'),
    (7,  true,   620000,  95000, 260000, 125000, 'ENGAGED',        'Right at our package ceiling.'),
    (8,  true,   670000, 100000, 250000, 110000, 'NOT_INTERESTED', 'Cited compensation as the primary reason.'),
    (9,  true,   590000,  85000, 300000, 185000, 'NOT_INTERESTED', 'Priced above our package band, despite a fixed ask close to ours.'),
    (10, true,   580000,  85000, 320000, 205000, 'ENGAGED',        'Still engaged despite the package gap — fixed expectations are within reach.'),
    (11, true,   585000,  85000, 330000, 220000, 'NOT_INTERESTED', 'The gap is almost entirely in bonus and LTI, not base.'),
    (12, true,   610000,  90000, 330000, 220000, 'NOT_INTERESTED', 'Declined on total package.'),
    (13, true,   680000, 100000, 320000, 200000, 'OUT_OF_SCOPE',   'Accepted a competing offer.'),
    (14, true,   818000, 120000, 250000, 152000, 'NOT_INTERESTED', 'Significantly over budget on both measures.'),
    (15, true,   700000, 100000, 340000, 240000, 'NOT_INTERESTED', 'Outside a realistic range for this band.'),
    (16, true,   698000, 100000, 380000, 272000, 'OUT_OF_SCOPE',   'Comp expectations disclosed upfront.'),
    (17, true,   440000,  80000, 160000,  60000, 'CONTACTED',      'Open to a conversation after year-end.'),
    (18, true,   610000,  90000, 240000, 100000, 'INTERESTED',     'Inside the band on both measures.'),
    (19, false, 1900000, 300000, 700000, 250000, 'ENGAGED',        'Quoted in another currency; not converted.'),
    (20, false, 2300000, 360000, 900000, 400000, 'CONTACTED',      'Quoted in another currency; not converted.')
) AS package(rank, in_brief_currency, base, allowances, bonus, lti, status, note);

CREATE TEMP TABLE seed_executive ON COMMIT DROP AS
WITH cell(ord, industry, lvl, headcount) AS (VALUES
    (1,  'consumer goods',   'BOARD',     3), (2,  'consumer goods',   'C_SUITE', 16),
    (3,  'consumer goods',   'N_MINUS_1', 13), (4,  'consumer goods',   'N_MINUS_2', 11),
    (5,  'food & beverages', 'C_SUITE',   12), (6,  'food & beverages', 'N_MINUS_1', 8),
    (7,  'food & beverages', 'N_MINUS_2', 6),  (8,  'retail',           'C_SUITE',   8),
    (9,  'retail',           'N_MINUS_1', 6),  (10, 'retail',           'N_MINUS_2', 5),
    (11, 'farming',          'BOARD',     2),  (12, 'farming',          'C_SUITE',   7),
    (13, 'farming',          'N_MINUS_1', 5),  (14, 'restaurants',      'C_SUITE',   5),
    (15, 'restaurants',      'N_MINUS_1', 4),  (16, 'dairy',            'C_SUITE',   3),
    (17, 'dairy',            'N_MINUS_1', 2)
),
seat AS (
    SELECT (row_number() OVER (ORDER BY cell.ord, member))::int AS n,
           row_number() OVER (PARTITION BY cell.industry ORDER BY cell.ord, member) AS seat_in_sector,
           cell.industry, cell.lvl
    FROM cell, generate_series(1, cell.headcount) member
),
employer AS (
    SELECT c.*, row_number() OVER (PARTITION BY c.industry ORDER BY c.n) - 1 AS slot,
           count(*) OVER (PARTITION BY c.industry) AS slots
    FROM seed_company c
    WHERE c.staffed
),
-- 37, 53, 71, 89 and 97 are coprime with 116, so each product walks all 116 seats in a different
-- order: five independent shuffles with no random().
described AS (
    SELECT s.*, e.name AS employer, e.country AS employer_country,
           CASE WHEN (s.n * 37) % 116 < 28  THEN 'Saudi'
                WHEN (s.n * 37) % 116 < 42  THEN 'Emirati'
                WHEN (s.n * 37) % 116 < 46  THEN 'Kuwaiti'
                WHEN (s.n * 37) % 116 < 48  THEN 'Qatari'
                WHEN (s.n * 37) % 116 < 50  THEN 'Omani'
                WHEN (s.n * 37) % 116 < 51  THEN 'Bahraini'
                WHEN (s.n * 37) % 116 < 81  THEN 'Arab expat, non-GCC'
                WHEN (s.n * 37) % 116 < 100 THEN 'South Asian'
                WHEN (s.n * 37) % 116 < 114 THEN 'Western expat'
           END AS nationality,
           row_number() OVER (PARTITION BY s.lvl ORDER BY (s.n * 53) % 116) AS rank_in_level,
           count(*) OVER (PARTITION BY s.lvl) AS level_size,
           CASE WHEN s.lvl IN ('C_SUITE', 'N_MINUS_1')
                THEN row_number() OVER (PARTITION BY s.lvl IN ('C_SUITE', 'N_MINUS_1') ORDER BY (s.n * 97) % 116) END AS package_rank
    FROM seat s
    JOIN employer e ON e.industry = s.industry AND e.slot = (s.seat_in_sector - 1) % e.slots
),
gendered AS (
    SELECT d.*,
           CASE WHEN d.rank_in_level <= (CASE d.lvl WHEN 'BOARD' THEN 1 WHEN 'C_SUITE' THEN 14
                                                    WHEN 'N_MINUS_1' THEN 13 ELSE 9 END) THEN 'FEMALE'
                WHEN d.lvl = 'N_MINUS_1' AND d.rank_in_level = d.level_size THEN 'OTHER'
                WHEN d.lvl IN ('C_SUITE', 'N_MINUS_2') AND d.rank_in_level = d.level_size THEN NULL
                ELSE 'MALE' END AS gender,
           CASE d.nationality WHEN 'South Asian' THEN 'south_asian' WHEN 'Western expat' THEN 'western'
                              ELSE 'arab' END AS name_pool
    FROM described d
),
named AS (
    SELECT g.*, (row_number() OVER (PARTITION BY g.name_pool, g.gender IS NOT DISTINCT FROM 'FEMALE' ORDER BY g.n))::int AS r
    FROM gendered g
)
-- Pool sizes are pairwise coprime (11|13 × 17, 5|7 × 9), so first × surname never repeats inside a pool.
SELECT md5('seed-report:' || :'project_id' || ':executive:' || p.n)::uuid AS id, p.*,
       CASE WHEN p.name_pool = 'arab' AND p.gender = 'FEMALE' THEN
                (ARRAY['Fatima','Noura','Layla','Mariam','Hessa','Dana','Reem','Salma','Huda','Amal','Rania'])[1 + p.r % 11]
            WHEN p.name_pool = 'arab' THEN
                (ARRAY['Khalid','Omar','Faisal','Tariq','Hassan','Yousef','Majed','Nasser','Rami','Sami','Walid','Ziad','Bader'])[1 + p.r % 13]
            WHEN p.name_pool = 'south_asian' AND p.gender = 'FEMALE' THEN
                (ARRAY['Priya','Anjali','Sana','Meera','Ayesha'])[1 + p.r % 5]
            WHEN p.name_pool = 'south_asian' THEN
                (ARRAY['Rajesh','Vikram','Imran','Arjun','Sanjay','Farhan','Nikhil'])[1 + p.r % 7]
            WHEN p.gender = 'FEMALE' THEN
                (ARRAY['Sarah','Emma','Claire','Laura','Helen'])[1 + p.r % 5]
            ELSE
                (ARRAY['James','David','Mark','Peter','Simon','Andrew','Thomas'])[1 + p.r % 7]
       END || ' ' ||
       CASE p.name_pool
            WHEN 'arab' THEN
                (ARRAY['Al-Zahrani','Al-Otaibi','Al-Dossari','Al-Harbi','Al-Shammari','Al-Ghamdi','Al-Subaie','Al-Amri',
                       'Al-Rashidi','Al-Anzi','Al-Muhanna','Haddad','Khoury','Mansour','El-Sayed','Kassem','Bahar'])[1 + p.r % 17]
            WHEN 'south_asian' THEN
                (ARRAY['Menon','Iyer','Sharma','Qureshi','Nair','Kapoor','Fernando','Chaudhry','Pillai'])[1 + p.r % 9]
            ELSE
                (ARRAY['Whitfield','Marsh','Lindqvist','Moreau','Becker','O''Connell','Hartley','Vance','Jansen'])[1 + p.r % 9]
       END AS full_name,
       CASE WHEN p.n % 29 = 0 THEN 'Egypt' WHEN p.n IN (17, 75) THEN 'Qatar' WHEN p.n = 40 THEN 'Bahrain'
            ELSE p.employer_country END AS country
FROM named p;

INSERT INTO app_lm_project_candidate
    (id, project_id, triage_company_id, company_name, full_name, title, seniority_level, status,
     location_country, location_city, nationality, gender, years_experience, note,
     compensation_currency, base_salary, allowances, bonus, long_term_incentive,
     source, added_by, created_at, updated_at)
SELECT x.id, :'project_id',
       (SELECT held.id FROM app_lm_project_triage_company held
        WHERE held.project_id = :'project_id' AND lower(held.company_name) = lower(x.employer)
        ORDER BY held.created_at LIMIT 1),
       x.employer, x.full_name,
       CASE x.lvl
            WHEN 'BOARD'     THEN (ARRAY['Board Member','Non-Executive Director'])[1 + x.n % 2]
            WHEN 'C_SUITE'   THEN (ARRAY['Chief Financial Officer','Group CFO','Regional CFO','CFO'])[1 + x.n % 4]
            WHEN 'N_MINUS_1' THEN (ARRAY['VP Finance','Finance Director','Head of FP&A','Group Financial Controller'])[1 + x.n % 4]
            ELSE                  (ARRAY['Senior Finance Manager','Head of Treasury','FP&A Manager','Financial Controller'])[1 + x.n % 4]
       END,
       x.lvl,
       COALESCE(pk.status,
                CASE WHEN (x.n * 71) % 116 < 46  THEN 'IDENTIFIED'
                     WHEN (x.n * 71) % 116 < 66  THEN 'CONTACTED'
                     WHEN (x.n * 71) % 116 < 82  THEN 'ENGAGED'
                     WHEN (x.n * 71) % 116 < 96  THEN 'INTERESTED'
                     WHEN (x.n * 71) % 116 < 108 THEN 'NOT_INTERESTED'
                     WHEN (x.n * 71) % 116 < 113 THEN 'OFF_LIMITS'
                     ELSE 'OUT_OF_SCOPE' END),
       x.country,
       CASE x.country WHEN 'United Arab Emirates' THEN (ARRAY['Dubai','Abu Dhabi'])[1 + x.n % 2]
                      WHEN 'Saudi Arabia' THEN (ARRAY['Riyadh','Jeddah'])[1 + x.n % 2]
                      WHEN 'Kuwait' THEN 'Kuwait City' WHEN 'Egypt' THEN 'Cairo'
                      WHEN 'Qatar' THEN 'Doha' ELSE 'Manama' END,
       x.nationality, x.gender,
       (CASE x.lvl WHEN 'BOARD' THEN 28 WHEN 'C_SUITE' THEN 20 WHEN 'N_MINUS_1' THEN 15 ELSE 10 END) + x.n % 6,
       pk.note,
       CASE WHEN pk.rank IS NULL THEN NULL WHEN pk.in_brief_currency THEN :'brief_currency'
            WHEN :'brief_currency' = 'AED' THEN 'SAR' ELSE 'AED' END,
       pk.base, pk.allowances, pk.bonus, pk.lti,
       'MANUAL', :'seeded_by', filed.at, filed.at
FROM seed_executive x
LEFT JOIN seed_package pk ON pk.rank = x.package_rank
CROSS JOIN LATERAL (
    -- The weekly curve 8 / 19 / 24 / 21 / 20 / 12 / 8 / 4, filed Sunday to Thursday and never in the future.
    SELECT LEAST(
        LEAST(:'kickoff'::date
              + 7 * (CASE WHEN (x.n * 89) % 116 < 8   THEN 0 WHEN (x.n * 89) % 116 < 27  THEN 1
                          WHEN (x.n * 89) % 116 < 51  THEN 2 WHEN (x.n * 89) % 116 < 72  THEN 3
                          WHEN (x.n * 89) % 116 < 92  THEN 4 WHEN (x.n * 89) % 116 < 104 THEN 5
                          WHEN (x.n * 89) % 116 < 112 THEN 6 ELSE 7 END)
              + (x.n * 13) % 5, current_date)
        + make_interval(hours => 8 + x.n % 9, mins => (x.n * 7) % 60),
        now()) AS at
) filed;

-- Researcher performance attributes by added_by, so the seeded executives are shared across the
-- mandate's staff seats (LEAD / RESEARCHER, never a client seat) — unevenly, so the table ranks.
-- A mandate staffed by its creator alone keeps every row theirs.
WITH staff AS (
    SELECT wm.user_id, row_number() OVER (ORDER BY pm.created_at, wm.user_id) - 1 AS seat
    FROM app_lm_project_member pm
    JOIN app_lm_workspace_member wm ON wm.id = pm.member_id
    WHERE pm.project_id = :'project_id'
      AND EXISTS (SELECT 1 FROM app_lm_project_member_role pmr JOIN app_lm_role r ON r.id = pmr.role_id
                  WHERE pmr.project_member_id = pm.id AND r.name IN ('LEAD', 'RESEARCHER'))
), sized AS (SELECT count(*) AS seats FROM staff)
UPDATE app_lm_project_candidate c
SET added_by = staff.user_id
FROM seed_executive x, sized, staff
WHERE c.id = x.id AND sized.seats > 1
  AND staff.seat = (CASE WHEN x.n % 10 < 5 THEN 0 WHEN x.n % 10 < 8 THEN 1 ELSE 2 END) % sized.seats;

-- A work address for most of the first seat's people and fewer of everyone else's, a third of them
-- verified, so the researcher table's quality column has a spread to show. Deleted with the row.
INSERT INTO app_lm_candidate_contact (candidate_id, channel, value, value_key, kind, verified, source)
SELECT c.id, 'EMAIL', v.address, v.address, 'WORK', x.n % 3 = 0, 'MANUAL'
FROM seed_executive x
JOIN app_lm_project_candidate c ON c.id = x.id
CROSS JOIN LATERAL (SELECT lower(regexp_replace(x.full_name, '[^A-Za-z]+', '.', 'g')) || '@example.com' AS address) v
WHERE x.n % 10 < 4 OR x.n % 3 = 0;

-- What the mandate already held keeps everything it has; only its blanks are filled, so the captures
-- count in the matrix and the diversity chapter instead of under "not on file".
UPDATE app_lm_project_candidate c
SET seniority_level = COALESCE(c.seniority_level,
        (ARRAY['C_SUITE','N_MINUS_1','N_MINUS_2'])[1 + mod(abs(hashtext(c.id::text)::bigint), 3)]),
    nationality = COALESCE(NULLIF(btrim(c.nationality), ''),
        (ARRAY['Western expat','South Asian','Arab expat, non-GCC','Saudi','Emirati','Qatari','Kuwaiti','Omani','Bahraini'])
            [1 + mod(abs(hashtext(c.id::text || ':nationality')::bigint), 9)]),
    gender = COALESCE(c.gender,
        CASE WHEN mod(abs(hashtext(c.id::text || ':gender')::bigint), 3) = 0 THEN 'FEMALE' ELSE 'MALE' END)
WHERE c.project_id = :'project_id'
  AND c.id NOT IN (SELECT id FROM seed_executive)
  AND (c.seniority_level IS NULL OR NULLIF(btrim(c.nationality), '') IS NULL OR c.gender IS NULL);

COMMIT;

\echo
\echo 'seed-report: seeded' :'mandate' '(' :project_id ') — kickoff' :kickoff
SELECT (SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = :'project_id') AS companies,
       (SELECT count(*) FROM app_lm_project_candidate WHERE project_id = :'project_id') AS executives,
       (SELECT count(*) FROM app_lm_project_candidate WHERE project_id = :'project_id' AND base_salary IS NOT NULL) AS packages,
       (SELECT count(DISTINCT nationality) FROM app_lm_project_candidate WHERE project_id = :'project_id') AS nationality_groups;
