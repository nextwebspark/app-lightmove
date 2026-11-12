package app.lightmove.api.position.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import app.lightmove.api.project.model.Project;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionDescriptionReaderTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID POSITION = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();

    private final PositionBriefLoader briefs = mock(PositionBriefLoader.class);
    private final PositionDocumentRepository documents = mock(PositionDocumentRepository.class);
    private final PositionDocumentTextReader textReader = mock(PositionDocumentTextReader.class);
    private final PositionDocumentRedactor redactor = mock(PositionDocumentRedactor.class);
    private final PositionDescriptionReader reader =
            new PositionDescriptionReader(briefs, documents, textReader, redactor);

    @Test
    @DisplayName("the client's position description reaches the assessment redacted, its client a placeholder")
    void theDescriptionIsReadRedacted() {
        briefOnFile(POSITION);
        PositionDocument document = mock(PositionDocument.class);
        when(document.getContent()).thenReturn(new byte[] {1});
        when(documents.findByPositionId(POSITION)).thenReturn(Optional.of(document));
        when(textReader.readUpTo(any(), eq(30_000))).thenReturn("Acme needs a CFO to list.");
        when(redactor.redact("Acme needs a CFO to list.", CLIENT, WORKSPACE))
                .thenReturn(new Redaction("[COMPANY_1] needs a CFO to list.", new Pseudonyms(Map.of())));

        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).contains("[COMPANY_1] needs a CFO to list.");
    }

    @Test
    @DisplayName("an undrafted brief, no description, or one that will not read leaves the brief's own fields to count")
    void nothingToReadIsEmpty() {
        briefOnFile(null);
        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).isEmpty();
        verify(documents, never()).findByPositionId(any());

        briefOnFile(POSITION);
        when(documents.findByPositionId(POSITION)).thenReturn(Optional.empty());
        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).isEmpty();

        PositionDocument scan = mock(PositionDocument.class);
        when(scan.getContent()).thenReturn(new byte[] {1});
        when(documents.findByPositionId(POSITION)).thenReturn(Optional.of(scan));
        when(textReader.readUpTo(any(), anyInt())).thenThrow(ApiException.of(ErrorCode.POSITION_DOCUMENT_UNREADABLE));
        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).isEmpty();
    }

    private void briefOnFile(UUID positionId) {
        Project project = mock(Project.class);
        when(project.getClientId()).thenReturn(CLIENT);
        Position position = mock(Position.class);
        when(position.getId()).thenReturn(positionId);
        when(briefs.read(WORKSPACE, PROJECT)).thenReturn(new PositionBrief(project, position));
    }
}
