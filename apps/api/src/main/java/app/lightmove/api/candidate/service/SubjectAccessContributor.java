package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.SubjectAccessContribution;
import app.lightmove.api.candidate.model.SubjectAccessSubject;
import java.util.Set;

/**
 * What a person's subject access export reads from one feature's tables (#919), declared here and implemented where
 * the rows live, as {@link PersonErasureContributor} is, so {@code candidate} depends on none of them. Every row is
 * read inside the export's transaction, on the request thread: row-level security reads nothing anywhere else.
 */
public interface SubjectAccessContributor {

    /** Every table whose rows this contributor exports; {@code SubjectAccessCoverageTest} holds the schema to them. */
    Set<String> tables();

    SubjectAccessContribution contributionOf(SubjectAccessSubject subject);
}
