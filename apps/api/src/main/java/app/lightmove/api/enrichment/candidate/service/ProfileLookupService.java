package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.HeldCandidateResponse;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.text.service.SuppliedText;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.constant.ProfileLookupOutcome;
import app.lightmove.api.enrichment.candidate.dto.LookedUpProfileResponse;
import app.lightmove.api.enrichment.candidate.dto.ProfileLookupResponse;
import app.lightmove.api.enrichment.candidate.model.ProfileResearchOutcome;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Add form's LinkedIn lookup: the research a plugin capture runs, answered to the form before anything is saved and
 * held ({@link LookedUpResearch}) for the save. Not transactional: no connection is held through the vendor call.
 */
@Service
@Slf4j
public class ProfileLookupService {

    private final ProfileResearch profileResearch;
    private final CandidateService candidates;
    private final TriageCompanyReadService companies;
    private final RateLimiter limiter;
    private final int lookupsPerUserPerMinute;

    public ProfileLookupService(ProfileResearch research, CandidateService candidates,
                                TriageCompanyReadService companies, RateLimiter limiter,
                                LightMoveProperties properties) {
        this.profileResearch = research;
        this.candidates = candidates;
        this.companies = companies;
        this.limiter = limiter;
        this.lookupsPerUserPerMinute = properties.enrichment().profileLookupsPerUserPerMinute();
    }

    public ProfileLookupResponse lookUp(UUID userId, UUID workspaceId, UUID projectId, String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(SuppliedText.browsableUrlOrNull(linkedinUrl));
        if (slug == null) {
            throw ApiException.of(ErrorCode.PROFILE_LOOKUP_NOT_A_PROFILE);
        }
        String profileUrl = LinkedInUrls.profileUrlOf(slug);

        Optional<HeldCandidateResponse> held = candidates.heldAt(workspaceId, projectId, profileUrl);
        if (held.isPresent()) {
            return new ProfileLookupResponse(ProfileLookupOutcome.HELD.value(), null, held.get());
        }
        return switch (researchOf(userId, workspaceId, profileUrl)) {
            case ProfileResearchOutcome.Refused refused -> throw refused.reason();
            case ProfileResearchOutcome.OnFile onFile -> found(workspaceId, projectId, profileUrl, onFile.research());
            case ProfileResearchOutcome.Fetched bought -> found(workspaceId, projectId, profileUrl, bought.research());
            case ProfileResearchOutcome.Unavailable unavailable ->
                    new ProfileLookupResponse(ProfileLookupOutcome.UNAVAILABLE.value(), null, null);
            case ProfileResearchOutcome.NotFound notFound ->
                    new ProfileLookupResponse(ProfileLookupOutcome.NOT_FOUND.value(), null, null);
        };
    }

    private ProfileLookupResponse found(UUID workspaceId, UUID projectId, String profileUrl, EnrichedProfile research) {
        UUID heldEmployerId = heldEmployerOf(workspaceId, projectId, research);
        return new ProfileLookupResponse(ProfileLookupOutcome.FOUND.value(),
                profileOf(profileUrl, research, heldEmployerId), null);
    }

    /** The budget is taken just before the paid read, and its refusal reaches the caller as it is. */
    private ProfileResearchOutcome researchOf(UUID userId, UUID workspaceId, String profileUrl) {
        try {
            return profileResearch.researchOrFetch(workspaceId, profileUrl, () -> requireBudget(userId));
        } catch (ApiException refused) {
            throw refused;
        } catch (RuntimeException failed) {
            // The message only: a transport failure's cause can quote the profile address.
            log.warn("Profile lookup failed: {}", failed.getMessage());
            throw ApiException.of(ErrorCode.PROFILE_LOOKUP_FAILED);
        }
    }

    /** Per user, so one caller cannot script the endpoint through a list of profiles. Taken just before the read. */
    private void requireBudget(UUID userId) {
        boolean isWithinBudget = limiter.tryAcquire("profile-lookup:user:%s".formatted(userId),
                lookupsPerUserPerMinute, Duration.ofMinutes(1));
        if (!isWithinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }

    private UUID heldEmployerOf(UUID workspaceId, UUID projectId, EnrichedProfile research) {
        if (research.employerName() == null) {
            return null;
        }
        return companies.heldAt(workspaceId, projectId, research.employerLinkedinUrl(), research.employerName())
                .map(TriageCompanyResponse::id)
                .orElse(null);
    }

    private static LookedUpProfileResponse profileOf(String profileUrl, EnrichedProfile research,
                                                     UUID heldEmployerId) {
        return new LookedUpProfileResponse(profileUrl, research.fullName(), research.title(), research.about(),
                research.employerName(), research.employerLinkedinUrl(), research.employerLogoUrl(), heldEmployerId,
                research.locationCity(), research.locationCountry(),
                research.career().stream()
                        .map(post -> new CandidateCareerEntryDto(post.company(), post.title(), post.period(),
                                post.location()))
                        .toList(),
                research.languages(),
                dataUrlOf(research.photo()));
    }

    private static String dataUrlOf(EnrichedPhoto photo) {
        if (photo == null || photo.content() == null || photo.content().length == 0) {
            return null;
        }
        return "data:" + photo.contentType() + ";base64," + Base64.getEncoder().encodeToString(photo.content());
    }
}
