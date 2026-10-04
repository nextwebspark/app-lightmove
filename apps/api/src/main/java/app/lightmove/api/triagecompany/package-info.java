/**
 * <b>Triage company — a mandate's decision about a company.</b> One row per decision with a
 * write-time snapshot, never a foreign key: the ETL reloads {@code app_lm_apollo_companies} wholesale.
 * Removing a row removes the decision only. Depends on {@code strategy}, never the reverse.
 */
package app.lightmove.api.triagecompany;
