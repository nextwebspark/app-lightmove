package app.lightmove.api.position.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.llm.service.TextPseudonymiser;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
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
 * Redacts a position description before its text reaches the model.
 *
 * <p>Pseudonymisation, not de-identification: the client's known name and domain are substituted, and
 * contact cards are stripped whole. No other name is detected — a name detector would redact the Title
 * Case headings being extracted — so <b>an unnamed third party's name in prose will reach Vertex</b>, a
 * deliberate trade.
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
     * At least eight digits separated by dashes, spaces or parentheses (never a dot, too easily a
     * decimal), not preceded by a currency code or symbol.
     *
     * <p>The currency exclusion is load-bearing — without it "AED 1,200,000" becomes a phone
     * placeholder — and lives in the one pattern both {@link #isContactLine} and the redaction use, so
     * the two cannot drift apart as they once did.
     */
    private static final Pattern PHONE_CANDIDATE = Pattern.compile(
            "(?<!(?i:AED|USD|SAR|QAR|KWD|GBP|EUR)\\s{0,4})(?<![$€£]\\s{0,4})\\+?\\d[\\d\\-\\s()]{6,}\\d");

    private static final int CONTACT_MARGIN_LINES = 2;

    /** A contacts card is a handful of lines; the cap stops a stray email sweeping a long section. */
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

        // URL before PHONE: PHONE must not consume digits out of a URL not yet redacted.
        LinkedHashMap<String, Pattern> patterns = new LinkedHashMap<>();
        if (settings.redactContactDetails()) {
            patterns.put(EMAIL_LABEL, EMAIL_PATTERN);
            patterns.put(URL_LABEL, URL_PATTERN);
            patterns.put(PHONE_LABEL, PHONE_CANDIDATE);
        }

        return pseudonymiser.redact(stripped, terms, patterns);
    }

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
     * Deletes each blank-line-bounded block of at most {@value #CONTACT_BLOCK_MAX_LINES} lines holding
     * an email or phone, plus {@value #CONTACT_MARGIN_LINES} lines either side — removing a contact's
     * name and title with no name detection. A longer block is swept only around each match.
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
