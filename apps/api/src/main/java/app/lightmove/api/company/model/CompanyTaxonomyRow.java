package app.lightmove.api.company.model;

import app.lightmove.api.company.constant.TaxonomyType;

/**
 * A distinct {@code primary_industry}/{@code industry_tags} value read straight from
 * {@code app_lm_companies}, before it has an embedding — what {@code TaxonomyBackfillRunner} diffs
 * against the existing {@code app_lm_company_taxonomy} rows to decide what needs (re-)embedding.
 */
public record CompanyTaxonomyRow(TaxonomyType type, String name, int companyCount) {
}
