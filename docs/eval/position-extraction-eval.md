# Position description extraction eval

Written by `PositionExtractionEval` (`apps/api/src/test/java/app/lightmove/api/position/service`): every field
the four readings (details, context, assessment, reporting) propose, through the shipped proposers. Every run
appends one section: counts and weights only, never a document's name or text. "Golden" scores a field
against `<file>.expected.json` kept beside a private document; "Balanced" is what the SPA's
`balancePanelWeights` lands on a panel nobody typed into. `baseline` swaps in the prompts kept under test
resources as `prompts/<id>-baseline-*` (today: assessment only, as it was before weights became shares).

## 2026-10-14 — baseline, gemini-3.5-flash, 4 documents (3 readable) × 2 runs

| Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
|---|---|---|---|---|
| details | 4 / 6 | 8.7 | 100% | 2 / 50 / 0 |
| context | 6 / 6 | 6.7 | 100% | 0 / 40 / 0 |
| assessment | 6 / 6 | 11.8 | 100% | 0 / 71 / 0 |
| reporting | 6 / 6 | 0.7 | 100% | 0 / 4 / 0 |

| Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
|---|---|---|---|---|---|
| roleTitle | 4 / 6 | 100% | 2 / 2 / 0 | 2 / 2 docs | — |
| department | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| locationCity | 2 / 6 | 100% | 0 / 2 / 0 | 1 / 1 docs | — |
| locationCountry | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| employmentType | 0 / 6 | — | — | — | — |
| seniority | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| narrative | 4 / 6 | 100% | 0 / 4 / 0 | 0 / 2 docs | — |
| responsibility | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 1 / 3 docs | — |
| mandateReason | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| businessDriver | 6 / 6 | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| strategicPriority | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 2 / 3 docs | — |
| requiredCriterion | 6 / 6 (17 items) | 100% | 0 / 17 / 0 | 1 / 3 docs | — |
| preferredCriterion | 6 / 6 (6 items) | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| technicalCompetency | 6 / 6 (25 items) | 100% | 0 / 25 / 0 | 1 / 3 docs | — |
| behaviouralCompetency | 6 / 6 (23 items) | 100% | 0 / 23 / 0 | 1 / 3 docs | — |
| reportsToTitle | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| directReportTitle | 0 / 6 (0 items) | — | — | — | — |
| teamSize | 0 / 6 | — | — | — | — |
| noticePeriod | 0 / 6 | — | — | — | — |

| Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
|---|---|---|---|---|---|---|
| 1 · 1 | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 1 · 2 | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 3 · 1 | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 3 · 2 | [0, 0, 0, 0, 0] | 0 | [20, 20, 20, 20, 20] | [0, 0, 0, 0, 0] | 0 | [20, 20, 20, 20, 20] |
| 4 · 1 | [90, 80, 70, 60] | 300 | [30, 27, 23, 20] | [80, 70, 60] | 210 | [38, 33, 29] |
| 4 · 2 | [90, 80, 70, 60] | 300 | [30, 27, 23, 20] | [80, 70, 60] | 210 | [38, 33, 29] |

**Rule checks:** 16 failed.
- document 1 run 1: behaviouralCompetency weights total 0 before balancing
- document 1 run 1: technicalCompetency weights total 0 before balancing
- document 1 run 2: behaviouralCompetency weights total 0 before balancing
- document 1 run 2: technicalCompetency weights total 0 before balancing
- document 3 run 1: details answered by documentHeadings, not the model
- document 3 run 1: no role title
- document 3 run 1: behaviouralCompetency weights total 0 before balancing
- document 3 run 1: technicalCompetency weights total 0 before balancing
- document 3 run 2: details answered by documentHeadings, not the model
- document 3 run 2: no role title
- document 3 run 2: behaviouralCompetency weights total 0 before balancing
- document 3 run 2: technicalCompetency weights total 0 before balancing
- document 4 run 1: behaviouralCompetency weights total 210 before balancing
- document 4 run 1: technicalCompetency weights total 300 before balancing
- document 4 run 2: behaviouralCompetency weights total 210 before balancing
- document 4 run 2: technicalCompetency weights total 300 before balancing
- unreadable by the app: document 2: That document has more than 40000 characters. Shorten it to the role description and attach it again.

## 2026-10-14 — shipped, gemini-3.5-flash, 4 documents (3 readable) × 2 runs

| Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
|---|---|---|---|---|
| details | 5 / 6 | 9.3 | 100% | 2 / 54 / 0 |
| context | 6 / 6 | 6.7 | 100% | 0 / 40 / 0 |
| assessment | 6 / 6 | 9.7 | 100% | 0 / 58 / 0 |
| reporting | 6 / 6 | 0.7 | 100% | 0 / 4 / 0 |

| Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
|---|---|---|---|---|---|
| roleTitle | 5 / 6 | 100% | 2 / 3 / 0 | 2 / 3 docs | — |
| department | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | — |
| locationCity | 2 / 6 | 100% | 0 / 2 / 0 | 1 / 1 docs | — |
| locationCountry | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| employmentType | 0 / 6 | — | — | — | — |
| seniority | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | — |
| narrative | 5 / 6 | 100% | 0 / 5 / 0 | 0 / 3 docs | — |
| responsibility | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 0 / 3 docs | — |
| mandateReason | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| businessDriver | 6 / 6 | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| strategicPriority | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 1 / 3 docs | — |
| requiredCriterion | 6 / 6 (17 items) | 100% | 0 / 17 / 0 | 2 / 3 docs | — |
| preferredCriterion | 5 / 6 (5 items) | 100% | 0 / 5 / 0 | 1 / 3 docs | — |
| technicalCompetency | 6 / 6 (18 items) | 100% | 0 / 18 / 0 | 2 / 3 docs | — |
| behaviouralCompetency | 6 / 6 (18 items) | 100% | 0 / 18 / 0 | 2 / 3 docs | — |
| reportsToTitle | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | — |
| directReportTitle | 0 / 6 (0 items) | — | — | — | — |
| teamSize | 0 / 6 | — | — | — | — |
| noticePeriod | 0 / 6 | — | — | — | — |

| Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
|---|---|---|---|---|---|---|
| 1 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [35, 35, 30] | 100 | [35, 35, 30] |
| 1 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [35, 35, 30] | 100 | [35, 35, 30] |
| 3 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 3 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 4 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 4 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |

**Rule checks:** 2 failed.
- document 3 run 2: details answered by documentHeadings, not the model
- document 3 run 2: no role title
- unreadable by the app: document 2: That document has more than 40000 characters. Shorten it to the role description and attach it again.

## 2026-10-14 — baseline, gemini-3.5-flash, 4 documents (3 readable) × 2 runs

**Golden, all fields:** 174 / 192 (91%)

| Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
|---|---|---|---|---|
| details | 5 / 6 | 9.3 | 100% | 2 / 54 / 0 |
| context | 6 / 6 | 6.7 | 100% | 0 / 40 / 0 |
| assessment | 6 / 6 | 11.7 | 100% | 0 / 70 / 0 |
| reporting | 6 / 6 | 0.7 | 100% | 0 / 4 / 0 |

| Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
|---|---|---|---|---|---|
| roleTitle | 5 / 6 | 100% | 2 / 3 / 0 | 2 / 3 docs | 5 / 6 |
| department | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | 5 / 6 |
| locationCity | 2 / 6 | 100% | 0 / 2 / 0 | 1 / 1 docs | 6 / 6 |
| locationCountry | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 2 / 2 |
| employmentType | 0 / 6 | — | — | — | 6 / 6 |
| seniority | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | 3 / 6 |
| narrative | 5 / 6 | 100% | 0 / 5 / 0 | 1 / 3 docs | — |
| responsibility | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 1 / 3 docs | 30 / 30 |
| mandateReason | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 2 / 6 |
| businessDriver | 6 / 6 | 100% | 0 / 6 / 0 | 3 / 3 docs | — |
| strategicPriority | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 2 / 3 docs | 23 / 30 |
| requiredCriterion | 6 / 6 (17 items) | 100% | 0 / 17 / 0 | 1 / 3 docs | 17 / 17 |
| preferredCriterion | 6 / 6 (6 items) | 100% | 0 / 6 / 0 | 1 / 3 docs | 4 / 6 |
| technicalCompetency | 6 / 6 (24 items) | 100% | 0 / 24 / 0 | 0 / 3 docs | 24 / 24 |
| behaviouralCompetency | 6 / 6 (23 items) | 100% | 0 / 23 / 0 | 1 / 3 docs | 23 / 23 |
| reportsToTitle | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 6 / 6 |
| directReportTitle | 0 / 6 (0 items) | — | — | — | 6 / 6 |
| teamSize | 0 / 6 | — | — | — | 6 / 6 |
| noticePeriod | 0 / 6 | — | — | — | 6 / 6 |

| Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
|---|---|---|---|---|---|---|
| 1 · 1 | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 1 · 2 | [0, 0, 0] | 0 | [34, 33, 33] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 3 · 1 | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] | [0, 0, 0, 0] | 0 | [25, 25, 25, 25] |
| 3 · 2 | [0, 0, 0, 0, 0] | 0 | [20, 20, 20, 20, 20] | [0, 0, 0, 0, 0] | 0 | [20, 20, 20, 20, 20] |
| 4 · 1 | [90, 80, 75, 60] | 305 | [29, 26, 25, 20] | [80, 70, 70] | 220 | [36, 32, 32] |
| 4 · 2 | [90, 80, 70, 60] | 300 | [30, 27, 23, 20] | [80, 70, 60] | 210 | [38, 33, 29] |

**Rule checks:** 14 failed.
- document 1 run 1: technicalCompetency weights total 0 before balancing
- document 1 run 1: behaviouralCompetency weights total 0 before balancing
- document 1 run 2: technicalCompetency weights total 0 before balancing
- document 1 run 2: behaviouralCompetency weights total 0 before balancing
- document 3 run 1: details answered by documentHeadings, not the model
- document 3 run 1: no role title
- document 3 run 1: technicalCompetency weights total 0 before balancing
- document 3 run 1: behaviouralCompetency weights total 0 before balancing
- document 3 run 2: technicalCompetency weights total 0 before balancing
- document 3 run 2: behaviouralCompetency weights total 0 before balancing
- document 4 run 1: technicalCompetency weights total 305 before balancing
- document 4 run 1: behaviouralCompetency weights total 220 before balancing
- document 4 run 2: technicalCompetency weights total 300 before balancing
- document 4 run 2: behaviouralCompetency weights total 210 before balancing
- unreadable by the app: document 2: That document has more than 40000 characters. Shorten it to the role description and attach it again.

## 2026-10-14 — shipped, gemini-3.5-flash, 4 documents (3 readable) × 2 runs

**Golden, all fields:** 167 / 189 (88%)

| Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
|---|---|---|---|---|
| details | 5 / 6 | 9.3 | 98% | 2 / 53 / 1 |
| context | 6 / 6 | 6.7 | 100% | 0 / 40 / 0 |
| assessment | 6 / 6 | 11.0 | 100% | 0 / 66 / 0 |
| reporting | 6 / 6 | 0.7 | 100% | 0 / 4 / 0 |

| Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
|---|---|---|---|---|---|
| roleTitle | 5 / 6 | 100% | 2 / 3 / 0 | 2 / 3 docs | 5 / 6 |
| department | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | 5 / 6 |
| locationCity | 2 / 6 | 100% | 0 / 2 / 0 | 1 / 1 docs | 6 / 6 |
| locationCountry | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 2 / 2 |
| employmentType | 0 / 6 | — | — | — | 6 / 6 |
| seniority | 5 / 6 | 100% | 0 / 5 / 0 | 2 / 3 docs | 3 / 6 |
| narrative | 5 / 6 | 100% | 0 / 5 / 0 | 1 / 3 docs | — |
| responsibility | 6 / 6 (30 items) | 97% | 0 / 29 / 1 | 1 / 3 docs | 30 / 30 |
| mandateReason | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 2 / 6 |
| businessDriver | 6 / 6 | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| strategicPriority | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 1 / 3 docs | 21 / 30 |
| requiredCriterion | 6 / 6 (19 items) | 100% | 0 / 19 / 0 | 0 / 3 docs | 19 / 19 |
| preferredCriterion | 5 / 6 (5 items) | 100% | 0 / 5 / 0 | 2 / 3 docs | 3 / 6 |
| technicalCompetency | 6 / 6 (21 items) | 100% | 0 / 21 / 0 | 0 / 3 docs | 20 / 21 |
| behaviouralCompetency | 6 / 6 (21 items) | 100% | 0 / 21 / 0 | 1 / 3 docs | 21 / 21 |
| reportsToTitle | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 6 / 6 |
| directReportTitle | 0 / 6 (0 items) | — | — | — | 6 / 6 |
| teamSize | 0 / 6 | — | — | — | 6 / 6 |
| noticePeriod | 0 / 6 | — | — | — | 6 / 6 |

| Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
|---|---|---|---|---|---|---|
| 1 · 1 | [40, 30, 15, 15] | 100 | [40, 30, 15, 15] | [30, 25, 25, 20] | 100 | [30, 25, 25, 20] |
| 1 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [35, 35, 30] | 100 | [35, 35, 30] |
| 3 · 1 | [35, 25, 20, 20] | 100 | [35, 25, 20, 20] | [30, 25, 25, 20] | 100 | [30, 25, 25, 20] |
| 3 · 2 | [35, 25, 20, 20] | 100 | [35, 25, 20, 20] | [40, 30, 15, 15] | 100 | [40, 30, 15, 15] |
| 4 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 4 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |

**Rule checks:** 2 failed.
- document 3 run 1: details answered by documentHeadings, not the model
- document 3 run 1: no role title
- unreadable by the app: document 2: That document has more than 40000 characters. Shorten it to the role description and attach it again.

## 2026-10-14 — shipped, gemini-3.5-flash, 4 documents (3 readable) × 2 runs

**Golden, all fields:** 177 / 184 (96%)

| Section | Model answered | Fields per reading | Snippet verified | High / medium / low |
|---|---|---|---|---|
| details | 6 / 6 | 10.0 | 97% | 6 / 52 / 2 |
| context | 6 / 6 | 6.0 | 100% | 0 / 36 / 0 |
| assessment | 6 / 6 | 10.3 | 100% | 0 / 62 / 0 |
| reporting | 6 / 6 | 0.7 | 100% | 0 / 4 / 0 |

| Field | Proposed | Snippet verified | High / medium / low | Same every run | Golden |
|---|---|---|---|---|---|
| roleTitle | 6 / 6 | 100% | 2 / 4 / 0 | 3 / 3 docs | 6 / 6 |
| department | 6 / 6 | 100% | 0 / 6 / 0 | 3 / 3 docs | 6 / 6 |
| locationCity | 2 / 6 | 100% | 0 / 2 / 0 | 1 / 1 docs | 6 / 6 |
| locationCountry | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 2 / 2 |
| employmentType | 0 / 6 | — | — | — | 6 / 6 |
| seniority | 6 / 6 | 67% | 4 / 0 / 2 | 3 / 3 docs | 6 / 6 |
| narrative | 6 / 6 | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| responsibility | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 2 / 3 docs | 30 / 30 |
| mandateReason | 0 / 6 | — | — | — | 6 / 6 |
| businessDriver | 6 / 6 | 100% | 0 / 6 / 0 | 2 / 3 docs | — |
| strategicPriority | 6 / 6 (30 items) | 100% | 0 / 30 / 0 | 2 / 3 docs | 28 / 30 |
| requiredCriterion | 6 / 6 (18 items) | 100% | 0 / 18 / 0 | 1 / 3 docs | 18 / 18 |
| preferredCriterion | 6 / 6 (6 items) | 100% | 0 / 6 / 0 | 2 / 3 docs | 6 / 6 |
| technicalCompetency | 6 / 6 (19 items) | 100% | 0 / 19 / 0 | 1 / 3 docs | 14 / 19 |
| behaviouralCompetency | 6 / 6 (19 items) | 100% | 0 / 19 / 0 | 1 / 3 docs | 19 / 19 |
| reportsToTitle | 4 / 6 | 100% | 0 / 4 / 0 | 2 / 2 docs | 6 / 6 |
| directReportTitle | 0 / 6 (0 items) | — | — | — | 6 / 6 |
| teamSize | 0 / 6 | — | — | — | 6 / 6 |
| noticePeriod | 0 / 6 | — | — | — | 6 / 6 |

| Doc · run | Technical weights | Total | Balanced | Behavioural weights | Total | Balanced |
|---|---|---|---|---|---|---|
| 1 · 1 | [40, 30, 15, 15] | 100 | [40, 30, 15, 15] | [30, 25, 25, 20] | 100 | [30, 25, 25, 20] |
| 1 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 3 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 3 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 4 · 1 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |
| 4 · 2 | [40, 30, 30] | 100 | [40, 30, 30] | [40, 30, 30] | 100 | [40, 30, 30] |

**Rule checks:** none failed.
- unreadable by the app: document 2: That document has more than 40000 characters. Shorten it to the role description and attach it again.
