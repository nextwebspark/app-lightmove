package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.core.text.service.LinkedInUrls;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Research the Add form's profile lookup already bought, held briefly per workspace and profile slug so the save that
 * follows files it rather than paying the vendor twice. In memory, as the rate limiter is: a miss (expired, or another
 * instance) only means the save files what the form typed.
 */
@Component
public class LookedUpProfiles {

    private static final Duration HELD_FOR = Duration.ofMinutes(30);

    /** Each entry may carry a photo's bytes; a few hundred covers every form open at once. */
    private static final long MOST_HELD = 500;

    private final Cache<String, EnrichedProfile> held =
            Caffeine.newBuilder().expireAfterWrite(HELD_FOR).maximumSize(MOST_HELD).build();

    public void remember(UUID workspaceId, String linkedinUrl, EnrichedProfile research) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        if (slug != null) {
            held.put(keyOf(workspaceId, slug), research);
        }
    }

    public Optional<EnrichedProfile> find(UUID workspaceId, String linkedinUrl) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        return slug == null ? Optional.empty() : Optional.ofNullable(held.getIfPresent(keyOf(workspaceId, slug)));
    }

    private static String keyOf(UUID workspaceId, String slug) {
        return workspaceId + ":" + slug;
    }
}
