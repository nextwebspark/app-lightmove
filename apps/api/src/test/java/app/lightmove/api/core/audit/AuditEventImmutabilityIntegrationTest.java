package app.lightmove.api.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That writing an audit event does not then try to update it.
 *
 * <p>The table has a {@code BEFORE UPDATE} trigger raising {@code insufficient_privilege}, and the
 * entity has no setters — so an {@code UPDATE} can only come from Hibernate deciding the row is
 * dirty and flushing one itself. {@code metadata} is what makes that possible: a {@code jsonb} map
 * is compared through a round trip, and a value does not always come back as the type that went in.
 *
 * <p>This is a regression test with a shipped bug behind it. Every audit caller written before the
 * assistant converted its details to strings by hand — {@code .toString()}, {@code String.valueOf},
 * {@code .name()} — so nothing ever put a {@code UUID} or a boxed number in that map, and the
 * missing {@code @Immutable} stayed invisible. The turn recorder was the first to pass values
 * straight through, and the failure landed at commit inside the async writer, where the try/catch
 * that exists so a lost audit row never fails the work it records could not see it.
 */
@IntegrationTest
class AuditEventImmutabilityIntegrationTest {

    @Autowired
    private AuditService audit;

    @Autowired
    private AuditEventRepository events;

    /** Details that are not all strings: a raw id and raw counts. */
    @Test
    void recordsAnEventWhoseDetailsAreNotAllStrings() {
        UUID turnId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        audit.event(ProjectEventType.ASSISTANT_ASKED)
                .actor(UUID.randomUUID())
                .workspace(workspaceId)
                .target("project", UUID.randomUUID())
                .origin("203.0.113.7", "test-agent")
                .detail("turnId", turnId)
                .detailIfPresent("vendorSearches", 7)
                .detailIfPresent("companiesOnCard", 12)
                .record();

        // Reaching this line at all is most of the assertion: the writer is synchronous under test,
        // so a flush that tried to UPDATE would have surfaced here as the trigger's refusal.
        // Read back in its own workspace: the trail shows a reader its workspace's events and the account's alone.
        List<AuditEvent> written = WorkspaceScope.call(workspaceId, () -> events.findAll());
        assertThat(written)
                .extracting(AuditEvent::getMetadata)
                .anySatisfy(metadata -> assertThat(metadata).containsEntry("turnId", turnId.toString()));
    }
}
