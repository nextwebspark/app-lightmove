package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.List;

/** One person as the Candidates page's export writes them: a line of the file, already resolved to text. */
public record PoolPersonExport(
        String fullName,
        String title,
        String companyName,
        String locationCity,
        String locationCountry,
        String linkedinUrl,
        List<String> emails,
        List<String> phones,
        List<PoolPositionExport> positions,
        List<String> tags,
        String ownerName,
        boolean doNotContact,
        Instant addedAt
) {}
