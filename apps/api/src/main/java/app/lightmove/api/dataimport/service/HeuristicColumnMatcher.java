package app.lightmove.api.dataimport.service;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.HeaderMatch;
import app.lightmove.api.dataimport.model.HeuristicProposal;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.SheetColumn;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Matches a sheet's headers to fields without asking a model anything.
 *
 * <p>Two jobs, and both matter. It <b>seeds</b> the request the model answers, so the model is
 * correcting a first draft rather than starting from a bare list — and it is the <b>fallback</b> when
 * the model cannot be reached at all. That second job is the load-bearing one: Vertex AI needs
 * Application Default Credentials on every path including a plain local run, and an import that became
 * impossible without them would be an import most people never got to use.
 *
 * <p>Three rules in order — exact normalised match, then a synonym, then token overlap — and a header
 * that matches nothing becomes a custom column rather than being dropped, because a column nobody
 * recognised is exactly the column this whole feature exists to keep.
 */
@Service
public class HeuristicColumnMatcher {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    /** Words that carry no signal about which field a header means, and drown the ones that do. */
    private static final Set<String> NOISE_WORDS =
            Set.of("the", "of", "a", "an", "s", "info", "information", "detail", "details", "field", "value");

    /** Below this, a token overlap is a coincidence rather than a match. */
    private static final double MINIMUM_OVERLAP = 0.6;

    /** Every field's synonyms, normalised once at class-load rather than on every header. */
    private static final Map<String, ImportTargetField> BY_SYNONYM = indexSynonyms();

    /**
     * A first mapping for every column of the sheet.
     *
     * <p>{@code existingColumns} is consulted before a new custom column is proposed, so re-importing
     * a file whose extra headers a mandate already has fills those columns instead of asking to make
     * them again.
     */
    public HeuristicProposal propose(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        Set<ImportTargetField> claimed = new LinkedHashSet<>();
        List<ColumnMapping> mappings = new ArrayList<>(sheet.columns().size());
        boolean everyColumnCertain = true;
        for (SheetColumn column : sheet.columns()) {
            Optional<HeaderMatch> matched = match(column.header());
            // A file with "Email" and "Work Email" would otherwise map both onto the same field and
            // let the second silently overwrite the first. The first wins; the loser becomes a custom
            // column, which keeps the data and leaves the correction to the person confirming.
            if (matched.isPresent() && claimed.add(matched.get().field())) {
                mappings.add(ColumnMapping.onto(column.index(), column.header(), matched.get().field()));
                everyColumnCertain &= matched.get().certain();
                continue;
            }
            ColumnMapping custom = asCustomColumn(column, existingColumns);
            mappings.add(custom);
            // Filling a column this project already has is as certain as hitting a known field. Minting
            // a new one is not: an unfamiliar header may be a field held under a name we do not know.
            everyColumnCertain &= custom.customFieldKey() != null;
        }
        return new HeuristicProposal(mappings, everyColumnCertain);
    }

    /** The best built-in field for one header, or empty when nothing matches well enough. */
    public Optional<HeaderMatch> match(String header) {
        String normalised = normalise(header);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        ImportTargetField bySynonym = BY_SYNONYM.get(normalised);
        if (bySynonym != null) {
            return Optional.of(HeaderMatch.certain(bySynonym));
        }
        return bestByOverlap(normalised).map(HeaderMatch::likely);
    }

    /**
     * An unrecognised header becomes a custom column: an existing one when the mandate already has a
     * column by that name, and otherwise a new one typed from what the values look like.
     */
    private ColumnMapping asCustomColumn(SheetColumn column, List<CustomColumnDto> existingColumns) {
        CustomColumnTarget target = guessTarget(column);
        Optional<CustomColumnDto> existing = existingColumns.stream()
                .filter(defined -> defined.label().equalsIgnoreCase(column.header().trim()))
                .findFirst();
        if (existing.isPresent()) {
            CustomColumnDto defined = existing.get();
            return ColumnMapping.intoCustomColumn(column.index(), column.header(),
                    CustomColumnTarget.fromValue(defined.target()), defined.fieldKey(), defined.label(),
                    CustomColumnType.fromValue(defined.dataType()));
        }
        return ColumnMapping.intoCustomColumn(column.index(), column.header(), target, null,
                column.header().trim(), typeFor(column.valueShape()));
    }

