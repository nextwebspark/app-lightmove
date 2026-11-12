package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionReason;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.Suggestion;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Judges and ranks the people one company's search found, by fixed points (docs/find-executives.md) — no model.
 * Someone whose current title holds the role ({@link SeatFit}) or whom a free check confirmed fits, and is scored
 * out of 100, each point a reason code; someone the live index found under a title that does not hold the role is
 * listed after them with the reason, and so is someone with no title on record that no check reached. The rest of
 * what the profile index alone returned — the function's whole staff, most untitled — is not listed: a title below
 * the role, or a free check that found no such title, says they are not it. The eval tunes the points.
 */
final class SuggestionJudge {

    private static final int ROLE_WORD_POINTS = 40;
    private static final int SEARCH_WORD_POINTS = 30;
    private static final int CONFIRMED_TITLE_POINTS = 30;
    private static final List<Integer> LEVEL_POINTS_BY_DISTANCE = List.of(30, 18, 6);
    private static final int ROLE_COUNTRY_POINTS = 10;
    private static final int REGION_POINTS = 6;
    private static final int UNKNOWN_HOME_POINTS = 3;
    private static final int DATED_ROLE_POINTS = 8;
    private static final int INDEX_ROLE_POINTS = 3;
    private static final int HEADLINE_ROLE_POINTS = 2;
    private static final int BOTH_INDEXES_POINTS = 5;
    private static final int SETTLED_POINTS = 3;
    private static final int SETTLED_MONTHS = 24;
    private static final int NEW_MONTHS = 12;

    private final SeatFit seat;
    private final SearchWords words;
    private final TitleLevel seatLevel;
    private final List<String> roleWords;
    private final String roleCountry;
    private final List<String> searchedCountries;
    private final YearMonth today;

    private SuggestionJudge(SeatFit seat, SearchWords words, TitleLevel seatLevel, List<String> roleWords,
                            String roleCountry, List<String> searchedCountries, YearMonth today) {
        this.seat = seat;
        this.words = words;
        this.seatLevel = seatLevel;
        this.roleWords = roleWords;
        this.roleCountry = roleCountry;
        this.searchedCountries = searchedCountries;
        this.today = today;
    }

    /** {@code searchedCountries} are where the search looked; a role word is the role title's own, or its abbreviation. */
    static SuggestionJudge of(SourcingBrief brief, SearchWords words, List<String> searchedCountries, YearMonth today) {
        SearchWords ownWords = SearchWords.defaultFor(brief.roleTitle(), brief.seniority());
        List<String> roleWords = Stream.concat(ownWords.standaloneTitles().stream(), ownWords.functionTerms().stream())
                .toList();
        return new SuggestionJudge(SeatFit.of(brief, words), words, TitleLevel.ofSeat(brief.seniority()), roleWords,
                brief.countryCode(), searchedCountries, today);
    }

    /** Those who fit first, best score first; then those with no title; then those who do not fit — each in found order. */
    List<Suggestion> judge(List<FoundPerson> found) {
        List<Judged> judged = IntStream.range(0, found.size())
                .mapToObj(order -> judgeOne(found.get(order), order))
                .filter(SuggestionJudge::isListed)
                .sorted(Comparator.comparingInt((Judged one) -> one.verdict().ordinal())
                        .thenComparing(one -> one.score() == null ? 0 : -one.score())
                        .thenComparingInt(Judged::order))
                .toList();
        return IntStream.range(0, judged.size())
                .mapToObj(rank -> judged.get(rank).asSuggestion(rank + 1))
                .toList();
    }

    private static boolean isListed(Judged one) {
        return one.found().foundBy() != FoundBy.INDEX || one.verdict() == SuggestionVerdict.FITS
                || one.found().check() == TitleCheck.UNCHECKED;
    }

