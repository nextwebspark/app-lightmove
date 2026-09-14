package app.lightmove.api.position.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.llm.service.TextPseudonymiser;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.repository.ClientRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * The vocabulary a position description is redacted against before its text ever reaches the model.
 *
 * <p><b>Pseudonymisation of contactable identifiers plus known-token substitution of the client, not
 * de-identification.</b> The highest-value piece needs no detection at all — the mandate already
 * knows its client's registered name and domain, so that is known-token substitution. For contact
 * details the strategy is a contact-block stripper, not name detection: this document reads the
 * client from {@link ClientRepository} via the same {@code findByIdAndWorkspaceId} edge
 * {@link PositionBriefLoader} already uses, exactly as it declines to detect any other name — a
 * capitalised-bigram detector in a document full of Title Case headings ("Chief Financial Officer")
 * would redact the very content being extracted. <b>An unnamed third party's name in prose will reach
 * Vertex</b> — a deliberate, stated trade, not an oversight.
 */
@Service
public class PositionDocumentRedactor {

    private static final String COMPANY_LABEL = "COMPANY";
    private static final String EMAIL_LABEL = "EMAIL";
    private static final String PHONE_LABEL = "PHONE";
    private static final String URL_LABEL = "URL";

    private static final List<String> CORPORATE_SUFFIXES = List.of(
            "L.L.C.", "LLC", "PJSC", "Holdings", "Holding", "Corporation", "Corp.", "Corp",
            "Company", "Ltd.", "Ltd", "Inc.", "Inc", "Co.", "Co", "Group");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");

    /**
     * A run of digits with the separators a genuine phone number is written with — dashes, spaces or
     * parentheses, never a dot (too easily a decimal figure or a version string) — at least eight
     * digits in total, so an ordinary short number in prose ("3 direct reports") is never a candidate,
     * and not immediately preceded by a currency code or symbol.
     *
     * <p>The currency exclusion is load-bearing and baked directly into the pattern rather than
     * checked afterwards: without it, "AED 1,200,000" becomes a phone placeholder and step four loses
     * the only figure it wanted — none of the four sample documents states compensation any other way.
     * One pattern used both to decide whether a line is contact-bearing ({@link #isContactLine}) and,
     * directly, to redact the match — so the two can never drift apart the way the currency guard once
     * did, when it was checked in one place and not the other.
     */
    private static final Pattern PHONE_CANDIDATE = Pattern.compile(
            "(?<!(?i:AED|USD|SAR|QAR|KWD|GBP|EUR)\\s{0,4})(?<![$€£]\\s{0,4})\\+?\\d[\\d\\-\\s()]{6,}\\d");

    /** Lines swept on either side of the contact-bearing block itself. */
    private static final int CONTACT_MARGIN_LINES = 2;

    /**
     * The largest contiguous non-blank block dropped wholesale once any line inside it looks like
     * contact detail — a contacts card is a handful of lines (name, title, firm, mobile, email), and
     * capping this is what stops a stray email at the end of a genuinely long, blank-line-free
     * section from sweeping the whole thing.
     */
    private static final int CONTACT_BLOCK_MAX_LINES = 6;

    private final TextPseudonymiser pseudonymiser;
    private final ClientRepository clients;
    private final PositionExtractionSettings settings;

    public PositionDocumentRedactor(TextPseudonymiser pseudonymiser, ClientRepository clients,
                                    LightMoveProperties properties) {
        this.pseudonymiser = pseudonymiser;
        this.clients = clients;
        this.settings = properties.position().extraction();
    }

