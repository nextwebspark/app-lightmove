package app.lightmove.api.assistant.tool;

import java.util.List;

/** What a company name turned out to mean: one company, several to choose between, or none found. */
public record CompanyCandidates(Match matches, List<CompanyProfile> candidates) {

    public enum Match { ONE, SEVERAL, NONE }

    static CompanyCandidates of(List<CompanyProfile> candidates) {
        Match matches = switch (candidates.size()) {
            case 0 -> Match.NONE;
            case 1 -> Match.ONE;
            default -> Match.SEVERAL;
        };
        return new CompanyCandidates(matches, candidates);
    }
}
