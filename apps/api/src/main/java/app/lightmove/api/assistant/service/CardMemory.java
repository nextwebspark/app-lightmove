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

    private static final Pattern CARD_BLOCK = Pattern.compile("(?s)<card\\b[^>]*>.*?</card>");
    private static final Pattern TAG_CHARACTERS = Pattern.compile("[<>\"]");

    private CardMemory() {
    }

    static String render(AssistantProposal card, ProposalOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("<card title=\"" + plain(card.title()) + "\">");
        for (ProposedCompany company : card.companies()) {
            lines.add("- " + describe(company));
        }
        if (outcome != null) {
            lines.add("Filed " + outcome.added() + " as " + stageLabel(outcome.status())
                    + (outcome.skipped() > 0 ? " (" + outcome.skipped() + " already in the mandate)" : ""));
        }
        lines.add("</card>");
        return String.join("\n", lines);
    }

    /** A model that copies the block into its own answer would show the consultant raw markup. */
    static String stripFrom(String answer) {
        return CARD_BLOCK.matcher(answer).replaceAll("").strip();
    }

    /** "5a1b… · Lulu Group · United Arab Emirates · 42,000 staff · operates Carrefour · already shortlisted". */
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
        if (company.alreadyInMandate()) {
            parts.add("already " + stageLabel(company.stage()).toLowerCase(Locale.ROOT));
        }
        return String.join(" · ", parts);
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

    /** Names are third-party text: flattened, and kept from closing the block they sit in. */
    private static String plain(String value) {
        if (value == null) {
            return "";
        }
        return TAG_CHARACTERS.matcher(value.replaceAll("\\s+", " ")).replaceAll("").strip();
    }
}
