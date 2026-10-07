-- Billing (epic #734, #736): the plan catalogue, each workspace's subscription, and the contact-credit ledger.
--
-- Contact credits are the only credit: an email found costs one, a phone found five, pooled per workspace.
-- Grants are buckets a spend drains; every movement is a row of app_lm_credit_entry, which nothing updates or
-- deletes; app_lm_credit_balance is the per-workspace cache of what the entries add up to, and its row is the
-- lock every ledger write takes first. Billing rows reference the workspace without a cascade: a financial
-- record must not disappear with a delete.

CREATE TABLE app_lm_billing_plan (
    code                     varchar(16)  PRIMARY KEY
        CONSTRAINT app_lm_billing_plan_code_chk CHECK (code IN ('CORE', 'PRO', 'ENTERPRISE')),
    name                     varchar(40)  NOT NULL,
    -- Per staff seat per month, in fils; the annual price is per month when billed yearly. Null on a custom plan.
    seat_price_monthly_fils  bigint
        CONSTRAINT app_lm_billing_plan_monthly_chk CHECK (seat_price_monthly_fils >= 0),
    seat_price_annual_fils   bigint
        CONSTRAINT app_lm_billing_plan_annual_chk CHECK (seat_price_annual_fils >= 0),
    contact_credits_per_seat integer
        CONSTRAINT app_lm_billing_plan_credits_chk CHECK (contact_credits_per_seat >= 0),
    -- Enterprise: price and credit pool are agreed per workspace and stored on its subscription.
    custom                   boolean      NOT NULL,
    stripe_price_monthly_id  varchar(64),
    stripe_price_annual_id   varchar(64),
    sort_order               smallint     NOT NULL,
    CONSTRAINT app_lm_billing_plan_priced_chk CHECK (
        custom OR (seat_price_monthly_fils IS NOT NULL AND seat_price_annual_fils IS NOT NULL
                   AND contact_credits_per_seat IS NOT NULL))
);

INSERT INTO app_lm_billing_plan (code, name, seat_price_monthly_fils, seat_price_annual_fils,
                                 contact_credits_per_seat, custom, sort_order)
VALUES ('CORE',       'Core',       29900, 23900, 50,   false, 1),
       ('PRO',        'Pro',        49900, 39900, 150,  false, 2),
       ('ENTERPRISE', 'Enterprise', NULL,  NULL,  NULL, true,  3);

CREATE TABLE app_lm_workspace_subscription (
    id                      uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            uuid         NOT NULL REFERENCES app_lm_workspace (id)
        CONSTRAINT app_lm_workspace_subscription_workspace_uk UNIQUE,
    plan_code               varchar(16)  NOT NULL REFERENCES app_lm_billing_plan (code),
    billing_interval        varchar(16)  NOT NULL
        CONSTRAINT app_lm_workspace_subscription_interval_chk CHECK (billing_interval IN ('MONTHLY', 'ANNUAL')),
    seats                   integer      NOT NULL
        CONSTRAINT app_lm_workspace_subscription_seats_chk CHECK (seats >= 0),
    -- Set on Enterprise, where the pool is agreed; on any other plan it is seats × the plan's credits per seat.
    contact_credit_pool     integer
        CONSTRAINT app_lm_workspace_subscription_pool_chk CHECK (contact_credit_pool >= 0),
    status                  varchar(16)  NOT NULL
        CONSTRAINT app_lm_workspace_subscription_status_chk
            CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'INVOICED')),
    current_period_start    timestamptz,
    current_period_end      timestamptz,
    stripe_customer_id      varchar(64),
    stripe_subscription_id  varchar(64),
    tax_registration_number varchar(32),
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    version                 bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_subscription_period_chk
        CHECK (current_period_end IS NULL OR current_period_start IS NULL OR current_period_end > current_period_start)
);

COMMENT ON TABLE app_lm_workspace_subscription IS
    'One per workspace: its plan, staff seats and status. INVOICED is billed outside Stripe, its credits granted by hand.';

CREATE TRIGGER app_lm_workspace_subscription_touch BEFORE UPDATE ON app_lm_workspace_subscription
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Every workspace that exists today is invoiced on Core, with a seat for each active staff member.
INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status)
SELECT w.id, 'CORE', 'MONTHLY',
       (SELECT count(DISTINCT m.id)
        FROM app_lm_workspace_member m
        JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
        JOIN app_lm_role r ON r.id = mr.role_id
        WHERE m.workspace_id = w.id AND m.status = 'ACTIVE' AND r.name IN ('ADMIN', 'MEMBER')),
       'INVOICED'
FROM app_lm_workspace w;

CREATE TABLE app_lm_credit_grant (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid          NOT NULL REFERENCES app_lm_workspace (id),
    source           varchar(16)   NOT NULL
        CONSTRAINT app_lm_credit_grant_source_chk
            CHECK (source IN ('PLAN', 'PURCHASED', 'PROMO', 'MANUAL', 'GOODWILL')),
    -- The order a spend drains grants in: the month's credits first, then given ones, bought ones last.
    drain_rank       smallint      NOT NULL,
    amount           bigint        NOT NULL
        CONSTRAINT app_lm_credit_grant_amount_chk CHECK (amount > 0),
    remaining        bigint        NOT NULL,
    effective_at     timestamptz   NOT NULL,
    expires_at       timestamptz,
    -- The cost basis revenue is recognised at as the credits are spent; zero for anything given away.
    fils_per_credit  numeric(14, 6) NOT NULL
        CONSTRAINT app_lm_credit_grant_cost_chk CHECK (fils_per_credit >= 0),
    -- A Stripe invoice or payment intent, or a reference an operator gave a manual grant.
    external_ref     varchar(128),
    granted_by       uuid,
    note             varchar(500),
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_credit_grant_remaining_chk CHECK (remaining >= 0 AND remaining <= amount),
    CONSTRAINT app_lm_credit_grant_expiry_chk CHECK (expires_at IS NULL OR expires_at > effective_at)
);

