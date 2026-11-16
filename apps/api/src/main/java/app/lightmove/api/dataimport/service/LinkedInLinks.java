package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.dataimport.model.ParsedSheet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** The LinkedIn profiles and company pages a sheet names, read exactly as the importer stores them. */
final class LinkedInLinks {

    private static final String LINKEDIN = "linkedin";

    private LinkedInLinks() {
    }

    static String profileSlugOf(String cell) {
        return LinkedInUrls.profileSlugOrNull(RowValues.linkedinProfile(cell));
    }

    static String companySlugOf(String cell) {
        return LinkedInUrls.companySlugOrNull(RowValues.linkedinCompany(cell));
    }

    static Set<String> profileSlugsIn(ParsedSheet sheet, int columnIndex) {
        return slugsIn(sheet, columnIndex, LinkedInLinks::profileSlugOf);
    }

    static Set<String> companySlugsIn(ParsedSheet sheet, int columnIndex) {
        return slugsIn(sheet, columnIndex, LinkedInLinks::companySlugOf);
    }

    /** Both counts in one pass over the column: the preview reads every column of up to 5,000 rows. */
    static ColumnLinkCounts countsIn(ParsedSheet sheet, int columnIndex) {
        Set<String> profiles = new HashSet<>();
        Set<String> companies = new HashSet<>();
        for (List<String> row : sheet.rows()) {
            String cell = sheet.cell(row, columnIndex);
            if (!mentionsLinkedIn(cell)) {
                continue;
            }
            String profile = profileSlugOf(cell);
            if (profile != null) {
                profiles.add(profile);
            } else {
                String company = companySlugOf(cell);
                if (company != null) {
                    companies.add(company);
                }
            }
        }
        return new ColumnLinkCounts(profiles.size(), companies.size());
    }

    record ColumnLinkCounts(int profiles, int companies) {}

    private static Set<String> slugsIn(ParsedSheet sheet, int columnIndex, Function<String, String> slugOf) {
        Set<String> slugs = new HashSet<>();
        if (columnIndex < 0) {
            return slugs;
        }
        for (List<String> row : sheet.rows()) {
            String cell = sheet.cell(row, columnIndex);
            // A substring check before any URL parse: nearly every column is names, titles or numbers, and a preview
            // reads every column of up to 5,000 rows.
            String slug = mentionsLinkedIn(cell) ? slugOf.apply(cell) : null;
            if (slug != null) {
                slugs.add(slug);
            }
        }
        return slugs;
    }

    private static boolean mentionsLinkedIn(String cell) {
        if (cell == null) {
            return false;
        }
        for (int at = 0; at + LINKEDIN.length() <= cell.length(); at++) {
            if (cell.regionMatches(true, at, LINKEDIN, 0, LINKEDIN.length())) {
                return true;
            }
        }
        return false;
    }
}
