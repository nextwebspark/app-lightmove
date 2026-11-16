package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.dataimport.model.ParsedSheet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** The LinkedIn profiles and company pages a sheet names, read exactly as the importer stores them. */
final class LinkedInLinks {

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

    private static Set<String> slugsIn(ParsedSheet sheet, int columnIndex, Function<String, String> slugOf) {
        Set<String> slugs = new HashSet<>();
        if (columnIndex < 0) {
            return slugs;
        }
        for (List<String> row : sheet.rows()) {
            String slug = slugOf.apply(sheet.cell(row, columnIndex));
            if (slug != null) {
                slugs.add(slug);
            }
        }
        return slugs;
    }
}
