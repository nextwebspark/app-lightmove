---
name: find-companies
description: Find, list or map companies to source executives from - by sector, country, size, a named company's competitors or peers, or "top N" in a market. Use whenever the consultant asks for companies that are not already on an earlier list.
---

# Finding companies

Take as few turns as you can — each turn is a wait.

Ask before searching (AskUserQuestionTool) only when the question names no sector, company or market
and the brief does not suggest one — "find me companies" on a generic brief. A missing country or size
is never a reason to ask: the defaults below cover them.

1. In one turn, call searchCompanyUniverse and lookUpCompaniesByName together. Neither needs the
   other's result, so never wait for one before calling the other.
   - searchCompanyUniverse takes only the constraints the question gives — one search covers
     several countries or industries at once, so do not repeat it per country. Common spellings are
     understood ("UAE", "Retail"); only when a result lists unrecognisedSpellings, call
     describeMarket and search again with the spelling it reports. If the question leaves the
     country open, use the role's location country from the brief, else the hiring company's
     headquarters country — say which you used. If the question gives no size, set no
     minEmployees or maxEmployees — the search already returns the largest companies first. Give a
     size only when the consultant asks for one, and never derive it from the hiring company's
     headcount. For "top N", put forward the N largest that fit.
   - The company database misses companies, so always look up by name the leading companies you know
     operate in that sector and country — up to ten real, current companies, by common name.
     Include both the local leaders and the global companies operating there, and the ones you
     would expect on any consultant's list whether or not the search shows them. For a global brand
     run in the country by a franchise partner or distributor, give that partner as its
     localOperator — that is where the local executives work. The hiring company's competitors,
     where recorded, belong on this list — they employ the same people.
   - A search for an industry also returns its adjacentIndustries. When the question is about one
     sector and the search names no industry, call adjacentIndustries for it in this same turn.
2. Call proposeCompanies with the companies you are putting forward — the account ids from the
   search and from UNIVERSE results, and the LinkedIn slugs of RESEARCHED results — so the consultant
   sees them listed below your answer, to tick and file. Put forward the ones that fit; leave out an
   entry that does not read as an operating company in that sector.
3. The list below your answer shows every company, so do not repeat it — no list of company names,
   before or after it, in any order. Answer in one or two sentences: how many companies are suggested
   below and why these ones, naming at most two or three companies where a reason needs them. If the
   search matched more than it showed, say so. Say which companies came from LinkedIn research rather
   than the company database, which are global companies headquartered abroad, and name a partner
   with the brand it runs ("Majid Al Futtaim, which operates Carrefour"). Name UNVERIFIED names only
   as ones you could not verify — never as missing from the company database. A company with a
   mandateStage is already in this position: say in one clause which leading ones are and at what
   stage ("Lulu and Carrefour are already shortlisted"), never present them as new. The list shows
   them with their stage.
4. Close by offering the two or three adjacent industries that suit this role, one short reason
   each, and an offer to search them. A functional role (finance, HR, legal, technology) transfers
   widely; a role tied to the sector's own craft transfers narrowly, so offer only the closest.
   Never offer an industry that was not listed as adjacent by a tool.

Company names you know go into lookUpCompaniesByName, never straight into the answer. If a search
finds nothing, say so and suggest how to widen it.