    public Redaction redact(String documentText, UUID clientId, UUID workspaceId) {
        String stripped = settings.redactContactDetails()
                ? stripContactBlocks(documentText)
                : documentText;

        LinkedHashMap<String, List<String>> terms = new LinkedHashMap<>();
        if (settings.redactKnownCompanyNames()) {
            terms.put(COMPANY_LABEL, companyTermsOf(clientId, workspaceId));
        }

        // Explicit, deterministic order — URL before PHONE matters: PHONE must not consume digits out
        // of a URL that has not been redacted yet.
        LinkedHashMap<String, Pattern> patterns = new LinkedHashMap<>();
        if (settings.redactContactDetails()) {
            patterns.put(EMAIL_LABEL, EMAIL_PATTERN);
            patterns.put(URL_LABEL, URL_PATTERN);
            patterns.put(PHONE_LABEL, PHONE_CANDIDATE);
        }

        return pseudonymiser.redact(stripped, terms, patterns);
    }

    /** The client's registered name, its common-suffix variants, and its domain. */
    private List<String> companyTermsOf(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .map(PositionDocumentRedactor::variantsOf)
                .orElse(List.of());
    }

    private static List<String> variantsOf(Client client) {
        Set<String> variants = new LinkedHashSet<>();
        String name = client.getName().trim();
        variants.add(name);
        for (String suffix : CORPORATE_SUFFIXES) {
            if (name.length() > suffix.length()
                    && name.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                String withoutSuffix = name.substring(0, name.length() - suffix.length()).trim();
                if (!withoutSuffix.isEmpty()) {
                    variants.add(withoutSuffix);
                }
            }
        }
        if (client.getDomain() != null && !client.getDomain().isBlank()) {
            variants.add(client.getDomain().trim());
        }
        return List.copyOf(variants);
    }

    /**
     * Deletes a whole contacts card — a contiguous, blank-line-bounded block of at most
     * {@value #CONTACT_BLOCK_MAX_LINES} lines containing an email or a genuine phone number — plus
     * {@value #CONTACT_MARGIN_LINES} lines either side of the block. This is what removes a
     * consultant's name and title along with their mobile and email, with no name detection at all:
     * the block is bounded by the blank lines a contacts card is naturally laid out between, not by
     * scanning outward from the matching line alone.
     *
     * <p>A block longer than the cap is swept only line-by-line around each match instead of
     * wholesale — the cap exists so one stray email at the end of a long, blank-line-free section
     * cannot sweep the whole section with it.
     */
    private String stripContactBlocks(String text) {
        String[] lines = text.split("\n", -1);
        boolean[] drop = new boolean[lines.length];
        int i = 0;
        while (i < lines.length) {
            if (lines[i].isBlank()) {
                i++;
                continue;
            }
            int blockStart = i;
            while (i < lines.length && !lines[i].isBlank()) {
                i++;
            }
            int blockEnd = i - 1;
            markBlockIfContactBearing(lines, blockStart, blockEnd, drop);
        }
        List<String> kept = new ArrayList<>();
        for (int j = 0; j < lines.length; j++) {
            if (!drop[j]) {
                kept.add(lines[j]);
            }
        }
        return String.join("\n", kept);
    }

    private static void markBlockIfContactBearing(String[] lines, int blockStart, int blockEnd,
                                                   boolean[] drop) {
        if (blockEnd - blockStart + 1 <= CONTACT_BLOCK_MAX_LINES) {
            boolean contactBearing = false;
            for (int j = blockStart; j <= blockEnd; j++) {
                if (isContactLine(lines[j])) {
                    contactBearing = true;
                    break;
                }
            }
            if (contactBearing) {
                markDropped(drop, Math.max(0, blockStart - CONTACT_MARGIN_LINES),
                        Math.min(lines.length - 1, blockEnd + CONTACT_MARGIN_LINES));
            }
            return;
        }
        for (int j = blockStart; j <= blockEnd; j++) {
            if (isContactLine(lines[j])) {
                markDropped(drop, Math.max(blockStart, j - CONTACT_MARGIN_LINES),
                        Math.min(blockEnd, j + CONTACT_MARGIN_LINES));
            }
        }
    }

    private static void markDropped(boolean[] drop, int from, int to) {
        for (int j = from; j <= to; j++) {
            drop[j] = true;
        }
    }

    private static boolean isContactLine(String line) {
        return EMAIL_PATTERN.matcher(line).find() || PHONE_CANDIDATE.matcher(line).find();
    }
}
