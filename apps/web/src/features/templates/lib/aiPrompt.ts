/**
 * What "Copy AI prompt" puts on the clipboard. Paired with the downloaded schema, it lets a model
 * outside the app write a file the import accepts; the rules below are the ones the import enforces.
 */
export const AI_TEMPLATE_PROMPT = `You are writing role templates for LightMove, an executive-search platform. A template drafts the position brief for every new search whose role title matches one of its keywords.

Reply with one JSON document and nothing else. It must validate against the attached JSON Schema (position-templates.schema.json) and have this outer shape:
{"format": "lightmove.position-templates", "formatVersion": 2, "templates": [ ... ]}

Rules:
- One template per role I describe. Leave "code" out unless I ask you to change an existing template.
- Never state a salary band, a location, a target date or a company name — those belong to each search.
- Write each "body" in the order of the brief's steps: Role Brief (employmentType, mandateReason, confidential, notice, responsibilities, narrative), Reporting (orgChart), Compensation, Assessment (criteria, competencies, technicalShare).
- Leave "mandateReason" and "confidential" out unless every search for this role shares them; left out, each search keeps its own.
- Notice is one of 1, 2, 3 or 6 months: "noticeValue" with "noticeUnit": "MONTHS".
- "orgChart": the seats around the role as a tree. Exactly one seat has "mandateSeat": true and no title — that is the role itself, with "id": "role". Give the seat it usually reports to a title and make it the role's "parentId", and put the role's usual direct reports beneath it with "parentId": "role". Seat titles only, never people's names.
- "bonusBasis" is PERCENT_OF_BASE (a percentage) or FIXED_AMOUNT (money); "incentiveType" is OPTIONS, RSU or LTIP_CASH, or left out for none.
- "technicalShare": how much of the assessment the technical panel carries, 0 to 100; the behavioural panel takes the rest.
- "keywords" are short lower-case fragments of the role titles this template should match, e.g. "cfo", "finance director". Keep them specific enough not to catch other roles.
- "competencies": a TECHNICAL panel and a BEHAVIOURAL panel of three to six competencies each, with a one-line description. The weights in each panel must total exactly 100.
- "criteria": three to six screening criteria. REQUIRED for must-haves, PREFERRED for tie-breakers.
- "benefits": allowance names only (e.g. "Housing allowance"), each MONTHLY or YEARLY. No amounts.
- Use only the enum values the schema lists. Use British English and the register of a retained-search brief.

Roles to write:
`;
