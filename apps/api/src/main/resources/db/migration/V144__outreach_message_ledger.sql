-- Outreach conversations, phase 1 (docs/outreach-conversations.md): every email Uncava sent becomes a line of
-- one executive's conversation on one position, read by person and position rather than through its run.
-- Expand-only. The columns a reply, a bounce or a one-off send will need are relaxed or added now so later
-- phases write rows, not schema; until they ship every row is an outbound sequence step, as before.

-- A scheduled one-off has no provider id yet, a bounce no subject or body, and nothing inbound is ever sent:
-- sent_at stays the daily cap's column and is set on an outbound email that went, never on anything else.
ALTER TABLE app_lm_outreach_message
    ALTER COLUMN enrollment_id DROP NOT NULL,
    ALTER COLUMN step DROP NOT NULL,
    ALTER COLUMN provider_message_id DROP NOT NULL,
    ALTER COLUMN subject DROP NOT NULL,
    ALTER COLUMN body DROP NOT NULL,
    ALTER COLUMN sent_at DROP NOT NULL;

-- The defaults are what every row so far is, so the previous revision's insert still lands during a deploy.
-- channel names the WhatsApp, SMS and call rows the conversation will one day carry; nothing writes them yet.
ALTER TABLE app_lm_outreach_message
    ADD COLUMN direction      varchar(8)   NOT NULL DEFAULT 'OUTBOUND'
        CONSTRAINT app_lm_outreach_message_direction_chk CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    ADD COLUMN channel        varchar(16)  NOT NULL DEFAULT 'EMAIL'
        CONSTRAINT app_lm_outreach_message_channel_chk CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'CALL')),
    ADD COLUMN kind           varchar(16)  NOT NULL DEFAULT 'SEQUENCE_STEP'
        CONSTRAINT app_lm_outreach_message_kind_chk CHECK (kind IN ('SEQUENCE_STEP', 'MANUAL', 'REPLY', 'BOUNCE')),
    ADD COLUMN status         varchar(12)  NOT NULL DEFAULT 'SENT'
        CONSTRAINT app_lm_outreach_message_status_chk
            CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED', 'RECEIVED')),
    ADD COLUMN project_id     uuid         REFERENCES app_lm_project (id) ON DELETE CASCADE,
    ADD COLUMN person_id      uuid         REFERENCES app_lm_person (id) ON DELETE CASCADE,
    -- Unmapping someone keeps what was said to them, as the enrollment does.
    ADD COLUMN candidate_id   uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    ADD COLUMN from_address   varchar(320),
    ADD COLUMN to_address     varchar(320),
    ADD COLUMN thread_gateway varchar(16)
        CONSTRAINT app_lm_outreach_message_thread_gateway_chk CHECK (thread_gateway IN ('NYLAS', 'DIRECT')),
    ADD COLUMN occurred_at    timestamptz  NOT NULL DEFAULT now(),
    -- How body_text is to be read: a sequence's paragraph text, or (one-off sends) the composer's Markdown.
    ADD COLUMN body_format    varchar(16)  NOT NULL DEFAULT 'TEXT'
        CONSTRAINT app_lm_outreach_message_body_format_chk CHECK (body_format IN ('TEXT', 'MARKDOWN')),
    ADD COLUMN body_text      text
        CONSTRAINT app_lm_outreach_message_body_text_length_chk CHECK (char_length(body_text) <= 20000),
    ADD CONSTRAINT app_lm_outreach_message_sent_at_chk CHECK (status <> 'SENT' OR sent_at IS NOT NULL),
    ADD CONSTRAINT app_lm_outreach_message_inbound_unsent_chk CHECK (direction = 'OUTBOUND' OR sent_at IS NULL);

UPDATE app_lm_outreach_message m
SET project_id     = e.project_id,
    person_id      = e.person_id,
    candidate_id   = e.candidate_id,
    to_address     = e.to_address,
    thread_gateway = e.thread_gateway,
    occurred_at    = m.sent_at
FROM app_lm_outreach_enrollment e
WHERE e.id = m.enrollment_id;

-- Only while the mailbox still holds the connection the email went from: a reconnect, which may land at another
-- address, re-stamps connected_at, and an older email is then left without a sender address rather than given the
-- new one.
UPDATE app_lm_outreach_message m
SET from_address = c.address
FROM app_lm_mailbox_connection c
WHERE c.workspace_id = m.workspace_id AND c.user_id = m.sender_user_id AND c.connected_at <= m.sent_at;

-- A first email's words are its run's first_body: the text rendered at Start and sent as it stood.
UPDATE app_lm_outreach_message m
SET body_text = left(e.first_body, 20000)
FROM app_lm_outreach_enrollment e
WHERE e.id = m.enrollment_id AND m.step = 0;

