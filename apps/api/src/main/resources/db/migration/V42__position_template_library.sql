-- The role-template library moves out of Java and into the database.
--
-- Since V7 a new mandate's brief has arrived drafted from PositionTemplates, a hand-written Java
-- catalog of seven roles matched on the mandate's title. That was always a placeholder: a firm cannot
-- edit a constant, a new role is a deploy, and the one thing the screen most wants — "give me the
-- brief for a Chief Compliance Officer" — was reachable only by naming the mandate correctly at
-- creation. This table is that catalog, with the same content plus ten more roles, in a shape a
-- workspace can eventually own.
--
-- workspace_id is nullable and that is the whole forward plan. NULL is a LightMove library template,
-- readable by every workspace and writable by nobody through the API; a non-null row is that
-- workspace's own, and the template-management screen will write those and only those. Every read
-- filters `workspace_id IS NULL OR workspace_id = :workspaceId`, so a firm's own templates are
-- tenant-scoped from the first row rather than from a later migration that has to backfill one.
--
-- The brief content is one jsonb document, following V30's filter and V36's profile rather than V39's
-- child tables. V39 drew that line precisely: child tables for a flat uniform list a step edits, jsonb
-- for a heterogeneous document read back whole by one screen. A template is the second kind — six
-- steps' worth of unlike content, never partially edited, never queried by axis, replaced wholesale
-- when an admin saves it. The eight owned lists a relational shape would need here hold no identity
-- of their own and would be written and read in one transaction every time.
--
-- Match keywords are the exception, and stay a table. They are the catalog's lookup key: the title of
-- a new mandate is matched against them, and a key buried inside a document is one nothing can index,
-- constrain or query later. It is also the same @ElementCollection idiom as
-- app_lm_position_responsibility, so nothing new is introduced.
--
-- Enums stay Java-owned — no CHECK on discipline, seniority, or any vocabulary inside body — following
-- V10, V20, V21 and V39. The body's tokens are validated where they are read: the document binds to a
-- typed record, and an unknown token fails there rather than half a release later.

