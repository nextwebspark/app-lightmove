/**
 * What "Copy AI prompt" puts on the clipboard. Paired with the downloaded schema, it lets a model
 * outside the app write a file the import accepts; the rules below are the ones the import enforces.
 */
export const AI_TEMPLATE_PROMPT = `You are writing role templates for LightMove, an executive-search platform. A template drafts the position brief for every new search whose role title matches one of its keywords.

Reply with one JSON document and nothing else. It must validate against the attached JSON Schema (position-templates.schema.json) and have this outer shape:
{"format": "lightmove.position-templates", "formatVersion": 1, "templates": [ ... ]}

Rules:
- One template per role I describe. Leave "code" out unless I ask you to change an existing template.
- Never state a salary band, a location, a target date or a company name — those belong to each search.
- "keywords" are short lower-case fragments of the role titles this template should match, e.g. "cfo", "finance director". Keep them specific enough not to catch other roles.
- "competencies": a TECHNICAL panel and a BEHAVIOURAL panel of three to six competencies each, with a one-line description. The weights in each panel must total exactly 100.
- "criteria": three to six screening criteria. REQUIRED for must-haves, PREFERRED for tie-breakers.
- "benefits": allowance names only (e.g. "Housing allowance"), each MONTHLY or YEARLY. No amounts.
- Use only the enum values the schema lists. Use British English and the register of a retained-search brief.

Roles to write:
`;
