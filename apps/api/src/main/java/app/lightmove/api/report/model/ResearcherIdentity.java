package app.lightmove.api.report.model;

import app.lightmove.api.report.constant.ResearcherRole;
import java.util.UUID;

/** A user who staffs the mandate or has filed an executive for it, named for the performance table. */
public record ResearcherIdentity(UUID userId, String name, String avatarUrl, ResearcherRole role) {}
