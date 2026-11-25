package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The search words' Try it: an invented finance chief brief searched across three countries. */
@Component
@RequiredArgsConstructor
class SearchWordsTrial implements PromptTrial {

    private static final SourcingBrief BRIEF = new SourcingBrief(
            "Group Chief Financial Officer", Seniority.C_SUITE, "Finance", "Riyadh", "Saudi Arabia",
            List.of("Lead the group's listing readiness", "Own treasury and the lending relationships",
                    "Build a finance team that closes the books in five working days"),
            "Runs finance for a family-owned industrial group preparing to list within three years.",
            List.of("Capital markets", "Financial reporting", "Treasury"));

    private static final List<String> COMPANY_COUNTRIES = List.of("Saudi Arabia", "Germany", "France");

    private final SearchWordsDrafter drafter;

    @Override
    public String promptId() {
        return "executive-sourcing-words";
    }

    @Override
    public String inputName() {
        return "Finance chief brief";
    }

    @Override
    public String inputDescription() {
        return "A fictional brief for a group finance chief, with the companies to search in Saudi Arabia, "
                + "Germany and France. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return SearchWordsDrafter.USER_MESSAGE;
    }

    @Override
    public void run() {
        drafter.draft(BRIEF, COMPANY_COUNTRIES);
    }
}
