package app.lightmove.api.candidate.model;

import java.time.Instant;

/** Every vendor's reading of a person laid together, and when a vendor last read the profile; {@code details} is null where none is kept. */
public record PersonResearchOnFile(ResearchedProfileDetails details, Instant vendorSeenAt) {}
