---
name: similar-companies
description: Find a named company's competitors, peers or look-alikes - "Seddiqi's competitors", "companies like BinHendi", "who competes with Chalhoub", "similar to us". Use whenever the question names one company and asks for others like it.
---

# Finding companies like one company

Take as few turns as you can — each turn is a wait.

1. Call identifyCompany with the name as the consultant wrote it, and the country when the question
   or the brief gives one. For "our competitors" or "companies like us", identify the hiring company
   named at the end of these instructions.
2. If matches is SEVERAL, ask which one with AskUserQuestionTool and stop: one question, one option
   per candidate, labelled with its name and described by what it does, its city and its headcount
   ("Watch and jewellery retail · Dubai · 950 staff"), the likeliest for this role first and marked
   "(Recommended)". Never ask when the consultant already chose in this chat — take that one. If
   matches is NONE and the company is the hiring company, do not stop: call searchCompaniesByActivity
   with one to four words for what it does, taken from its description and sectors at the end of
   these instructions, in its country, and carry on from step 4 with those companies. If matches is
   NONE for any other company, say it could not be found and offer to search by what it does.
3. In one turn, call findSimilarCompanies with the chosen company's apolloAccountId, or its
   linkedinSlug where it has none, and lookUpCompaniesByName with up to ten real competitors you know
   in that niche and country — the hiring company's recorded competitors too, when it is the company
   asked about. Pass count when the consultant asked for a number. Give countries only when the
   consultant named them; otherwise the company's own country is used.
4. Call proposeCompanies with the similar companies first — their apolloAccountId, or linkedinSlug
   where they have none — then the UNIVERSE and RESEARCHED lookups that work in the same niche. Leave
   out a company whose niche plainly differs (a supermarket is not a watch retailer's competitor).
5. The list below your answer shows every company, so do not repeat it — no bullet list of names. Answer in one or two
   sentences: which company "similar" was measured against and on what — its industry, its size and
   two or three of its niche words; every criterion that was loosened, in the tool's words; which
   came from LinkedIn rather than the company database; and which leading ones are already in this
   position and at what stage. If shortOf is above zero, say how many fewer were found than asked and
   offer to look in the neighbouring countries.

Never present a company as a competitor that no tool returned.
