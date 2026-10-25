package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.common.constant.NoticePeriod;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.model.CallSuggestion;
import app.lightmove.api.notetaker.model.SuggestedValue;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * One model call reading a recorded call's transcript against what the person's record holds, and the code that
 * decides which of its answers become suggestions. The model proposes; this class keeps only what it can check — a
 * notice period that folds onto one of the five, a package in a real currency, a country the catalog knows — and drops
 * anything equal to what is on file, so a consultant is never asked to apply a change that changes nothing.
 */
@Component
class CallChangeSuggester {

    private static final String PROMPT_ID = "call-changes";
    private static final String BLOCKED = "{\"guard\":\"" + BlockedAnswer.MARKER + "\"}";
    private static final int LONGEST_TEXT = 600;
    private static final int LONGEST_QUOTE = 400;
    private static final int LONGEST_TASK = 120;
    private static final int MOST_TASKS = 3;
    private static final Pattern CALL_TIME = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?");
    private static final Set<String> CURRENCIES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    private final StructuredPrompt prompt;

    CallChangeSuggester(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /**
     * Throws {@link ApiException} {@code VALIDATION_FAILED} when the guard stopped the transcript and
     * {@code AI_UNAVAILABLE} when the model answered nothing; any failure of the model call itself propagates for the
     * caller to answer as unavailable.
     */
    List<CallSuggestion> suggest(List<TranscriptLine> transcript, Instant callStart, CallHeldFields held) {
        CallChangesAnswer answer = prompt.ask(CallChangesAnswer.class, user -> user.text("""
                WHAT THE RECORD HOLDS NOW
                {held}

                THE TRANSCRIPT (time into the call, speaker, words)
                {transcript}
                """)
                .param("held", heldText(held))
                .param("transcript", MeetingSummariser.transcriptText(transcript, callStart)));
        if (answer == null) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        if (BlockedAnswer.matches(answer.guard())) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Something said on this call reads like an instruction to the AI, so it was not read. "
                            + "Apply any changes by hand from the transcript.");
        }
        return suggestionsOf(answer, held);
    }

    static List<CallSuggestion> suggestionsOf(CallChangesAnswer answer, CallHeldFields held) {
        List<CallSuggestion> found = new ArrayList<>();
        noticeOf(answer.notice(), held).ifPresent(found::add);
        payOf(answer.currentPackage(), held).ifPresent(found::add);
        placeOf(answer.location(), held).ifPresent(found::add);
        relocationOf(answer.relocation(), held).ifPresent(found::add);
        wordsOf(CallSuggestionKind.MOTIVATION, answer.motivation(), held).ifPresent(found::add);
        wordsOf(CallSuggestionKind.EXPECTED_PACKAGE, answer.expectedPackage(), held)
                .ifPresent(found::add);
        found.addAll(tasksOf(answer.nextSteps()));
        return List.copyOf(found);
    }

    private static Optional<CallSuggestion> noticeOf(CallChangesAnswer.Notice notice, CallHeldFields held) {
        if (notice == null || notice.count() == null || notice.count() < 0) {
            return Optional.empty();
        }
        NoticeUnit unit = unitOf(notice.unit());
        NoticePeriod period = notice.count() == 0 ? NoticePeriod.NONE : NoticePeriod.ofPair(notice.count(), unit);
        if (period == null || period.value().equalsIgnoreCase(Objects.toString(held.noticePeriod(), ""))) {
            return Optional.empty();
        }
        return Optional.of(CallSuggestion.pending(CallSuggestionKind.NOTICE_PERIOD,
                SuggestedValue.text(period.value()), quote(notice.said()), timeOf(notice.at())));
    }

    private static Optional<CallSuggestion> payOf(CallChangesAnswer.Pay pay, CallHeldFields held) {
        if (pay == null || pay.currency() == null) {
            return Optional.empty();
        }
        String currency = pay.currency().strip().toUpperCase(Locale.ROOT);
        if (!CURRENCIES.contains(currency)) {
            return Optional.empty();
        }
        List<String> working = new ArrayList<>();
        Long base = annual(pay.base(), "base", working);
        Long bonus = annual(pay.bonus(), "bonus", working);
        if (bonus == null && pay.bonusMonthsOfBase() != null && pay.bonusMonthsOfBase() > 0 && base != null
                && pay.bonusMonthsOfBase() <= 36) {
            bonus = Math.round(base / 12.0 * pay.bonusMonthsOfBase());
            working.add("bonus " + plain(pay.bonusMonthsOfBase()) + " months' base");
        }
        Long allowances = annual(pay.allowances(), "allowances", working);
        boolean sameCurrency = currency.equalsIgnoreCase(Objects.toString(held.currency(), ""));
        if (sameCurrency) {
            base = Objects.equals(base, held.baseSalary()) ? null : base;
            bonus = Objects.equals(bonus, held.bonus()) ? null : bonus;
            allowances = Objects.equals(allowances, held.allowances()) ? null : allowances;
        }
        if (base == null && bonus == null && allowances == null) {
            return Optional.empty();
        }
        if (held.compensation().wouldRelabel(currency, base != null, bonus != null, allowances != null)) {
            // Taking it would relabel a figure held in another currency; the consultant edits that by hand.
            return Optional.empty();
        }
        String note = working.isEmpty() ? null : "Annualised from monthly: " + String.join(" · ", working);
        return Optional.of(CallSuggestion.pending(CallSuggestionKind.CURRENT_PACKAGE,
                SuggestedValue.pay(currency, base, bonus, allowances, note), quote(pay.said()), timeOf(pay.at())));
    }

    private static Optional<CallSuggestion> placeOf(CallChangesAnswer.Place place, CallHeldFields held) {
        if (place == null || place.country() == null) {
            return Optional.empty();
        }
        var country = Countries.resolveSpelling(place.country().strip());
        if (country.isEmpty()) {
            return Optional.empty();
        }
        String countryName = country.get().name();
        String city = Countries.cityOf(place.city());
        boolean same = countryName.equalsIgnoreCase(Objects.toString(held.country(), ""))
                && Objects.toString(city, "").equalsIgnoreCase(Objects.toString(held.city(), ""));
        if (same || (city == null && countryName.equalsIgnoreCase(Objects.toString(held.country(), "")))) {
            return Optional.empty();
        }
        return Optional.of(CallSuggestion.pending(CallSuggestionKind.LOCATION,
                SuggestedValue.place(city, countryName), quote(place.said()), timeOf(place.at())));
    }

    private static Optional<CallSuggestion> relocationOf(CallChangesAnswer.Relocation relocation,
                                                                   CallHeldFields held) {
        if (relocation == null || !Boolean.TRUE.equals(relocation.open()) || held.relocationTagId() == null
                || held.openToRelocate()) {
            return Optional.empty();
        }
        return Optional.of(CallSuggestion.pending(CallSuggestionKind.OPEN_TO_RELOCATE,
                SuggestedValue.text("Open to relocate"), quote(relocation.said()), timeOf(relocation.at())));
    }

    private static Optional<CallSuggestion> wordsOf(CallSuggestionKind kind,
                                                              CallChangesAnswer.Words words, CallHeldFields held) {
        String text = words == null ? null : clip(words.text(), LONGEST_TEXT);
        if (text == null || (held.motivation() != null && held.motivation().contains(text))) {
            return Optional.empty();
        }
        return Optional.of(CallSuggestion.pending(kind, SuggestedValue.text(text), quote(words.said()),
                timeOf(words.at())));
    }

    private static List<CallSuggestion> tasksOf(List<CallChangesAnswer.Step> steps) {
        if (steps == null) {
            return List.of();
        }
        List<CallSuggestion> tasks = new ArrayList<>();
        for (CallChangesAnswer.Step step : steps) {
            if (step == null || !"CONSULTANT".equalsIgnoreCase(Objects.toString(step.owner(), "").strip())) {
                continue;
            }
            String title = clip(step.title() == null ? null : step.title().replaceAll("\\s+", " "), LONGEST_TASK);
            if (title == null || tasks.stream().anyMatch(held -> held.value().text().equalsIgnoreCase(title))) {
                continue;
            }
            tasks.add(CallSuggestion.pending(CallSuggestionKind.TASK,
                    SuggestedValue.text(title), quote(step.said()), timeOf(step.at())));
            if (tasks.size() == MOST_TASKS) {
                break;
            }
        }
        return tasks;
    }

    /** A figure as a year's worth in whole units; one stated per month is multiplied and the working kept. */
    private static Long annual(CallChangesAnswer.Figure figure, String label, List<String> working) {
        if (figure == null || figure.amount() == null || !Double.isFinite(figure.amount()) || figure.amount() <= 0
                || figure.per() == null) {
            return null;
        }
        String per = figure.per().strip().toUpperCase(Locale.ROOT);
        if (per.startsWith("MONTH")) {
            double yearly = figure.amount() * 12;
            if (yearly >= SuggestedValue.LARGEST_FIGURE) {
                return null;
            }
            working.add(label + " " + plain(figure.amount()) + " × 12");
            return Math.round(yearly);
        }
        if (per.startsWith("YEAR") || per.startsWith("ANNUAL")) {
            return figure.amount() >= SuggestedValue.LARGEST_FIGURE ? null : Math.round(figure.amount());
        }
        return null;
    }

    private static NoticeUnit unitOf(String unit) {
        if (unit == null) {
            return null;
        }
        String spelled = unit.strip().toUpperCase(Locale.ROOT);
        if (spelled.startsWith("MONTH")) {
            return NoticeUnit.MONTHS;
        }
        if (spelled.startsWith("WEEK")) {
            return NoticeUnit.WEEKS;
        }
        return spelled.startsWith("DAY") ? NoticeUnit.DAYS : null;
    }

    /**
     * What the model is told the record holds. Never the package: the model sees no compensation on file (the
     * `CandidateDossier` rule), so a stated package is compared with the held one in code, in {@link #payOf}.
     */
    private static String heldText(CallHeldFields held) {
        return """
                Notice period: %s
                Lives in: %s
                Tagged open to relocate: %s
                Motivation on file: %s""".formatted(orNone(held.noticePeriod()),
                orNone(List.of(Objects.toString(held.city(), ""), Objects.toString(held.country(), "")).stream()
                        .filter(part -> !part.isBlank()).collect(Collectors.joining(", "))),
                held.openToRelocate() ? "yes" : "no", orNone(clip(held.motivation(), LONGEST_TEXT)));
    }

    private static String plain(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "nothing" : value;
    }

    private static String quote(String said) {
        return clip(said == null ? null : said.replaceAll("\\s+", " "), LONGEST_QUOTE);
    }

    private static String timeOf(String at) {
        return at != null && CALL_TIME.matcher(at.strip()).matches() ? at.strip() : null;
    }

    private static String clip(String value, int longest) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= longest ? stripped : stripped.substring(0, longest - 1) + "…";
    }
}
