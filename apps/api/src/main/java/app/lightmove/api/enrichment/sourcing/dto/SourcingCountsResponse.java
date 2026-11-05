package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import java.util.List;
import java.util.UUID;

/**
 * What a search would reach, counted free before anything is spent: per company the people whose current title
 * holds the words, those already on file, and the profiles a search would buy; per level and per suggested word the
 * people they reach across the companies. {@code counted} is false where no live index can count, and every count
 * then reads zero.
 */
public record SourcingCountsResponse(boolean counted, List<CompanyCount> companies, List<LevelCount> levels,
                                     List<WordCount> words, long match, long onFile, long toBuy) {

    /** {@code searchable} is false for a company with no LinkedIn page, which no search can key on. */
    public record CompanyCount(UUID triageCompanyId, String companyName, boolean searchable, long match, long onFile,
                               long toBuy) {}

    public record LevelCount(SearchLevel level, long count) {}

    public record WordCount(String word, long count) {}
}