    private Judged judgeOne(FoundPerson found, int order) {
        if (found.check() == TitleCheck.CONFIRMED) {
            return scored(found, order, List.of(new Points(CONFIRMED_TITLE_POINTS,
                    SuggestionReason.TITLE_CONFIRMED.code())));
        }
        String title = CurrentRole.of(found.person());
        if (!found.hasTitle() || title.isBlank()) {
            return new Judged(found, order, SuggestionVerdict.NO_TITLE, null, List.of());
        }
        Optional<String> whyNot = seat.whyNot(title);
        if (whyNot.isPresent()) {
            return new Judged(found, order, SuggestionVerdict.NOT_FIT, null, List.of(whyNot.get()));
        }
        List<Points> points = new ArrayList<>();
        points.add(titleWordPoints(title));
        TitleLevel level = CurrentRole.levelOf(title);
        int distance = level.distanceTo(seatLevel);
        if (level != TitleLevel.NONE) {
            points.add(new Points(distance < LEVEL_POINTS_BY_DISTANCE.size() ? LEVEL_POINTS_BY_DISTANCE.get(distance) : 0,
                    SuggestionReason.LEVEL.with(level.name())));
        }
        points.add(new Points(roleReadPoints(found), null));
        tenurePoints(found.person()).ifPresent(points::add);
        return scored(found, order, points);
    }

    private Judged scored(FoundPerson found, int order, List<Points> earned) {
        List<Points> points = new ArrayList<>(earned);
        points.add(homePoints(found.person()));
        if (found.foundBy() == FoundBy.BOTH) {
            points.add(new Points(BOTH_INDEXES_POINTS, SuggestionReason.FOUND_BY_BOTH.code()));
        }
        int score = Math.min(100, points.stream().mapToInt(Points::points).sum());
        List<String> reasons = points.stream()
                .filter(earnedPoints -> earnedPoints.reason() != null)
                .sorted(Comparator.comparingInt(Points::points).reversed())
                .map(Points::reason)
                .toList();
        return new Judged(found, order, SuggestionVerdict.FITS, score, reasons);
    }

    private Points titleWordPoints(String title) {
        Optional<String> roleWord = roleWords.stream()
                .filter(word -> TitleWords.containsAny(title, List.of(word)))
                .findFirst();
        if (roleWord.isPresent()) {
            return new Points(ROLE_WORD_POINTS, SuggestionReason.ROLE_WORD.with(roleWord.get()));
        }
        return Stream.concat(words.standaloneTitles().stream(), words.functionTerms().stream())
                .filter(word -> TitleWords.containsAny(title, List.of(word)))
                .findFirst()
                .map(word -> new Points(SEARCH_WORD_POINTS, SuggestionReason.SEARCH_WORD.with(word)))
                .orElse(new Points(0, null));
    }

    private Points homePoints(BrightDataPerson person) {
        String home = person.countryCode() == null ? null : person.countryCode().toUpperCase(Locale.ROOT);
        if (home == null) {
            return new Points(UNKNOWN_HOME_POINTS, null);
        }
        if (home.equals(roleCountry)) {
            return new Points(ROLE_COUNTRY_POINTS, SuggestionReason.LIVES_IN_ROLE_COUNTRY.with(home));
        }
        if (searchedCountries.contains(home)) {
            return new Points(REGION_POINTS, SuggestionReason.LIVES_IN_REGION.with(home));
        }
        return new Points(0, searchedCountries.isEmpty() ? null : SuggestionReason.LIVES_ELSEWHERE.with(home));
    }

    /** How the current role was read: from a dated role, from the profile index alone, or from a headline. */
    private static int roleReadPoints(FoundPerson found) {
        if (found.foundBy() == FoundBy.INDEX) {
            return INDEX_ROLE_POINTS;
        }
        return CurrentRole.startOf(found.person()).isPresent() ? DATED_ROLE_POINTS : HEADLINE_ROLE_POINTS;
    }

    private Optional<Points> tenurePoints(BrightDataPerson person) {
        OptionalInt months = CurrentRole.startOf(person)
                .map(start -> OptionalInt.of((int) start.until(today, ChronoUnit.MONTHS)))
                .orElse(OptionalInt.empty());
        if (months.isEmpty() || months.getAsInt() < 0) {
            return Optional.empty();
        }
        if (months.getAsInt() < NEW_MONTHS) {
            return Optional.of(new Points(0, SuggestionReason.NEW_IN_ROLE.with(Integer.toString(months.getAsInt()))));
        }
        return months.getAsInt() >= SETTLED_MONTHS ? Optional.of(new Points(SETTLED_POINTS, null)) : Optional.empty();
    }

    /** Points earned, and the reason code they show as — null where they show as none. */
    private record Points(int points, String reason) {}

    private record Judged(FoundPerson found, int order, SuggestionVerdict verdict, Integer score,
                          List<String> reasons) {

        Suggestion asSuggestion(int rank) {
            return new Suggestion(found.slug(), verdict, score, rank, reasons, found.foundBy());
        }
    }
}
