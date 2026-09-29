package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import java.util.List;

/**
 * What one people search answered, how much of it was bought ({@code billed}) or read back free
 * ({@code cached}), and how many people matched in all ({@code matched}, null when the provider did not
 * say) — more than were answered means the per-company cap cut the search short.
 */
public record PeopleFound(List<BrightDataPerson> people, int billed, int cached, Long matched) {}
