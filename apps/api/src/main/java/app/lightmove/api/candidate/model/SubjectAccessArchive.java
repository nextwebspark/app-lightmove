package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiUse;
import java.time.Instant;
import java.util.List;

/**
 * Everything a subject access export holds, read before a byte of the zip is written: the zip streams on another
 * thread, where row-level security reads nothing, so only the documents' bytes are fetched then, from the bucket.
 */
public record SubjectAccessArchive(String personName, String workspaceName, String exportedBy, Instant exportedAt,
                                   SubjectAccessAbout about, List<AiUse> aiUses, List<SubjectAccessSection> sections,
                                   SubjectAccessReferences references, List<SubjectAccessFile> files) {}