-- A follow-up is read back out of the HTML OutreachEmailBody wrote: a paragraph per <p>, a line break per <br>,
-- and every character Spring's HtmlUtils.htmlEscape turned into a reference. That is the whole HTML 4.01 table,
-- not five entities — a curly apostrophe is &rsquo; and an accent &eacute; — so the map below is Spring's own
-- (HtmlCharacterEntityReferences.properties, spring-web 7.0.9), and decimal and hex references are decoded too.
-- Each reference is decoded once, left to right, so "&amp;lt;" reads "&lt;" as it was typed.
CREATE TEMP TABLE v144_html_entity (name text PRIMARY KEY, code integer NOT NULL) ON COMMIT DROP;
INSERT INTO v144_html_entity (name, code) VALUES
    ('nbsp', 160), ('iexcl', 161), ('cent', 162), ('pound', 163), ('curren', 164), ('yen', 165),
    ('brvbar', 166), ('sect', 167), ('uml', 168), ('copy', 169), ('ordf', 170), ('laquo', 171),
    ('not', 172), ('shy', 173), ('reg', 174), ('macr', 175), ('deg', 176), ('plusmn', 177),
    ('sup2', 178), ('sup3', 179), ('acute', 180), ('micro', 181), ('para', 182), ('middot', 183),
    ('cedil', 184), ('sup1', 185), ('ordm', 186), ('raquo', 187), ('frac14', 188), ('frac12', 189),
    ('frac34', 190), ('iquest', 191), ('Agrave', 192), ('Aacute', 193), ('Acirc', 194), ('Atilde', 195),
    ('Auml', 196), ('Aring', 197), ('AElig', 198), ('Ccedil', 199), ('Egrave', 200), ('Eacute', 201),
    ('Ecirc', 202), ('Euml', 203), ('Igrave', 204), ('Iacute', 205), ('Icirc', 206), ('Iuml', 207),
    ('ETH', 208), ('Ntilde', 209), ('Ograve', 210), ('Oacute', 211), ('Ocirc', 212), ('Otilde', 213),
    ('Ouml', 214), ('times', 215), ('Oslash', 216), ('Ugrave', 217), ('Uacute', 218), ('Ucirc', 219),
    ('Uuml', 220), ('Yacute', 221), ('THORN', 222), ('szlig', 223), ('agrave', 224), ('aacute', 225),
    ('acirc', 226), ('atilde', 227), ('auml', 228), ('aring', 229), ('aelig', 230), ('ccedil', 231),
    ('egrave', 232), ('eacute', 233), ('ecirc', 234), ('euml', 235), ('igrave', 236), ('iacute', 237),
    ('icirc', 238), ('iuml', 239), ('eth', 240), ('ntilde', 241), ('ograve', 242), ('oacute', 243),
    ('ocirc', 244), ('otilde', 245), ('ouml', 246), ('divide', 247), ('oslash', 248), ('ugrave', 249),
    ('uacute', 250), ('ucirc', 251), ('uuml', 252), ('yacute', 253), ('thorn', 254), ('yuml', 255),
    ('fnof', 402), ('Alpha', 913), ('Beta', 914), ('Gamma', 915), ('Delta', 916), ('Epsilon', 917),
    ('Zeta', 918), ('Eta', 919), ('Theta', 920), ('Iota', 921), ('Kappa', 922), ('Lambda', 923),
    ('Mu', 924), ('Nu', 925), ('Xi', 926), ('Omicron', 927), ('Pi', 928), ('Rho', 929),
    ('Sigma', 931), ('Tau', 932), ('Upsilon', 933), ('Phi', 934), ('Chi', 935), ('Psi', 936),
    ('Omega', 937), ('alpha', 945), ('beta', 946), ('gamma', 947), ('delta', 948), ('epsilon', 949),
    ('zeta', 950), ('eta', 951), ('theta', 952), ('iota', 953), ('kappa', 954), ('lambda', 955),
    ('mu', 956), ('nu', 957), ('xi', 958), ('omicron', 959), ('pi', 960), ('rho', 961),
    ('sigmaf', 962), ('sigma', 963), ('tau', 964), ('upsilon', 965), ('phi', 966), ('chi', 967),
    ('psi', 968), ('omega', 969), ('thetasym', 977), ('upsih', 978), ('piv', 982), ('bull', 8226),
    ('hellip', 8230), ('prime', 8242), ('Prime', 8243), ('oline', 8254), ('frasl', 8260), ('weierp', 8472),
    ('image', 8465), ('real', 8476), ('trade', 8482), ('alefsym', 8501), ('larr', 8592), ('uarr', 8593),
    ('rarr', 8594), ('darr', 8595), ('harr', 8596), ('crarr', 8629), ('lArr', 8656), ('uArr', 8657),
    ('rArr', 8658), ('dArr', 8659), ('hArr', 8660), ('forall', 8704), ('part', 8706), ('exist', 8707),
    ('empty', 8709), ('nabla', 8711), ('isin', 8712), ('notin', 8713), ('ni', 8715), ('prod', 8719),
    ('sum', 8721), ('minus', 8722), ('lowast', 8727), ('radic', 8730), ('prop', 8733), ('infin', 8734),
    ('ang', 8736), ('and', 8743), ('or', 8744), ('cap', 8745), ('cup', 8746), ('int', 8747),
    ('there4', 8756), ('sim', 8764), ('cong', 8773), ('asymp', 8776), ('ne', 8800), ('equiv', 8801),
    ('le', 8804), ('ge', 8805), ('sub', 8834), ('sup', 8835), ('nsub', 8836), ('sube', 8838),
    ('supe', 8839), ('oplus', 8853), ('otimes', 8855), ('perp', 8869), ('sdot', 8901), ('lceil', 8968),
    ('rceil', 8969), ('lfloor', 8970), ('rfloor', 8971), ('lang', 9001), ('rang', 9002), ('loz', 9674),
    ('spades', 9824), ('clubs', 9827), ('hearts', 9829), ('diams', 9830), ('quot', 34), ('amp', 38),
    ('lt', 60), ('gt', 62), ('OElig', 338), ('oelig', 339), ('Scaron', 352), ('scaron', 353),
    ('Yuml', 376), ('circ', 710), ('tilde', 732), ('ensp', 8194), ('emsp', 8195), ('thinsp', 8201),
    ('zwnj', 8204), ('zwj', 8205), ('lrm', 8206), ('rlm', 8207), ('ndash', 8211), ('mdash', 8212),
    ('lsquo', 8216), ('rsquo', 8217), ('sbquo', 8218), ('ldquo', 8220), ('rdquo', 8221), ('bdquo', 8222),
    ('dagger', 8224), ('Dagger', 8225), ('permil', 8240), ('lsaquo', 8249), ('rsaquo', 8250), ('euro', 8364);