CREATE TABLE app_lm_position_template (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid         REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    code         varchar(64)  NOT NULL,
    title        varchar(160) NOT NULL,
    discipline   varchar(32)  NOT NULL,
    seniority    varchar(16)  NOT NULL,
    summary      varchar(300),
    sort_order   integer      NOT NULL DEFAULT 0,
    active       boolean      NOT NULL DEFAULT true,
    body         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_position_template_touch BEFORE UPDATE ON app_lm_position_template
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

COMMENT ON TABLE app_lm_position_template IS
    'Role templates a fresh position brief is drafted from. workspace_id NULL is the shared LightMove '
    'library; a non-null row belongs to that workspace alone.';

COMMENT ON COLUMN app_lm_position_template.code IS
    'Stable slug, unique within its owner. The seeding path resolves its fallback by code, so a '
    'renamed title never changes which template a new mandate lands on.';

COMMENT ON COLUMN app_lm_position_template.sort_order IS
    'Display order in the picker, and match precedence: the first template whose keyword matches wins.';

COMMENT ON COLUMN app_lm_position_template.body IS
    'The brief content this template drafts — PositionTemplateBody. Read whole, written whole.';

-- Two partial indexes rather than one over (workspace_id, code): a unique constraint containing a
-- NULL never fires in Postgres, so a single index would let the library hold twenty rows all coded
-- chief-financial-officer. The workspace-scoped half also leads on workspace_id, which is the FK
-- index V26 and V28 would otherwise ask for.
CREATE UNIQUE INDEX app_lm_position_template_library_code_uq
    ON app_lm_position_template (code) WHERE workspace_id IS NULL;

CREATE UNIQUE INDEX app_lm_position_template_workspace_code_uq
    ON app_lm_position_template (workspace_id, code) WHERE workspace_id IS NOT NULL;

CREATE TABLE app_lm_position_template_keyword (
    template_id uuid        NOT NULL REFERENCES app_lm_position_template (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    keyword     varchar(80) NOT NULL,
    PRIMARY KEY (template_id, sort_order)
);

COMMENT ON TABLE app_lm_position_template_keyword IS
    'Lower-case fragments matched against a new mandate''s role title, in the order the template '
    'lists them. A template with no keywords is chosen deliberately and never matched by title.';

-- ── The library ──────────────────────────────────────────────────────────────
--
-- Twelve C-suite roles, four functional heads, and the generic executive a title nothing recognises
-- falls back to. Every panel totals 100, so a seeded brief reads as ready on the day it is created.
--
-- Compensation carries the shape of a GCC package — currency, base period, a market bonus target, the
-- allowance lines — and no salary band. A template cannot know a client's budget, and a number nobody
-- gave us is worse than an empty field the consultant has to fill.
--
-- Keywords are disjoint, and no keyword is a substring of another template's title, so match
-- precedence never decides between two plausible answers.
--
-- The newline after each $body$ tag is load-bearing. Flyway substitutes dollar-brace placeholders
-- before Postgres ever sees this file, and a dollar-quote opener written hard against the document's
-- opening brace ends in exactly that pair — an unresolved placeholder that fails the migration at
-- boot, comments included, so do not write one here either.

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-executive-officer', 'Chief Executive Officer', 'EXECUTIVE', 'C_SUITE', 'Full P&L ownership and the strategic agenda, accountable to the board.', 10, $body$
{
  "department": "Executive Office",
  "narrative": "The Chief Executive Officer will own the full P&L and set the strategic agenda, accountable to the board. The ideal candidate has led an organisation of comparable scale end-to-end and pairs commercial instinct with the credibility to carry shareholders, regulators and the leadership team.",
  "responsibilities": [
    "Strategy and the multi-year plan",
    "Full P&L ownership",
    "Executive team leadership",
    "Board and shareholder stewardship"
  ],
  "reportsTo": "Board of Directors",
  "directReports": [
    "Chief Financial Officer",
    "Chief Operating Officer",
    "Chief Human Resources Officer",
    "Chief Commercial Officer"
  ],
  "strategicPriorities": [
    "Portfolio growth",
    "Capital discipline",
    "Operational excellence",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": 50,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Full P&L ownership as CEO, MD or business-unit head at comparable scale", "mode": "REQUIRED"},
    {"text": "Track record setting and delivering a multi-year growth strategy", "mode": "REQUIRED"},
    {"text": "Experience working with institutional, sovereign or family shareholders", "mode": "PREFERRED"},
    {"text": "In-region operating experience across the GCC", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Strategy & Growth", "description": "Sets a multi-year agenda and finds the growth in it", "weight": 35},
    {"panel": "TECHNICAL", "name": "Commercial & P&L Management", "description": "Owns the number and the levers that move it", "weight": 35},
    {"panel": "TECHNICAL", "name": "Governance & Board Relations", "description": "Runs the board relationship and the governance around it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-executive-officer' AND workspace_id IS NULL), 0, 'chief executive officer'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-executive-officer' AND workspace_id IS NULL), 1, 'ceo'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-executive-officer' AND workspace_id IS NULL), 2, 'group ceo'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-executive-officer' AND workspace_id IS NULL), 3, 'managing director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-financial-officer', 'Chief Financial Officer', 'FINANCE', 'C_SUITE', 'Group finance, the capital structure and the shareholder relationship.', 20, $body$
{
  "department": "Finance",
  "narrative": "The Chief Financial Officer will sit on the executive committee, reporting to the Group CEO with board-level exposure. This is a hands-on leadership role for someone who has operated at scale within a diversified or multi-business-unit environment, and who can bring rigor to the function while remaining a trusted advisor to the shareholder.",
  "responsibilities": [
    "Group P&L stewardship",
    "Capital structure & treasury",
    "Board & shareholder reporting",
    "Finance transformation"
  ],
  "reportsTo": "Group CEO",
  "directReports": ["Financial Controller", "Head of Treasury", "Head of FP&A", "Head of Investor Relations"],
  "strategicPriorities": [
    "Capital discipline",
    "Governance & controls",
    "Portfolio growth",
    "Operational excellence",
    "Talent development"
  ],
  "bonusValue": 40,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Track record leading finance or ops function through M&A or restructuring", "mode": "REQUIRED"},
    {"text": "Experience reporting to a board or sovereign shareholder", "mode": "REQUIRED"},
    {"text": "Prior P&L ownership above $500M revenue scope", "mode": "REQUIRED"},
    {"text": "Sector experience relevant to the client's core business", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Financial Reporting & Controls", "description": "IFRS reporting, audit readiness and the control environment", "weight": 30},
    {"panel": "TECHNICAL", "name": "M&A / Restructuring Experience", "description": "Deal execution, carve-outs and post-merger integration", "weight": 30},
    {"panel": "TECHNICAL", "name": "Treasury & Capital Markets", "description": "Debt structuring, liquidity and lender relationships", "weight": 20},
    {"panel": "TECHNICAL", "name": "Board & Investor Relations", "description": "Board reporting and shareholder communication", "weight": 20},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-financial-officer' AND workspace_id IS NULL), 0, 'chief financial'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-financial-officer' AND workspace_id IS NULL), 1, 'cfo'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-financial-officer' AND workspace_id IS NULL), 2, 'finance director'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-financial-officer' AND workspace_id IS NULL), 3, 'group finance director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-operating-officer', 'Chief Operating Officer', 'OPERATIONS', 'C_SUITE', 'Day-to-day operations across the group, translating strategy into delivery.', 30, $body$
{
  "department": "Operations",
  "narrative": "The Chief Operating Officer will run day-to-day operations across the group, translating strategy into delivery. The ideal candidate has scaled complex, multi-site operations and drives performance through systems and people rather than heroics.",
  "responsibilities": [
    "Multi-site operational delivery",
    "Performance and productivity",
    "Supply chain and procurement",
    "Operating-model design"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Manufacturing",
    "Head of Supply Chain",
    "Head of Procurement",
    "Head of Health, Safety & Environment"
  ],
  "strategicPriorities": [
    "Operational excellence",
    "Capital discipline",
    "Portfolio growth",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": 40,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led multi-site or multi-country operations at comparable scale", "mode": "REQUIRED"},
    {"text": "Track record of measurable operational-efficiency improvement", "mode": "REQUIRED"},
    {"text": "Experience in a transformation or turnaround context", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Operational Strategy & Execution", "description": "Turns the strategy into a delivery plan that holds", "weight": 35},
    {"panel": "TECHNICAL", "name": "Process & Performance Management", "description": "Runs the operation on measures rather than escalation", "weight": 35},
    {"panel": "TECHNICAL", "name": "Supply Chain & Procurement", "description": "End-to-end supply, supplier leverage and cost", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-operating-officer' AND workspace_id IS NULL), 0, 'chief operating'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-operating-officer' AND workspace_id IS NULL), 1, 'coo'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-operating-officer' AND workspace_id IS NULL), 2, 'operations director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-technology-officer', 'Chief Technology Officer', 'TECHNOLOGY', 'C_SUITE', 'The technology strategy, the engineering organisation and the platform it ships.', 40, $body$
{
  "department": "Technology",
  "narrative": "The Chief Technology Officer will own the technology strategy and the delivery organisation behind it. The ideal candidate has built and led engineering at scale, balancing platform modernisation with commercial pragmatism.",
  "responsibilities": [
    "Technology strategy and architecture",
    "Delivery organisation and engineering standards",
    "Platform modernisation programme",
    "Cybersecurity and technology risk"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Engineering",
    "Head of Architecture",
    "Head of Cybersecurity",
    "Head of Data & Analytics"
  ],
  "strategicPriorities": [
    "Digital transformation",
    "Operational excellence",
    "Portfolio growth",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": 35,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led a technology organisation of comparable scale", "mode": "REQUIRED"},
    {"text": "Track record delivering large platform or transformation programmes", "mode": "REQUIRED"},
    {"text": "Experience presenting technology strategy at board level", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Technology Strategy & Architecture", "description": "Chooses the platform direction and defends the trade-offs", "weight": 35},
    {"panel": "TECHNICAL", "name": "Delivery & Engineering Leadership", "description": "Ships at scale with engineering standards that survive growth", "weight": 35},
    {"panel": "TECHNICAL", "name": "Cybersecurity & Risk", "description": "Owns the security posture and the risk conversation around it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-technology-officer' AND workspace_id IS NULL), 0, 'chief technology'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-technology-officer' AND workspace_id IS NULL), 1, 'cto'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-technology-officer' AND workspace_id IS NULL), 2, 'technology director'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-technology-officer' AND workspace_id IS NULL), 3, 'vp engineering');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-information-officer', 'Chief Information Officer', 'TECHNOLOGY', 'C_SUITE', 'Enterprise IT, the ERP estate and the digital operating model.', 50, $body$
{
  "department": "Information Technology",
  "narrative": "The Chief Information Officer will own enterprise IT end to end — the core platforms the business runs on, the service that keeps them up, and the roadmap that modernises them. The ideal candidate has carried a group through a major ERP or digital programme without losing the run rate.",
  "responsibilities": [
    "Enterprise IT strategy and roadmap",
    "ERP and core-platform programme",
    "Cybersecurity and technology risk",
    "IT service quality and cost"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Enterprise Applications",
    "Head of Infrastructure",
    "Head of Cybersecurity",
    "Head of IT Service Delivery"
  ],
  "strategicPriorities": [
    "Digital transformation",
    "Operational excellence",
    "Capital discipline",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": 30,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led enterprise IT for a group of comparable scale", "mode": "REQUIRED"},
    {"text": "Delivered a full ERP or core-platform implementation end to end", "mode": "REQUIRED"},
    {"text": "Experience running IT across several GCC jurisdictions", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Enterprise Architecture & ERP", "description": "Owns the core platform estate and the roadmap through it", "weight": 35},
    {"panel": "TECHNICAL", "name": "Cybersecurity & Technology Risk", "description": "Holds the security posture and the risk conversation around it", "weight": 35},
    {"panel": "TECHNICAL", "name": "IT Operations & Vendor Management", "description": "Runs service and suppliers to a cost and a standard", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-information-officer' AND workspace_id IS NULL), 0, 'chief information officer'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-information-officer' AND workspace_id IS NULL), 1, 'cio'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-information-officer' AND workspace_id IS NULL), 2, 'it director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-human-resources-officer', 'Chief Human Resources Officer', 'PEOPLE', 'C_SUITE', 'Talent, culture and organisation design across the group.', 60, $body$
{
  "department": "Human Resources",
  "narrative": "The people leader will own talent, culture and organisation design across the group. The ideal candidate has led HR through growth or restructuring at comparable scale and operates as a true business partner to the CEO.",
  "responsibilities": [
    "Talent and succession across the group",
    "Organisation design and change",
    "Reward and performance framework",
    "Culture and employee experience"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Talent Acquisition",
    "Head of Reward",
    "Head of Learning & Development",
    "Head of HR Operations"
  ],
  "strategicPriorities": [
    "Talent development",
    "Culture and engagement",
    "Operational excellence",
    "Governance & controls",
    "Portfolio growth"
  ],
  "bonusValue": 30,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led the HR function at comparable organisational scale", "mode": "REQUIRED"},
    {"text": "Experience driving organisation design or restructuring", "mode": "REQUIRED"},
    {"text": "Exposure to executive remuneration and board-level reporting", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Talent & Succession", "description": "Builds the bench the strategy will need", "weight": 35},
    {"panel": "TECHNICAL", "name": "Organisation Design & Change", "description": "Reshapes the organisation and carries people through it", "weight": 35},
    {"panel": "TECHNICAL", "name": "Reward & Performance", "description": "Reward, performance and the governance around executive pay", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-human-resources-officer' AND workspace_id IS NULL), 0, 'chief human resources'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-human-resources-officer' AND workspace_id IS NULL), 1, 'chief people'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-human-resources-officer' AND workspace_id IS NULL), 2, 'chro'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-human-resources-officer' AND workspace_id IS NULL), 3, 'hr director'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-human-resources-officer' AND workspace_id IS NULL), 4, 'people director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-commercial-officer', 'Chief Commercial Officer', 'COMMERCIAL', 'C_SUITE', 'Revenue across every channel, and the sales organisation that delivers it.', 70, $body$
{
  "department": "Commercial",
  "narrative": "The commercial leader will own revenue across all channels. The ideal candidate has built and led high-performing sales organisations at comparable scale and brings discipline to pipeline, pricing and key-account growth.",
  "responsibilities": [
    "Revenue ownership across channels",
    "Sales organisation and capability",
    "Pricing and commercial governance",
    "Key-account and partner growth"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Sales",
    "Head of Key Accounts",
    "Head of Channel Partnerships",
    "Head of Commercial Operations"
  ],
  "strategicPriorities": [
    "Portfolio growth",
    "Operational excellence",
    "Capital discipline",
    "Talent development",
    "Governance & controls"
  ],
  "bonusValue": 45,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Owned a revenue number of comparable scale", "mode": "REQUIRED"},
    {"text": "Track record building or turning around a sales organisation", "mode": "REQUIRED"},
    {"text": "Established relationships in the client's key markets", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Sales Strategy & Execution", "description": "Sets the commercial plan and holds the organisation to it", "weight": 35},
    {"panel": "TECHNICAL", "name": "Key Account & Channel Management", "description": "Grows the accounts and channels the number depends on", "weight": 35},
    {"panel": "TECHNICAL", "name": "Pricing & Commercial Operations", "description": "Pricing discipline, pipeline hygiene and deal governance", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-commercial-officer' AND workspace_id IS NULL), 0, 'chief commercial'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-commercial-officer' AND workspace_id IS NULL), 1, 'chief revenue'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-commercial-officer' AND workspace_id IS NULL), 2, 'chief sales'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-commercial-officer' AND workspace_id IS NULL), 3, 'commercial director'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-commercial-officer' AND workspace_id IS NULL), 4, 'sales director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-marketing-officer', 'Chief Marketing Officer', 'COMMERCIAL', 'C_SUITE', 'Brand, demand and customer strategy, tied to a commercial outcome.', 80, $body$
{
  "department": "Marketing",
  "narrative": "The marketing leader will own brand, demand and customer strategy. The ideal candidate has built brands and growth engines at comparable scale and connects marketing investment to commercial outcomes.",
  "responsibilities": [
    "Brand and communications strategy",
    "Demand generation and the growth engine",
    "Customer insight and segmentation",
    "Marketing budget and return"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Brand",
    "Head of Digital Marketing",
    "Head of Customer Insight",
    "Head of Corporate Communications"
  ],
  "strategicPriorities": [
    "Portfolio growth",
    "Digital transformation",
    "Capital discipline",
    "Talent development",
    "Operational excellence"
  ],
  "bonusValue": 30,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led marketing at comparable scale with clear commercial accountability", "mode": "REQUIRED"},
    {"text": "Track record building brand equity and measurable demand", "mode": "REQUIRED"},
    {"text": "Experience across both digital and traditional channels in-region", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Brand & Communications", "description": "Builds and defends the brand across every channel", "weight": 35},
    {"panel": "TECHNICAL", "name": "Digital & Performance Marketing", "description": "Runs a measurable growth engine, not a campaign calendar", "weight": 35},
    {"panel": "TECHNICAL", "name": "Customer Insight & Analytics", "description": "Turns customer evidence into where the money goes", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-marketing-officer' AND workspace_id IS NULL), 0, 'chief marketing'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-marketing-officer' AND workspace_id IS NULL), 1, 'cmo'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-marketing-officer' AND workspace_id IS NULL), 2, 'marketing director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-compliance-officer', 'Chief Compliance Officer', 'GOVERNANCE', 'C_SUITE', 'The compliance programme, the regulatory relationship and the licence to operate.', 90, $body$
{
  "department": "Compliance",
  "narrative": "The Chief Compliance Officer will own the group's compliance framework and the relationship with its regulators. The ideal candidate has built a programme rather than inherited one, and can hold a position the business does not want to hear while keeping its confidence.",
  "responsibilities": [
    "Group compliance framework and policy",
    "Regulatory relationships and reporting",
    "Financial crime, AML and sanctions",
    "Compliance monitoring and testing"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": ["Head of Regulatory Compliance", "Head of Financial Crime", "Head of Compliance Monitoring"],
  "strategicPriorities": [
    "Governance & controls",
    "Operational excellence",
    "Talent development",
    "Capital discipline",
    "Portfolio growth"
  ],
  "bonusValue": 25,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led compliance for a regulated entity of comparable scale", "mode": "REQUIRED"},
    {"text": "Owned the relationship with a financial regulator or licensing authority", "mode": "REQUIRED"},
    {"text": "Track record building an AML and sanctions programme", "mode": "REQUIRED"},
    {"text": "A recognised compliance certification (ICA, ACAMS or equivalent)", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Regulatory Framework & Licensing", "description": "Knows what the licence requires and keeps the group inside it", "weight": 30},
    {"panel": "TECHNICAL", "name": "Financial Crime, AML & Sanctions", "description": "Builds and runs the programme, not just the policy", "weight": 30},
    {"panel": "TECHNICAL", "name": "Compliance Monitoring & Testing", "description": "Tests what is claimed and reports what is found", "weight": 20},
    {"panel": "TECHNICAL", "name": "Policy & Governance", "description": "Turns obligation into policy the business can actually follow", "weight": 20},
    {"panel": "BEHAVIOURAL", "name": "Independence & Objectivity", "description": "Holds a position the business does not want to hear when the evidence requires it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Board & Regulator Credibility", "description": "Trusted in the room by the board committee and the regulator alike", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Carries the executive committee without relying on escalation", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-compliance-officer' AND workspace_id IS NULL), 0, 'chief compliance'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-compliance-officer' AND workspace_id IS NULL), 1, 'group compliance officer'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-compliance-officer' AND workspace_id IS NULL), 2, 'compliance director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('general-counsel', 'General Counsel', 'GOVERNANCE', 'C_SUITE', 'Group legal, corporate governance and the contracting the business runs on.', 100, $body$
{
  "department": "Legal",
  "narrative": "The General Counsel will lead the legal function and act as the board's adviser on governance. The ideal candidate is a qualified lawyer who has run an in-house team at comparable scale and is as comfortable on an acquisition as on a dispute.",
  "responsibilities": [
    "Group legal strategy and advice",
    "Corporate governance and company secretarial",
    "Contracting and commercial risk",
    "Disputes, litigation and regulatory matters"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Corporate Legal",
    "Head of Commercial Contracts",
    "Head of Litigation & Disputes",
    "Company Secretary"
  ],
  "strategicPriorities": [
    "Governance & controls",
    "Portfolio growth",
    "Capital discipline",
    "Operational excellence",
    "Talent development"
  ],
  "bonusValue": 30,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Qualified lawyer with in-house leadership at comparable scale", "mode": "REQUIRED"},
    {"text": "Track record advising a board on governance and disclosure", "mode": "REQUIRED"},
    {"text": "Experience of M&A and complex commercial contracting", "mode": "REQUIRED"},
    {"text": "Familiarity with GCC, DIFC or ADGM legal frameworks", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Corporate & Commercial Law", "description": "Command of the law the group's transactions actually run on", "weight": 35},
    {"panel": "TECHNICAL", "name": "Governance & Company Secretarial", "description": "Runs the board's governance machinery to a standard", "weight": 30},
    {"panel": "TECHNICAL", "name": "Disputes & Regulatory Matters", "description": "Manages exposure before it becomes a headline", "weight": 35},
    {"panel": "BEHAVIOURAL", "name": "Independence & Objectivity", "description": "Holds a position the business does not want to hear when the evidence requires it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Board & Regulator Credibility", "description": "Trusted in the room by the board committee and the regulator alike", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Carries the executive committee without relying on escalation", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'general-counsel' AND workspace_id IS NULL), 0, 'general counsel'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'general-counsel' AND workspace_id IS NULL), 1, 'chief legal'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'general-counsel' AND workspace_id IS NULL), 2, 'group legal counsel'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'general-counsel' AND workspace_id IS NULL), 3, 'legal director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-risk-officer', 'Chief Risk Officer', 'GOVERNANCE', 'C_SUITE', 'Risk appetite, the enterprise framework and the board''s view of exposure.', 110, $body$
{
  "department": "Risk",
  "narrative": "The Chief Risk Officer will own the group's risk appetite and the framework that holds it. The ideal candidate has built enterprise risk management in a regulated environment and reports to a board committee with the independence that requires.",
  "responsibilities": [
    "Group risk appetite and framework",
    "Credit, market and operational risk",
    "Risk reporting to the board committee",
    "Business continuity and resilience"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Credit Risk",
    "Head of Market Risk",
    "Head of Operational Risk",
    "Head of Business Continuity"
  ],
  "strategicPriorities": [
    "Governance & controls",
    "Capital discipline",
    "Operational excellence",
    "Talent development",
    "Portfolio growth"
  ],
  "bonusValue": 30,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Three-year performance cycle, vesting one third annually",
  "criteria": [
    {"text": "Led an enterprise risk function at comparable scale", "mode": "REQUIRED"},
    {"text": "Owned a board-approved risk appetite framework end to end", "mode": "REQUIRED"},
    {"text": "Regulated financial services experience in the GCC", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Enterprise Risk Framework", "description": "Sets appetite and makes it bind on real decisions", "weight": 35},
    {"panel": "TECHNICAL", "name": "Credit & Market Risk", "description": "Measures the exposures the balance sheet actually carries", "weight": 30},
    {"panel": "TECHNICAL", "name": "Operational Resilience", "description": "Keeps the group running through the event it planned for", "weight": 35},
    {"panel": "BEHAVIOURAL", "name": "Independence & Objectivity", "description": "Holds a position the business does not want to hear when the evidence requires it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Board & Regulator Credibility", "description": "Trusted in the room by the board committee and the regulator alike", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Carries the executive committee without relying on escalation", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-risk-officer' AND workspace_id IS NULL), 0, 'chief risk'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-risk-officer' AND workspace_id IS NULL), 1, 'risk director'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-risk-officer' AND workspace_id IS NULL), 2, 'group head of risk');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('chief-investment-officer', 'Chief Investment Officer', 'INVESTMENT', 'C_SUITE', 'Investment strategy, origination and the portfolio''s risk-adjusted return.', 120, $body$
{
  "department": "Investments",
  "narrative": "The Chief Investment Officer will set investment strategy and asset allocation, and own the portfolio's performance through the cycle. The ideal candidate has originated and exited at comparable ticket size and is credible in front of an investment committee.",
  "responsibilities": [
    "Investment strategy and asset allocation",
    "Origination and deal execution",
    "Portfolio construction and performance",
    "Investment committee reporting"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [
    "Head of Private Equity",
    "Head of Public Markets",
    "Head of Real Assets",
    "Head of Portfolio Management"
  ],
  "strategicPriorities": [
    "Portfolio growth",
    "Risk-adjusted returns",
    "Capital discipline",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": 40,
  "bonusBasis": "PERCENT_OF_BASE",
  "incentiveType": "LTIP_CASH",
  "incentiveVesting": "Aligned to portfolio performance over a three-year cycle",
  "criteria": [
    {"text": "Led an investment function or a major asset class at comparable AUM", "mode": "REQUIRED"},
    {"text": "Track record of origination and exits through a full cycle", "mode": "REQUIRED"},
    {"text": "Experience presenting to an investment committee or board", "mode": "REQUIRED"},
    {"text": "Regional deal experience across the GCC", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Investment Strategy & Allocation", "description": "Decides where the capital goes and defends the thesis", "weight": 30},
    {"panel": "TECHNICAL", "name": "Origination & Execution", "description": "Finds the deal and gets it closed on terms", "weight": 30},
    {"panel": "TECHNICAL", "name": "Portfolio & Risk Management", "description": "Runs the book rather than a collection of positions", "weight": 25},
    {"panel": "TECHNICAL", "name": "Valuation & Financial Modelling", "description": "Prices an asset and knows what the model cannot see", "weight": 15},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-investment-officer' AND workspace_id IS NULL), 0, 'chief investment officer'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-investment-officer' AND workspace_id IS NULL), 1, 'head of investments'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'chief-investment-officer' AND workspace_id IS NULL), 2, 'investment director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('head-of-finance', 'Head of Finance', 'FINANCE', 'N_MINUS_1', 'The close, the plan and the controls, one level below the CFO.', 130, $body$
{
  "department": "Finance",
  "narrative": "The Head of Finance will own the reporting cycle end to end — the close, the statutory accounts, the plan and the controls around them. The ideal candidate is a qualified accountant who has run the function hands-on in a group structure.",
  "responsibilities": [
    "Statutory reporting and the monthly close",
    "Budgeting, forecasting and analysis",
    "Controls and audit readiness",
    "Finance team leadership"
  ],
  "reportsTo": "Chief Financial Officer",
  "directReports": ["Financial Controller", "FP&A Manager", "Treasury Manager"],
  "strategicPriorities": [
    "Governance & controls",
    "Capital discipline",
    "Operational excellence",
    "Talent development",
    "Digital transformation"
  ],
  "bonusValue": 20,
  "bonusBasis": "PERCENT_OF_BASE",
  "criteria": [
    {"text": "Qualified accountant (CA, ACCA, CPA or equivalent)", "mode": "REQUIRED"},
    {"text": "Ownership of the monthly close and statutory reporting", "mode": "REQUIRED"},
    {"text": "IFRS reporting within a group structure", "mode": "PREFERRED"},
    {"text": "GCC VAT and corporate-tax exposure", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Financial Reporting & IFRS", "description": "Closes the books to a deadline and a standard", "weight": 35},
    {"panel": "TECHNICAL", "name": "Planning & Analysis", "description": "Builds a plan the business recognises and can be held to", "weight": 30},
    {"panel": "TECHNICAL", "name": "Controls & Audit", "description": "Keeps the control environment audit-ready rather than audit-driven", "weight": 20},
    {"panel": "TECHNICAL", "name": "Finance Systems & Process", "description": "Improves the process instead of adding another spreadsheet", "weight": 15},
    {"panel": "BEHAVIOURAL", "name": "Functional Leadership", "description": "Runs a team and develops the people in it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Trusted by the executive committee and the business the function serves", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Improves the function while keeping the day job running", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Pressure", "description": "Holds the standard through peak load and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-finance' AND workspace_id IS NULL), 0, 'head of finance'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-finance' AND workspace_id IS NULL), 1, 'financial controller'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-finance' AND workspace_id IS NULL), 2, 'group financial controller'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-finance' AND workspace_id IS NULL), 3, 'finance manager');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('head-of-compliance', 'Head of Compliance', 'GOVERNANCE', 'N_MINUS_1', 'Day-to-day compliance, monitoring and the regulatory submissions.', 140, $body$
{
  "department": "Compliance",
  "narrative": "The Head of Compliance will run the programme day to day — the advice the business asks for, the monitoring it does not, and the submissions the regulator expects on time. The ideal candidate is hands-on and has worked inside a regulated entity.",
  "responsibilities": [
    "Day-to-day compliance advice",
    "The monitoring and testing programme",
    "Regulatory reporting and submissions",
    "Policy maintenance and training"
  ],
  "reportsTo": "Chief Compliance Officer",
  "directReports": ["Compliance Monitoring Manager", "Financial Crime Manager", "Regulatory Reporting Analyst"],
  "strategicPriorities": [
    "Governance & controls",
    "Operational excellence",
    "Talent development",
    "Digital transformation",
    "Capital discipline"
  ],
  "bonusValue": 20,
  "bonusBasis": "PERCENT_OF_BASE",
  "criteria": [
    {"text": "Compliance ownership within a regulated entity", "mode": "REQUIRED"},
    {"text": "Hands-on AML, KYC and sanctions experience", "mode": "REQUIRED"},
    {"text": "A recognised compliance certification (ICA, ACAMS or equivalent)", "mode": "PREFERRED"},
    {"text": "Comfortable with regulatory correspondence in Arabic", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Regulatory Knowledge", "description": "Knows the rulebook the entity is licensed under", "weight": 30},
    {"panel": "TECHNICAL", "name": "AML, KYC & Sanctions", "description": "Runs screening and case work that stands up to inspection", "weight": 30},
    {"panel": "TECHNICAL", "name": "Monitoring & Testing", "description": "Finds the gap before the regulator does", "weight": 25},
    {"panel": "TECHNICAL", "name": "Policy & Training", "description": "Writes policy the business can follow and teaches it", "weight": 15},
    {"panel": "BEHAVIOURAL", "name": "Independence & Objectivity", "description": "Holds a position the business does not want to hear when the evidence requires it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Board & Regulator Credibility", "description": "Trusted in the room by the board committee and the regulator alike", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Carries the executive committee without relying on escalation", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-compliance' AND workspace_id IS NULL), 0, 'head of compliance'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-compliance' AND workspace_id IS NULL), 1, 'compliance manager');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('head-of-human-resources', 'Head of Human Resources', 'PEOPLE', 'N_MINUS_1', 'Hiring, reward, employee relations and the HR operation behind them.', 150, $body$
{
  "department": "Human Resources",
  "narrative": "The Head of HR will run the people function day to day — hiring, the reward and performance cycles, employee relations and the operation underneath. The ideal candidate has done it hands-on at comparable headcount and knows GCC labour law in practice rather than in theory.",
  "responsibilities": [
    "Talent acquisition and onboarding",
    "Performance and reward cycles",
    "Employee relations and policy",
    "HR operations and systems"
  ],
  "reportsTo": "Chief Human Resources Officer",
  "directReports": [
    "Talent Acquisition Manager",
    "HR Business Partner",
    "Reward & Payroll Manager",
    "HR Operations Manager"
  ],
  "strategicPriorities": [
    "Talent development",
    "Culture and engagement",
    "Governance & controls",
    "Operational excellence",
    "Digital transformation"
  ],
  "bonusValue": 20,
  "bonusBasis": "PERCENT_OF_BASE",
  "criteria": [
    {"text": "Led an HR function, or a significant HR remit, at comparable headcount", "mode": "REQUIRED"},
    {"text": "Working knowledge of GCC labour law and nationalisation quotas", "mode": "REQUIRED"},
    {"text": "HRIS implementation or migration experience", "mode": "PREFERRED"},
    {"text": "Experience of a high-volume hiring programme", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Talent Acquisition & Onboarding", "description": "Fills the roles the plan depends on, at quality", "weight": 30},
    {"panel": "TECHNICAL", "name": "Reward & Performance", "description": "Runs the cycles so they change behaviour rather than fill a form", "weight": 25},
    {"panel": "TECHNICAL", "name": "Employee Relations & Labour Law", "description": "Handles the difficult case correctly the first time", "weight": 25},
    {"panel": "TECHNICAL", "name": "HR Operations & Systems", "description": "Payroll, records and the system that keeps them true", "weight": 20},
    {"panel": "BEHAVIOURAL", "name": "Functional Leadership", "description": "Runs a team and develops the people in it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Trusted by the executive committee and the business the function serves", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Improves the function while keeping the day job running", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Pressure", "description": "Holds the standard through peak load and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-human-resources' AND workspace_id IS NULL), 0, 'head of hr'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-human-resources' AND workspace_id IS NULL), 1, 'head of human resources'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-human-resources' AND workspace_id IS NULL), 2, 'head of people'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-human-resources' AND workspace_id IS NULL), 3, 'hr manager');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('head-of-internal-audit', 'Head of Internal Audit', 'GOVERNANCE', 'N_MINUS_1', 'The risk-based audit plan and the reporting line into the audit committee.', 160, $body$
{
  "department": "Internal Audit",
  "narrative": "The Head of Internal Audit will own the audit plan and the reporting line into the audit committee. The ideal candidate has run a risk-based function, follows through on management actions, and keeps the independence the role only has if it is used.",
  "responsibilities": [
    "The risk-based annual audit plan",
    "Audit execution and reporting",
    "Follow-up on management actions",
    "Audit committee reporting"
  ],
  "reportsTo": "Audit Committee",
  "directReports": ["Internal Audit Manager", "IT Audit Manager", "Senior Internal Auditor"],
  "strategicPriorities": [
    "Governance & controls",
    "Operational excellence",
    "Capital discipline",
    "Talent development",
    "Digital transformation"
  ],
  "bonusValue": 20,
  "bonusBasis": "PERCENT_OF_BASE",
  "criteria": [
    {"text": "Led internal audit at comparable scale", "mode": "REQUIRED"},
    {"text": "Qualified (CIA, CA, ACCA or equivalent)", "mode": "REQUIRED"},
    {"text": "Experience reporting directly to an audit committee", "mode": "REQUIRED"},
    {"text": "IT audit or data-analytics capability", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Risk-Based Audit Planning", "description": "Points the plan at where the risk actually is", "weight": 30},
    {"panel": "TECHNICAL", "name": "Audit Execution & Reporting", "description": "Evidences a finding so it survives being argued with", "weight": 30},
    {"panel": "TECHNICAL", "name": "Internal Controls & Governance", "description": "Reads a control environment and where it thins out", "weight": 25},
    {"panel": "TECHNICAL", "name": "Data Analytics in Audit", "description": "Tests the population rather than a sample of it", "weight": 15},
    {"panel": "BEHAVIOURAL", "name": "Independence & Objectivity", "description": "Holds a position the business does not want to hear when the evidence requires it", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Board & Regulator Credibility", "description": "Trusted in the room by the board committee and the regulator alike", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Carries the executive committee without relying on escalation", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);

INSERT INTO app_lm_position_template_keyword (template_id, sort_order, keyword)
VALUES
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-internal-audit' AND workspace_id IS NULL), 0, 'head of internal audit'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-internal-audit' AND workspace_id IS NULL), 1, 'internal audit'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-internal-audit' AND workspace_id IS NULL), 2, 'chief audit executive'),
    ((SELECT id FROM app_lm_position_template WHERE code = 'head-of-internal-audit' AND workspace_id IS NULL), 3, 'audit director');

INSERT INTO app_lm_position_template
    (code, title, discipline, seniority, summary, sort_order, body)
VALUES ('generic-executive', 'Senior Executive (generic)', 'EXECUTIVE', 'N_MINUS_1', 'The fallback brief for a title the library does not recognise yet.', 170, $body$
{
  "department": null,
  "narrative": "This is a senior leadership appointment with significant scope and visibility. The ideal candidate combines a strong operating track record at comparable scale with the presence to influence senior stakeholders from day one.",
  "responsibilities": [
    "Functional ownership and delivery",
    "Leadership team contribution",
    "Budget and headcount accountability",
    "Stakeholder and executive reporting"
  ],
  "reportsTo": "Chief Executive Officer",
  "directReports": [],
  "strategicPriorities": [
    "Capital discipline",
    "Portfolio growth",
    "Operational excellence",
    "Governance & controls",
    "Talent development"
  ],
  "bonusValue": null,
  "bonusBasis": null,
  "criteria": [
    {"text": "Track record operating at comparable scale and scope", "mode": "REQUIRED"},
    {"text": "Experience leading through significant organisational change", "mode": "REQUIRED"},
    {"text": "Prior experience in the client's sector or an adjacent one", "mode": "PREFERRED"}
  ],
  "competencies": [
    {"panel": "TECHNICAL", "name": "Functional Depth", "description": "Command of the discipline the role is accountable for", "weight": 40},
    {"panel": "TECHNICAL", "name": "Commercial Acumen", "description": "Connects functional decisions to commercial outcomes", "weight": 30},
    {"panel": "TECHNICAL", "name": "Operational Excellence", "description": "Runs the function through systems rather than heroics", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Strategic Leadership", "description": "Sets direction and carries a transformation at group level", "weight": 30},
    {"panel": "BEHAVIOURAL", "name": "Stakeholder Influence", "description": "Credible with the shareholder, the board and the executive committee", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Change Management", "description": "Builds the function's capability while running the day job", "weight": 25},
    {"panel": "BEHAVIOURAL", "name": "Resilience under Ambiguity", "description": "Operates through market shifts and incomplete information", "weight": 20}
  ],
  "employmentType": "FULL_TIME_PERMANENT",
  "currency": "USD",
  "baseSalaryMode": "ANNUAL",
  "noticeValue": 3,
  "noticeUnit": "MONTHS",
  "benefits": [
    {"name": "Housing allowance", "frequency": "MONTHLY"},
    {"name": "Transport allowance", "frequency": "MONTHLY"},
    {"name": "Family medical cover", "frequency": "YEARLY"},
    {"name": "Children's education", "frequency": "YEARLY"},
    {"name": "Annual home leave flights", "frequency": "YEARLY"}
  ]
}$body$);
