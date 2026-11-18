package app.lightmove.api.triagecompany.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Someone deliberately added or edited a company the mandate already held, published inside that transaction. */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyWorked(UUID projectId, UUID triageCompanyId) {}
