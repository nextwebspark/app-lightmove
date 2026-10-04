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
 * Matches headers to fields without a model — the seed for the model's request and the fallback when
 * it is unreachable. A header matching nothing becomes a custom column, never dropped.
 */
@Service
public class HeuristicColumnMatcher {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    /** Carry no signal about which field a header means, and drown the words that do. */
    private static final Set<String> NOISE_WORDS =
            Set.of("the", "of", "a", "an", "s", "info", "information", "detail", "details", "field", "value");

    /** Below this, a token overlap is a coincidence rather than a match. */
    private static final double MINIMUM_OVERLAP = 0.6;

    private static final Map<String, ImportTargetField> BY_SYNONYM = indexSynonyms();

    /** {@code existingColumns} is consulted first, so a re-import fills columns rather than re-creating them. */
    public HeuristicProposal propose(ParsedSheet sheet, List<CustomColumnDto> existingColumns) {
        Set<ImportTargetField> claimed = new LinkedHashSet<>();
        List<ColumnMapping> mappings = new ArrayList<>(sheet.columns().size());
        boolean everyColumnCertain = true;
        for (SheetColumn column : sheet.columns()) {
            Optional<HeaderMatch> matched = match(column.header());
            // "Email" and "Work Email" must not both claim one field; the loser becomes a custom column.
            if (matched.isPresent() && claimed.add(matched.get().field())) {
                mappings.add(ColumnMapping.onto(column.index(), column.header(), matched.get().field()));
                everyColumnCertain &= matched.get().certain();
                continue;
            }
            ColumnMapping custom = asCustomColumn(column, existingColumns);
            mappings.add(custom);
            // Filling an existing column is certain; minting a new one is not.
            everyColumnCertain &= custom.customFieldKey() != null;
        }
        return new HeuristicProposal(mappings, everyColumnCertain);
    }

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

    /** Defaults to the person, whom an unlabelled column is usually about; the mapping step shows the guess. */
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
            case EMAIL, URL, SHORT_TEXT, LONG_TEXT, BLANK -> CustomColumnType.TEXT;
        };
    }

    /** Token overlap, not edit distance, which rates "Bonus" near "Bonds" but "Company" far from "Company Name". */
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
        // The larger side, or "Company" would perfectly match "Company Registration Number".
        return (double) shared / Math.max(headerTokens.size(), synonymTokens.size());
    }

    private static Set<String> tokensOf(String normalised) {
        return Arrays.stream(normalised.split(" "))
                .filter(token -> !token.isBlank() && !NOISE_WORDS.contains(token))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Lower-case words, single-spaced, accents folded. */
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
                // putIfAbsent: declaration order decides a synonym listed under two fields.
                index.putIfAbsent(normalise(synonym), field);
            }
        }
        return Map.copyOf(index);
    }
}
