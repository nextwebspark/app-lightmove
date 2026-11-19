package app.lightmove.api.triagecompany.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A captured company the market could not resolve, with a LinkedIn page for the enrichment worker. */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyCapturedEvent(UUID companyId, UUID projectId, String linkedinSlug) {}