    /**
     * Which half of the row an unrecognised column describes.
     *
     * <p><b>The person.</b> A row on this screen is a person at a company, and an unlabelled extra
     * column on such a list is far more often about the individual — a rating, a nationality, a source
     * — than about their employer. Guessing wrong is cheap and visible: the mapping step shows the
     * choice and the user changes it in one click.
     */
    private static CustomColumnTarget guessTarget(SheetColumn column) {
        String normalised = normalise(column.header());
        boolean namesCompany = normalised.startsWith("company")
                || normalised.startsWith("employer")
                || normalised.startsWith("organisation")
                || normalised.startsWith("organization")
                || normalised.startsWith("account");
        return namesCompany ? CustomColumnTarget.COMPANY : CustomColumnTarget.CANDIDATE;
    }

    private static CustomColumnType typeFor(SheetColumn.ValueShape shape) {
        return switch (shape) {
            case NUMBER -> CustomColumnType.NUMBER;
            case DATE -> CustomColumnType.DATE;
            case BOOLEAN -> CustomColumnType.BOOLEAN;
            // An email, a URL and free text are all text. A type that only affected the input's
            // keyboard is not worth a column type nobody can change their mind about cheaply.
            case EMAIL, URL, SHORT_TEXT, LONG_TEXT, BLANK -> CustomColumnType.TEXT;
        };
    }

    /**
     * The field sharing the most tokens with this header, when they share enough to be a match.
     *
     * <p>Overlap rather than edit distance: headers differ by whole words ("Company" vs "Company
     * Name", "Email" vs "Work Email Address"), not by characters, and edit distance scores those two
     * pairs as barely related while scoring "Bonus" against "Bonds" as nearly identical.
     */
    private static Optional<ImportTargetField> bestByOverlap(String normalisedHeader) {
        Set<String> headerTokens = tokensOf(normalisedHeader);
        if (headerTokens.isEmpty()) {
            return Optional.empty();
        }
        ImportTargetField best = null;
        double bestScore = 0;
        for (ImportTargetField field : ImportTargetField.values()) {
            for (String synonym : field.synonyms()) {
                double score = overlap(headerTokens, tokensOf(normalise(synonym)));
                if (score > bestScore) {
                    best = field;
                    bestScore = score;
                }
            }
        }
        return bestScore >= MINIMUM_OVERLAP ? Optional.ofNullable(best) : Optional.empty();
    }

    private static double overlap(Set<String> headerTokens, Set<String> synonymTokens) {
        if (synonymTokens.isEmpty()) {
            return 0;
        }
        long shared = synonymTokens.stream().filter(headerTokens::contains).count();
        // Divided by the larger side, so "Company" does not score a perfect match against
        // "Company Registration Number" just because every token of one appears in the other.
        return (double) shared / Math.max(headerTokens.size(), synonymTokens.size());
    }

    private static Set<String> tokensOf(String normalised) {
        return Arrays.stream(normalised.split(" "))
                .filter(token -> !token.isBlank() && !NOISE_WORDS.contains(token))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * A header reduced to lower-case words separated by single spaces, with accents folded.
     *
     * <p>"E-Mail", "e_mail" and "E Mail" have to reach the same key as "email", because a file writes
     * whichever of them its author's tool produced.
     */
    static String normalise(String header) {
        if (header == null) {
            return "";
        }
        String folded = DIACRITICS
                .matcher(Normalizer.normalize(header, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(folded).replaceAll(" ").trim().replaceAll(" +", " ");
    }

    private static Map<String, ImportTargetField> indexSynonyms() {
        Map<String, ImportTargetField> index = new HashMap<>();
        for (ImportTargetField field : ImportTargetField.values()) {
            for (String synonym : field.synonyms()) {
                // putIfAbsent: the enum's declaration order decides a shared synonym, so a spelling
                // listed under two fields lands on the one a reader meets first rather than on
                // whichever the map happened to write last.
                index.putIfAbsent(normalise(synonym), field);
            }
        }
        return Map.copyOf(index);
    }
}
