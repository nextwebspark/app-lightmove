package app.lightmove.api.candidate.model;

import org.jspecify.annotations.Nullable;

/** A position a subject access export names: its title, the client or business unit it is for, and if archived. */
public record SubjectAccessPosition(String title, @Nullable String hiringFor, @Nullable String archivedAt) {}
