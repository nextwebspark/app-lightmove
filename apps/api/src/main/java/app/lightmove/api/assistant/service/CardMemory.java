package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposalOutcome;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An earlier card, written back into the chat the model reads, so a follow-up ("shortlist the first
 * three", "more like these") knows what the card held. The answer never lists the card's companies,
 * so without this the model would be asked about companies it cannot see. Everything here is copied
 * from the stored card, never from the model's text.
 */
final class CardMemory {

    /**
     * Cards replayed row by row, newest first; an older card is its title and count only. Every row is
     * billed again on every follow-up, and a follow-up is almost always about the last card or two.
     */
    static final int CARDS_LISTED_IN_FULL = 3;

    /** Long enough for a legal name, short enough that no name reads as a paragraph of instructions. */
    private static final int MAX_TEXT = 80;

    private static final Pattern CARD_BLOCK =
            Pattern.compile("(?s)<suggested_companies\\b[^>]*>.*?</suggested_companies>");
    private static final Pattern MARKUP_CHARACTERS = Pattern.compile("[<>\"\\[\\]]");

    private CardMemory() {
    }

    /**
     * {@code listed} false leaves the rows out and says how many there were. Each row opens with its
     * state in brackets — written here, never taken from third-party text, which cannot contain a
     * bracket — so a company name that reads "already shortlisted" cannot pass for one.
     */
    static String render(AssistantProposal card, ProposalOutcome outcome, boolean listed) {
        List<String> lines = new ArrayList<>();
        lines.add("<suggested_companies title=\"" + plain(card.title()) + "\">");
        if (listed) {
            for (ProposedCompany company : card.companies()) {
                lines.add("- " + describe(company));
            }
        } else {
            lines.add("(" + card.companies().size() + " companies, not listed again here)");
        }
        if (outcome != null) {
            lines.add("Filed " + outcome.added() + " as " + stageLabel(outcome.status())
                    + (outcome.skipped() > 0 ? " (" + outcome.skipped() + " already in the mandate)" : ""));
        }
        lines.add("</suggested_companies>");
        return String.join("\n", lines);
    }

    /** A model that copies the block into its own answer would show the consultant raw markup. */
    static String stripFrom(String answer) {
        return CARD_BLOCK.matcher(answer).replaceAll("").strip();
    }

    /** "[shortlisted] 5a1b… · Lulu Group · United Arab Emirates · 42,000 staff · operates Carrefour". */
    private static String describe(ProposedCompany company) {
        List<String> parts = new ArrayList<>();
        parts.add(company.key());
        parts.add(plain(company.companyName()));
        if (company.country() != null) {
            parts.add(plain(company.country()));
        }
        if (company.employees() != null) {
            parts.add(String.format(Locale.ROOT, "%,d staff", company.employees()));
        }
        if (company.apolloAccountId() == null) {
            parts.add("researched on LinkedIn");
        }
        if (company.operates() != null) {
            parts.add("operates " + plain(company.operates()));
        }
        String state = company.alreadyInMandate()
                ? "already " + stageLabel(company.stage()).toLowerCase(Locale.ROOT)
                : "new";
        return "[" + state + "] " + String.join(" · ", parts);
    }

    private static String stageLabel(String token) {
        TriageCompanyStatus stage = TriageCompanyStatus.fromValue(token);
        if (stage == null) {
            return "In universe";
        }
        return switch (stage) {
            case IN_UNIVERSE -> "In universe";
            case SHORTLISTED -> "Shortlisted";
            case DECLINED -> "Declined";
        };
    }

    /** Names are third-party text: flattened, cut short, and kept from closing or forging markup. */
    private static String plain(String value) {
        if (value == null) {
            return "";
        }
        String flat = MARKUP_CHARACTERS.matcher(value.replaceAll("\\s+", " ")).replaceAll("").strip();
        return flat.length() <= MAX_TEXT ? flat : flat.substring(0, MAX_TEXT).strip() + "…";
    }
}
