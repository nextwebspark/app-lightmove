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
import app.lightmove.api.position.service.ExtractionDocumentLoader.LoadedDocument;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionDescriptionReaderTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final LoadedDocument DESCRIPTION = new LoadedDocument(new byte[] {1}, CLIENT, "Group CFO");

    private final ExtractionDocumentLoader loader = mock(ExtractionDocumentLoader.class);
    private final PositionDocumentTextReader textReader = mock(PositionDocumentTextReader.class);
    private final PositionDocumentRedactor redactor = mock(PositionDocumentRedactor.class);
    private final PositionDescriptionReader reader = new PositionDescriptionReader(loader, textReader, redactor);

    @Test
    @DisplayName("the client's position description reaches the assessment redacted, its client a placeholder")
    void theDescriptionIsReadRedacted() {
        when(loader.find(WORKSPACE, PROJECT)).thenReturn(Optional.of(DESCRIPTION));
        when(textReader.readUpTo(any(), eq(30_000))).thenReturn("Acme needs a CFO to list.");
        when(redactor.redact("Acme needs a CFO to list.", CLIENT, WORKSPACE))
                .thenReturn(new Redaction("[COMPANY_1] needs a CFO to list.", new Pseudonyms(Map.of())));

        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).contains("[COMPANY_1] needs a CFO to list.");
    }

    @Test
    @DisplayName("no description, or one that will not read, leaves the brief's own fields to count")
    void nothingToReadIsEmpty() {
        when(loader.find(WORKSPACE, PROJECT)).thenReturn(Optional.empty());
        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).isEmpty();
        verify(textReader, never()).readUpTo(any(), anyInt());

        when(loader.find(WORKSPACE, PROJECT)).thenReturn(Optional.of(DESCRIPTION));
        when(textReader.readUpTo(any(), anyInt())).thenThrow(ApiException.of(ErrorCode.POSITION_DOCUMENT_UNREADABLE));
        assertThat(reader.textOf(WORKSPACE, PROJECT, 30_000)).isEmpty();
        verify(redactor, never()).redact(any(), any(), any());
    }
}
