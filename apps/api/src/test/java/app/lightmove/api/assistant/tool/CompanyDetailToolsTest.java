package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/** What a ranking reads about companies: everything held, in the order asked, and nothing bought. */
class CompanyDetailToolsTest {

    private static final String UAE = "United Arab Emirates";

    private final ApolloCompanyQueryService market = mock(ApolloCompanyQueryService.class);
    private final CompanyResearch research = mock(CompanyResearch.class);
    private final TriageCompanyReadService triaged = mock(TriageCompanyReadService.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final AssistantToolContext context = new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(),
            recorder);
    private final CompanyDetailTools tools = new CompanyDetailTools(market, research, triaged);

    @Test
    @DisplayName("an earlier answer's keys come back in the order given — database rows with their niche, a "
            + "LinkedIn page from the cache — and an unknown key is named, never guessed")
    void readsTheKeysInOrder() {
        when(market.byAccountIds(anyList())).thenReturn(List.of(
                row("a1", "Ahmed Seddiqi & Sons", "luxury goods & jewelry", 950,
                        List.of("Luxury Watches", "retail", "fine jewellery"))));
        when(market.nicheKeywordCounts(anyList())).thenReturn(Map.of("fine jewellery", 12L, "luxury watches", 40L));
        when(research.heldRecordsOf(anyCollection())).thenReturn(Map.of("rivoli-group", new VendorCompanyRecord(
                "rivoli-group", "Rivoli Group", "Retail Luxury Goods and Jewelry", UAE, "Dubai", 1_800, null, null,
                1986, "Rivoli is a leading luxury watch and eyewear retailer in the Middle East.", null,
                List.of("watches", "eyewear"), "{}")));
        when(triaged.stagesOf(any(), any(), anyList(), anyList()))
                .thenReturn(new MandateStages(Map.of("a1", TriageCompanyStatus.SHORTLISTED), Map.of()));

        CompanyDetails details = tools.readCompanyDetails(List.of("rivoli-group", " a1 ", "nobody-knows", "a1"),
                toolContext());

        assertThat(details.companies()).extracting(CompanyDetail::companyName)
                .containsExactly("Rivoli Group", "Ahmed Seddiqi & Sons");
        assertThat(details.companies().get(1)).satisfies(seddiqi -> {
            assertThat(seddiqi.niche()).containsExactly("luxury watches", "fine jewellery");
            assertThat(seddiqi.sectorGroup()).isNotBlank();
            assertThat(seddiqi.mandateStage()).isEqualTo("shortlisted");
        });
        assertThat(details.companies().getFirst()).satisfies(rivoli -> {
            assertThat(rivoli.linkedinSlug()).isEqualTo("rivoli-group");
            assertThat(rivoli.industry()).isEqualTo("luxury goods & jewelry");
            assertThat(rivoli.niche()).containsExactly("watches", "eyewear");
        });
        assertThat(details.notFound()).containsExactly("nobody-knows");
        verify(research, never()).recordOf(any());
        verify(research).heldRecordsOf(argThat(slugs -> slugs.containsAll(List.of("rivoli-group", "nobody-knows"))
                && slugs.size() == 2));
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Reading 3 companies");
            assertThat(step.detail()).isEqualTo("2 read · 1 not found");
        });
    }

    @Test
    @DisplayName("a page the cache has lost is read from what the earlier answer kept, and a long about is cut")
    void fallsBackToWhatTheChatKept() {
        when(triaged.stagesOf(any(), any(), anyList(), anyList())).thenReturn(MandateStages.NONE);
        recorder.remember("boutique-1", new CapturedCompanyDetails("Boutique One", "Retail", UAE, "Dubai", 300,
                null, null, null, null, "x".repeat(400), null, null, null));

        CompanyDetails details = tools.readCompanyDetails(List.of("boutique-1"), toolContext());

        assertThat(details.companies()).singleElement().satisfies(boutique -> {
            assertThat(boutique.companyName()).isEqualTo("Boutique One");
            assertThat(boutique.about()).hasSize(CompanyDetail.MAX_ABOUT + 1).endsWith("…");
        });
        assertThat(details.notFound()).isEmpty();
    }

    @Test
    @DisplayName("a stage of the mandate is read whole with what each company does, and says when there is more")
    void readsAStageOfTheMandate() {
        TriageCompanyResponse seddiqi = filed("a1", "Ahmed Seddiqi & Sons", "luxury goods & jewelry");
        TriageCompanyResponse typed = filed(null, "Hand-typed Boutique", "retail");
        when(triaged.listAllOfStage(eq(context.workspaceId()), eq(context.projectId()),
                eq(TriageCompanyStatus.SHORTLISTED), any(), eq(CompanyDetailTools.MAX_MANDATE_COMPANIES)))
                .thenReturn(new TriageCompaniesResponse(List.of(seddiqi, typed), 140, 0, 100, null));
        when(market.byAccountIds(List.of("a1"))).thenReturn(List.of(
                row("a1", "Ahmed Seddiqi & Sons", "luxury goods & jewelry", 950, List.of("luxury watches"))));
        when(market.nicheKeywordCounts(anyList())).thenReturn(Map.of("luxury watches", 40L));

        MandateCompanies shortlist = tools.listMandateCompanies("Shortlisted", toolContext());

        assertThat(shortlist.stage()).isEqualTo("shortlisted");
        assertThat(shortlist.total()).isEqualTo(140);
        assertThat(shortlist.companies()).extracting(CompanyDetail::companyName, CompanyDetail::niche,
                        CompanyDetail::mandateStage)
                .containsExactly(
                        tuple("Ahmed Seddiqi & Sons", List.of("luxury watches"), "shortlisted"),
                        tuple("Hand-typed Boutique", List.of(), "shortlisted"));
        assertThat(recorder.steps()).singleElement().satisfies(step -> {
            assertThat(step.label()).isEqualTo("Reading the shortlisted companies");
            assertThat(step.detail()).isEqualTo("The first 2 of 140");
        });
    }

    @Test
    @DisplayName("a stage is understood however the model spells it, and the universe is the default")
    void readsTheStageLeniently() {
        assertThat(CompanyDetailTools.stageOf("in universe")).isEqualTo(TriageCompanyStatus.IN_UNIVERSE);
        assertThat(CompanyDetailTools.stageOf("SHORTLISTED")).isEqualTo(TriageCompanyStatus.SHORTLISTED);
        assertThat(CompanyDetailTools.stageOf("declined")).isEqualTo(TriageCompanyStatus.DECLINED);
        assertThat(CompanyDetailTools.stageOf(null)).isEqualTo(TriageCompanyStatus.IN_UNIVERSE);
        assertThat(CompanyDetailTools.stageOf("the best ones")).isEqualTo(TriageCompanyStatus.IN_UNIVERSE);
    }

    private ToolContext toolContext() {
        return new ToolContext(context.asMap());
    }

    private static CompanyRow row(String id, String name, String industry, int employees, List<String> keywords) {
        return new CompanyRow(id, name, industry, UAE, "Dubai", employees, null, null, null,
                "Watch and jewellery retailer.", 1950, null, null, null, null, null, null, null, null, null, null,
                null, null, keywords, List.of(), List.of(), List.of());
    }

    private static TriageCompanyResponse filed(String apolloAccountId, String name, String industry) {
        return new TriageCompanyResponse(UUID.randomUUID(), apolloAccountId, "manual", "shortlisted", null, false,
                name, industry, UAE, "Dubai", 500, null, null, null, null, null, null, null, Map.of(), Instant.EPOCH);
    }
}
