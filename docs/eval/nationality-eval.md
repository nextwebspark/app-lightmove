# Nationality and seniority eval

Written by `NationalityEval` (`apps/api/src/test/java/app/lightmove/api/eval`). Every run appends one
section. It holds counts only: no name, and no line of anybody's profile.

- **baseline**: the single AI enrichment call as it shipped before #563. It always answers, states no
  confidence and proposes no seniority.
- **classifier**: the dedicated nationality prompt (`candidate-nationality-system.st`) and the
  assessment call's seniority.

The gates are **precision at `high` ≥ 95%**, because `high` is what fills the field, and **no GCC
false positives**. They are judged on the private golden set of about 100 human-confirmed real
profiles, which never enters the repository. The synthetic fixtures are twelve invented rubric cases:
a smoke test, not a measurement. Four of them (syn-02, 04, 06 and 07) restate the prompt's own worked
examples, so the classifier's synthetic score is an upper bound.

## baseline — 2026-09-27

Golden set: synthetic fixtures, 12 rows labelled for nationality (label sources: known 12).

| Metric | Value | Target |
|---|---|---|
| Accuracy | 33.3% | |
| Precision at `high` | n/a — states no confidence | ≥ 95% |
| GCC false positives | 0 of 11 (0.0%) | 0 |
| Unknown rate | 50.0% | |
| Coverage (high or medium) | n/a | |
| Seniority | n/a — proposes none | |

| Category | Support | Predicted | Precision | Recall |
|---|---|---|---|---|
| Emirati | 1 | 0 | n/a | 0.0% |
| Western expat | 3 | 2 | 100.0% | 66.7% |
| South Asian | 1 | 2 | 0.0% | 0.0% |
| Asian | 2 | 0 | n/a | 0.0% |
| Arab expat, non-GCC | 1 | 2 | 0.0% | 0.0% |
| Other expat | 2 | 0 | n/a | 0.0% |
| Unknown | 2 | 6 | 33.3% | 100.0% |

Confusion (rows expected, columns predicted):

| | Emirati | Western expat | South Asian | Asian | Arab expat, non-GCC | Other expat | Unknown |
|---|---|---|---|---|---|---|---|
| Emirati | · | · | · | · | · | · | 1 |
| Western expat | · | 2 | · | · | · | · | 1 |
| South Asian | · | · | · | · | · | · | 1 |
| Asian | · | · | 2 | · | · | · | · |
| Arab expat, non-GCC | · | · | · | · | · | · | 1 |
| Other expat | · | · | · | · | 2 | · | · |
| Unknown | · | · | · | · | · | · | 2 |

## classifier — 2026-09-27

Golden set: synthetic fixtures, 12 rows labelled for nationality (label sources: known 12).

| Metric | Value | Target |
|---|---|---|
| Accuracy | 100.0% | |
| Precision at `high` | 100.0% (9 answers) | ≥ 95% |
| GCC false positives | 0 of 11 (0.0%) | 0 |
| Unknown rate | 16.7% | |
| Coverage (high or medium) | 83.3% | |
| Seniority exact | 91.7% (12 rows) | |
| Seniority within one level | 100.0% | |

| Category | Support | Predicted | Precision | Recall |
|---|---|---|---|---|
| Emirati | 1 | 1 | 100.0% | 100.0% |
| Western expat | 3 | 3 | 100.0% | 100.0% |
| South Asian | 1 | 1 | 100.0% | 100.0% |
| Asian | 2 | 2 | 100.0% | 100.0% |
| Arab expat, non-GCC | 1 | 1 | 100.0% | 100.0% |
| Other expat | 2 | 2 | 100.0% | 100.0% |
| Unknown | 2 | 2 | 100.0% | 100.0% |

Confusion (rows expected, columns predicted):

| | Emirati | Western expat | South Asian | Asian | Arab expat, non-GCC | Other expat | Unknown |
|---|---|---|---|---|---|---|---|
| Emirati | 1 | · | · | · | · | · | · |
| Western expat | · | 3 | · | · | · | · | · |
| South Asian | · | · | 1 | · | · | · | · |
| Asian | · | · | · | 2 | · | · | · |
| Arab expat, non-GCC | · | · | · | · | 1 | · | · |
| Other expat | · | · | · | · | · | 2 | · |
| Unknown | · | · | · | · | · | · | 2 |

