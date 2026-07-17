package app.lightmove.api.company.constant;

/** What a row in {@code app_lm_company_taxonomy} was derived from. */
public enum TaxonomyType {
    /** From a distinct {@code app_lm_companies.primary_industry} value. */
    PRIMARY_TYPE,
    /** From one element of a {@code app_lm_companies.industry_tags} array. */
    TAG
}
