package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/**
 * One person on the Candidates page, in Strategy → People's reading — who, their links, where, how
 * experienced, which contacts are on file — then the CRM's: on which positions, how labelled, by whom
 * owned, and the latest line.
 */
public record CandidatePoolRowResponse(
        UUID personId,
        String fullName,
        String title,
        /** The employer their most recent position recorded, else their current post. */
        String companyName,
        /** That employer's logo: the position's company row's, else research's; null draws an initial. */
        String companyLogoUrl,
        String locationCity,
        String locationCountry,
        String linkedinUrl,
        /** When research last landed, ISO-8601; null for someone never researched, who has no photo. */
        String enrichedAt,
        boolean doNotContact,
        Integer yearsExperience,
        /** Posts in their recorded career. */
        int careerRoles,
        /** Which channels the contact ledger holds; the values stay in the drawer. */
        boolean hasEmail,
        boolean hasPhone,
        /** Every position mapping them, most recently added first. */
        List<PersonPositionResponse> positions,
        List<UUID> tagIds,
        UUID ownerUserId,
        /** Their latest timeline line; null for someone nothing has been recorded about. */
        PersonTimelineEntryResponse lastActivity
) {}
