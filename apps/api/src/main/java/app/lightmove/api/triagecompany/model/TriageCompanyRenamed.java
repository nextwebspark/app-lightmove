package app.lightmove.api.triagecompany.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A mandate-supplied company's name was corrected, published inside the editing transaction. */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyRenamed(UUID projectId, UUID triageCompanyId, String companyName) {}