CREATE FUNCTION app_lm_v144_text_of(html text) RETURNS text
    LANGUAGE plpgsql AS $$
DECLARE
    rest       text;
    decoded    text := '';
    hit        text[];
    token      text;
    match_at   integer;
    code_point integer;
BEGIN
    IF html IS NULL THEN
        RETURN NULL;
    END IF;
    rest := regexp_replace(replace(replace(html, '</p><p>', E'\n\n'), '<br>', E'\n'), '<[^>]*>', '', 'g');
    LOOP
        hit := regexp_match(rest, '&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,8});');
        EXIT WHEN hit IS NULL;
        token := hit[1];
        match_at := strpos(rest, '&' || token || ';');
        code_point := CASE
            WHEN token ~ '^#[xX]' THEN ('x' || lpad(substr(token, 3), 8, '0'))::bit(32)::integer
            WHEN token ~ '^#' THEN substr(token, 2)::integer
            ELSE (SELECT e.code FROM v144_html_entity e WHERE e.name = token)
        END;
        decoded := decoded || substr(rest, 1, match_at - 1)
            || CASE WHEN code_point BETWEEN 1 AND 1114111 AND code_point NOT BETWEEN 55296 AND 57343 THEN chr(code_point)
                    ELSE '&' || token || ';' END;
        rest := substr(rest, match_at + length(token) + 2);
    END LOOP;
    RETURN decoded || rest;
END;
$$;

UPDATE app_lm_outreach_message
SET body_text = left(app_lm_v144_text_of(body), 20000)
WHERE body IS NOT NULL AND body_text IS NULL;

DROP FUNCTION app_lm_v144_text_of(text);

-- project_id and person_id stay nullable for the deploy window: the previous revision inserts without them.
-- A row it sends then is still found through its enrollment, and phase 2's migration fills it and sets NOT NULL.

ALTER TABLE app_lm_outreach_message DROP CONSTRAINT app_lm_outreach_message_step_uk;
CREATE UNIQUE INDEX app_lm_outreach_message_step_uk ON app_lm_outreach_message (enrollment_id, step)
    WHERE kind = 'SEQUENCE_STEP';

-- One executive's conversation on one position, newest first.
CREATE INDEX app_lm_outreach_message_conversation_idx
    ON app_lm_outreach_message (workspace_id, project_id, person_id, occurred_at DESC);

COMMENT ON TABLE app_lm_outreach_message IS
    'One line of an executive''s conversation on a position: an outreach email as sent, and from later phases a '
    'reply''s text, a bounce or a one-off send. Staff-only tenant data: every read filters by workspace_id.';
