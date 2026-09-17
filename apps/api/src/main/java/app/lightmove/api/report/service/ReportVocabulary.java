package app.lightmove.api.report.service;

import app.lightmove.api.common.constant.Seniority;
import java.util.Arrays;
import java.util.List;

/** The two things every chapter says the same way: the tail's label, and the seniority ladder's tokens. */
final class ReportVocabulary {

    static final String OTHER = "Other";

    private ReportVocabulary() {
    }

    static List<String> levelTokens() {
        return Arrays.stream(Seniority.values()).map(Seniority::value).toList();
    }
}
