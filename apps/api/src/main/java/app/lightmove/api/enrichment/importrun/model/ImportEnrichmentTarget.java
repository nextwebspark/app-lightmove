package app.lightmove.api.enrichment.importrun.model;

import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** One person or company an import filed with a LinkedIn link: a candidate's or a triage company's id, and its slug. */
@NamedInterface(name = "api", propagate = false)
public record ImportEnrichmentTarget(ImportEnrichmentKind kind, UUID targetId, String linkedinSlug) {}
