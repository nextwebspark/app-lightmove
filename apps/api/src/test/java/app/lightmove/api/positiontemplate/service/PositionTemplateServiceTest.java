package app.lightmove.api.positiontemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.repository.PositionTemplateRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which template a role title drafts. The New position modal sends a picked template's title and nothing
 * else, so a title that is a visible template's own must draft that template, whatever its keywords say.
 */
class PositionTemplateServiceTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    private final PositionTemplateRepository repository = mock(PositionTemplateRepository.class);
    private final PositionTemplateService service = new PositionTemplateService(repository);

    private final PositionTemplate libraryCfo = library("chief-financial-officer", "Chief Financial Officer",
            "chief financial", "cfo");
    private final PositionTemplate fallback = library(PositionTemplateService.FALLBACK_CODE, "Executive");

    @Test
    @DisplayName("a firm's template whose keywords miss its own title still drafts a mandate given that title")
    void ownTitleBeatsAnotherTemplatesKeyword() {
        PositionTemplate groupCfo = own("Group Chief Financial Officer", "group cfo");
        when(repository.findAllVisibleTo(WORKSPACE)).thenReturn(List.of(groupCfo, libraryCfo, fallback));

        assertThat(service.matching(WORKSPACE, "Group Chief Financial Officer")).contains(groupCfo);
    }

    @Test
    @DisplayName("a firm's template with no keywords drafts a mandate given its title, not the fallback")
    void keywordlessTemplateIsDraftedByItsTitle() {
        PositionTemplate sustainability = own("Chief Sustainability Officer");
        when(repository.findAllVisibleTo(WORKSPACE)).thenReturn(List.of(sustainability, libraryCfo, fallback));

        assertThat(service.matching(WORKSPACE, "chief sustainability officer ")).contains(sustainability);
    }

    @Test
    @DisplayName("a firm keyword inside a library template's title does not take that title from it")
    void firmKeywordDoesNotTakeALibraryTitle() {
        PositionTemplate chiefOfStaff = own("Chief of Staff", "chief");
        when(repository.findAllVisibleTo(WORKSPACE)).thenReturn(List.of(chiefOfStaff, libraryCfo, fallback));

        assertThat(service.matching(WORKSPACE, "Chief Financial Officer")).contains(libraryCfo);
    }

    @Test
    @DisplayName("a title no template carries is still matched by keyword, then falls back")
    void typedTitlesStillMatchByKeyword() {
        PositionTemplate chiefOfStaff = own("Chief of Staff", "chief of staff");
        when(repository.findAllVisibleTo(WORKSPACE)).thenReturn(List.of(chiefOfStaff, libraryCfo, fallback));

        assertThat(service.matching(WORKSPACE, "Group CFO – Energy Division")).contains(libraryCfo);
        assertThat(service.matching(WORKSPACE, "Deputy Chief of Staff")).contains(chiefOfStaff);
        assertThat(service.matching(WORKSPACE, "Head of Treasury")).contains(fallback);
    }

    private static PositionTemplate library(String code, String title, String... keywords) {
        return PositionTemplate.forLibrary(code, draft(title, keywords), 0, null);
    }

    private static PositionTemplate own(String title, String... keywords) {
        return PositionTemplate.ownedBy(WORKSPACE, "own-" + UUID.randomUUID(), draft(title, keywords), null);
    }

    private static PositionTemplateDraft draft(String title, String... keywords) {
        return new PositionTemplateDraft(title, PositionDiscipline.FINANCE, Seniority.C_SUITE, null,
                List.of(keywords), null).normalised();
    }
}
