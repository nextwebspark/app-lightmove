package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.CompanyFacetsSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.dto.FacetsResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

/**
 * Everything the filter sidebar counts, composed once and held.
 *
 * <p>Four aggregates over the whole universe cost ~110ms and were recomputed on every call, for data
 * that is the same for every user in every workspace and changes only when the pipeline reloads. So it
 * is held as <b>one entry under no key</b>: keying it by workspace would multiply identical work by
 * tenant, which is the whole thing worth avoiding.
 *
 * <p>Caffeine directly rather than Spring's cache abstraction, matching
 * {@link app.lightmove.api.core.ratelimit.service.Bucket4jRateLimiter}. Nothing else here is cacheable
 * — the taxonomies are already constructor-loaded maps and the keyword vocabulary is already a
 * materialised view — so {@code @EnableCaching} would arm a global, proxy-based mechanism for a single
 * entry. Two hazards come with it that this seam does not have: the self-invocation trap that has
 * already made {@code @Async}, {@code @Transactional} and {@code @Retryable} inert here, and a default
 * key of the method arguments — which for a workspace-scoped method reading
 * {@code AuthPrincipal.requireWorkspaceId()} internally is no arguments at all, and so one entry shared
 * across every tenant.
 *
 * <p>{@code get(key, loader)} is a {@code computeIfAbsent}: a cold start with several tabs opening at
 * once runs the four queries once rather than once per tab.
 */
@Service
public class CompanyFacetsService {

    /** The cache holds one thing; this names it rather than leaving a bare literal at the call site. */
    private static final String SINGLE_ENTRY = "facets";

    private final ApolloCompanyQueryService companies;
    private final IndustryAdjacency adjacency;

    /** Null when the TTL is zero, which is how the suite makes every call re-read the universe. */
    private final Cache<String, FacetsResponse> cache;

    public CompanyFacetsService(ApolloCompanyQueryService companies, IndustryAdjacency adjacency,
                                LightMoveProperties properties) {
        this.companies = companies;
        this.adjacency = adjacency;
        CompanyFacetsSettings settings = properties.company().facets();
        this.cache = settings.isEnabled()
                ? Caffeine.newBuilder().expireAfterWrite(settings.cacheTtl()).maximumSize(1).build()
                : null;
    }

    public FacetsResponse facets() {
        return cache == null ? compute() : cache.get(SINGLE_ENTRY, key -> compute());
    }

    private FacetsResponse compute() {
        return new FacetsResponse(
                companies.sectorGroups(),
                adjacency.neighbours(),
                companies.marketSegmentFacets(),
                companies.employeeBandFacets(),
                companies.revenueBandFacets());
    }
}
