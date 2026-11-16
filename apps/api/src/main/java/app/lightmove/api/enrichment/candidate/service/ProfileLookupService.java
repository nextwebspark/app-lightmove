package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.HeldCandidateResponse;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.candidate.service.LookedUpProfiles;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.constant.ProfileLookupOutcome;
import app.lightmove.api.enrichment.candidate.dto.LookedUpProfileResponse;
import app.lightmove.api.enrichment.candidate.dto.ProfileLookupResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Add form's LinkedIn lookup: the research a plugin capture runs, answered to the form before anything is saved,
 * and held ({@link LookedUpProfiles}) so the save files it without a second vendor read. Not transactional: no
 * connection is held through the vendor call.
 */
@Service
@Slf4j
public class ProfileLookupService {

    private final LinkedInProfileEnricher enricher;
    private final CandidateService candidates;
    private final TriageCompanyReadService companies;
    private final LookedUpProfiles lookedUp;
    private final RateLimiter limiter;
    private final int lookupsPerUserPerMinute;

    public ProfileLookupService(LinkedInProfileEnricher enricher, CandidateService candidates,
                                TriageCompanyReadService companies, LookedUpProfiles lookedUp, RateLimiter limiter,
                                LightMoveProperties properties) {
        this.enricher = enricher;
        this.candidates = candidates;
        this.companies = companies;
        this.lookedUp = lookedUp;
        this.limiter = limiter;
        this.lookupsPerUserPerMinute = properties.enrichment().profileLookupsPerUserPerMinute();
    }

    public ProfileLookupResponse lookUp(UUID userId, UUID workspaceId, UUID projectId, String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(withScheme(linkedinUrl));
        if (slug == null) {
            throw ApiException.of(ErrorCode.PROFILE_LOOKUP_NOT_A_PROFILE);
        }
        String profileUrl = "https://www.linkedin.com/in/" + slug + "/";

        Optional<HeldCandidateResponse> held = candidates.heldAt(workspaceId, projectId, profileUrl);
        if (held.isPresent()) {
            return new ProfileLookupResponse(ProfileLookupOutcome.HELD.value(), null, held.get());
        }
        if (!enricher.isOffered()) {
            return new ProfileLookupResponse(ProfileLookupOutcome.UNAVAILABLE.value(), null, null);
        }
        requireBudget(userId);

        Optional<EnrichedProfile> research = fetch(profileUrl);
        if (research.isEmpty()) {
            return new ProfileLookupResponse(ProfileLookupOutcome.NOT_FOUND.value(), null, null);
        }
        lookedUp.remember(workspaceId, profileUrl, research.get());
        UUID heldEmployerId = heldEmployerOf(workspaceId, projectId, research.get());
        return new ProfileLookupResponse(ProfileLookupOutcome.FOUND.value(),
                profileOf(profileUrl, research.get(), heldEmployerId), null);
    }

    private Optional<EnrichedProfile> fetch(String profileUrl) {
        try {
            return enricher.fetch(profileUrl);
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

    /** A pasted "linkedin.com/in/…" has no scheme, and a URI without one has no host to check. */
    private static String withScheme(String url) {
        String trimmed = url == null ? "" : url.trim();
        return trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
    }
}