CREATE UNIQUE INDEX app_lm_credit_grant_external_ref_uk
    ON app_lm_credit_grant (workspace_id, source, external_ref) WHERE external_ref IS NOT NULL;
CREATE INDEX app_lm_credit_grant_spendable_idx
    ON app_lm_credit_grant (workspace_id, drain_rank, expires_at) WHERE remaining > 0;

CREATE TRIGGER app_lm_credit_grant_touch BEFORE UPDATE ON app_lm_credit_grant
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_credit_hold (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid          NOT NULL REFERENCES app_lm_workspace (id),
    action           varchar(32)   NOT NULL
        CONSTRAINT app_lm_credit_hold_action_chk CHECK (action IN ('EMAIL_FOUND', 'PHONE_FOUND')),
    credits          bigint        NOT NULL
        CONSTRAINT app_lm_credit_hold_credits_chk CHECK (credits > 0),
    -- What the grants covered; the rest is an overdraft, possible only while enforcement is off.
    covered          bigint        NOT NULL,
    status           varchar(16)   NOT NULL
        CONSTRAINT app_lm_credit_hold_status_chk CHECK (status IN ('OPEN', 'CAPTURED', 'RELEASED', 'REFUNDED')),
    idempotency_key  varchar(128)  NOT NULL,
    user_id          uuid,
    project_id       uuid,
    person_id        uuid,
    expires_at       timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_credit_hold_covered_chk CHECK (covered >= 0 AND covered <= credits),
    CONSTRAINT app_lm_credit_hold_idempotency_uk UNIQUE (workspace_id, idempotency_key)
);

CREATE INDEX app_lm_credit_hold_open_idx ON app_lm_credit_hold (expires_at) WHERE status = 'OPEN';

CREATE TRIGGER app_lm_credit_hold_touch BEFORE UPDATE ON app_lm_credit_hold
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_credit_entry (
    id               bigserial     PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES app_lm_workspace (id),
    kind             varchar(16)   NOT NULL
        CONSTRAINT app_lm_credit_entry_kind_chk
            CHECK (kind IN ('GRANT', 'HOLD', 'CAPTURE', 'RELEASE', 'REFUND', 'EXPIRE', 'ADJUST')),
    -- The change to the balance's available and held credits: their sums are what app_lm_credit_balance caches.
    available_delta  bigint        NOT NULL,
    held_delta       bigint        NOT NULL,
    -- Credits spent beyond what the grants held, recorded instead of refused while enforcement is off.
    overdraft        bigint        NOT NULL DEFAULT 0
        CONSTRAINT app_lm_credit_entry_overdraft_chk CHECK (overdraft >= 0 AND (overdraft = 0 OR kind = 'ADJUST')),
    grant_id         uuid          REFERENCES app_lm_credit_grant (id),
    hold_id          uuid          REFERENCES app_lm_credit_hold (id),
    action           varchar(32)
        CONSTRAINT app_lm_credit_entry_action_chk CHECK (action IN ('EMAIL_FOUND', 'PHONE_FOUND')),
    user_id          uuid,
    project_id       uuid,
    person_id        uuid,
    created_at       timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_credit_entry IS
    'The contact-credit ledger: append-only, one row per grant a movement touches. Tenant data: every read filters by workspace_id.';

CREATE INDEX app_lm_credit_entry_workspace_idx ON app_lm_credit_entry (workspace_id, id DESC);
CREATE INDEX app_lm_credit_entry_hold_idx ON app_lm_credit_entry (hold_id) WHERE hold_id IS NOT NULL;
CREATE INDEX app_lm_credit_entry_grant_idx ON app_lm_credit_entry (grant_id) WHERE grant_id IS NOT NULL;

CREATE OR REPLACE FUNCTION app_lm_credit_entry_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_credit_entry is append-only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_credit_entry_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON app_lm_credit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION app_lm_credit_entry_is_append_only();

CREATE TABLE app_lm_credit_balance (
    workspace_id  uuid         PRIMARY KEY REFERENCES app_lm_workspace (id),
    available     bigint       NOT NULL DEFAULT 0
        CONSTRAINT app_lm_credit_balance_available_chk CHECK (available >= 0),
    held          bigint       NOT NULL DEFAULT 0
        CONSTRAINT app_lm_credit_balance_held_chk CHECK (held >= 0),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_credit_balance IS
    'The sum of app_lm_credit_entry per workspace, and the row every ledger write locks first.';

CREATE TRIGGER app_lm_credit_balance_touch BEFORE UPDATE ON app_lm_credit_balance
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Settings → Billing: the plan, seats, buying credits and the card. Admins only.
INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'BILLING_MANAGE', 'Billing: change the plan and seats, buy credits, manage the card and invoices'),
       ('PLATFORM',  'CREDIT_GRANT',   'Grant a workspace contact credits by hand, and set an invoiced workspace''s plan and seats')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('WORKSPACE', 'ADMIN',       'BILLING_MANAGE'),
             ('PLATFORM',  'SUPER_ADMIN', 'CREDIT_GRANT')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name
ON CONFLICT (role_id, action_id) DO NOTHING;
