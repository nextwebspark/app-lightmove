package app.lightmove.api.talentmap.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MapboxSettings;
import app.lightmove.api.core.config.TalentMapSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.model.GeocodingResult;
import app.lightmove.api.geocoding.model.PlaceKey;
import app.lightmove.api.geocoding.service.GeocodingService;
import app.lightmove.api.talentmap.dto.MapLocationDto;
import app.lightmove.api.talentmap.dto.TalentMapConfigResponse;
import app.lightmove.api.talentmap.dto.TalentMapResponse;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One stage of a mandate, read as points. Composes the three seams the package doc names and adds
 * nothing of its own beyond the pairing: which of the mandate's people sit at which of the stage's
 * companies, and which sit at none.
 *
 * <p>Not {@code @Transactional}: the geocoding it triggers may call the vendor, and the seams it
 * reads through open and close their own transactions.
 */
@Service
public class TalentMapService {

    private final TriageCompanyService triage;
    private final CandidateService candidates;
    private final GeocodingService geocoding;
    private final MapboxSettings mapbox;
    private final TalentMapSettings caps;

    // Hand-written rather than @RequiredArgsConstructor: it derives two settings branches from the
    // properties root rather than taking them, which is the one case the Lombok rule exempts.
    public TalentMapService(TriageCompanyService triage, CandidateService candidates,
                            GeocodingService geocoding, LightMoveProperties properties) {
        this.triage = triage;
        this.candidates = candidates;
        this.geocoding = geocoding;
        this.mapbox = properties.mapbox();
        this.caps = properties.talentMap();
    }

    public TalentMapConfigResponse config() {
        return new TalentMapConfigResponse(mapbox.isConfigured(),
                mapbox.isConfigured() ? mapbox.publicToken() : null);
    }

    public TalentMapResponse read(UUID workspaceId, UUID projectId, String statusToken) {
        TriageCompanyStatus status = resolveStatus(statusToken);
        TriageCompaniesResponse companies =
                triage.listAllOfStage(workspaceId, projectId, status, caps.maxCompanies());
        CandidatesResponse everyone = candidates.listAllOfProject(workspaceId, projectId, caps.maxCandidates());

        // The people at this stage's companies, plus — on the universe alone, as the grid does — the
        // ones mapped at no company of the mandate at all.
        Set<UUID> companyIds = new HashSet<>();
        companies.companies().forEach(company -> companyIds.add(company.id()));
        List<CandidateResponse> people = everyone.candidates().stream()
                .filter(person -> person.triageCompanyId() != null
                        ? companyIds.contains(person.triageCompanyId())
                        : status == TriageCompanyStatus.IN_UNIVERSE)
                .toList();

        // A company is drawn where its people are. The Companies grid's Location column already reads
        // an executive's own city over their employer's, and the map is that same mapping drawn
        // differently, so a company with somebody mapped at it follows them — HQ is what a company
        // nobody has mapped falls back to. First mapped wins where two disagree, which is the order
        // the people arrive in.
        Map<UUID, PlaceKey> placeOfCompanyPeople = new HashMap<>();
        for (CandidateResponse person : people) {
            if (person.triageCompanyId() == null) {
                continue;
            }
            PlaceKey.of(person.locationCity(), person.locationCountry())
                    .ifPresent(place -> placeOfCompanyPeople.putIfAbsent(person.triageCompanyId(), place));
        }

        Map<UUID, PlaceKey> placeOfRow = new HashMap<>();
        for (TriageCompanyResponse company : companies.companies()) {
            PlaceKey mapped = placeOfCompanyPeople.get(company.id());
            if (mapped != null) {
                placeOfRow.put(company.id(), mapped);
                continue;
            }
            PlaceKey.of(company.companyCity(), company.companyCountry())
                    .ifPresent(place -> placeOfRow.put(company.id(), place));
        }
        for (CandidateResponse person : people) {
            PlaceKey.of(person.locationCity(), person.locationCountry())
                    .ifPresent(place -> placeOfRow.put(person.id(), place));
        }

        GeocodingResult resolved = geocoding.resolve(new HashSet<>(placeOfRow.values()));
        Map<UUID, MapLocationDto> locations = new HashMap<>();
        placeOfRow.forEach((rowId, place) -> Optional.ofNullable(resolved.points().get(place))
                .ifPresent(point -> locations.put(rowId, toDto(point, place))));

        return new TalentMapResponse(companies.companies(), companies.totalCount(), people,
                everyone.totalCount(), locations, resolved.pending());
    }

    private static MapLocationDto toDto(GeoPoint point, PlaceKey place) {
        return new MapLocationDto(point.latitude(), point.longitude(), point.precision().name(),
                labelOf(place));
    }

    /** "riyadh, saudi arabia" as the key holds it, in title case, since the display spelling is gone. */
    static String labelOf(PlaceKey place) {
        StringBuilder label = new StringBuilder();
        if (place.hasCity()) {
            label.append(titleCase(place.city()));
        }
        if (place.hasCountry()) {
            if (!label.isEmpty()) {
                label.append(", ");
            }
            label.append(titleCase(place.country()));
        }
        return label.toString();
    }

    private static String titleCase(String words) {
        StringBuilder out = new StringBuilder(words.length());
        boolean startOfWord = true;
        for (char letter : words.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(letter) : letter);
            startOfWord = letter == ' ' || letter == '-';
        }
        return out.toString();
    }

    private static TriageCompanyStatus resolveStatus(String token) {
        if (token == null || token.isBlank()) {
            return TriageCompanyStatus.IN_UNIVERSE;
        }
        TriageCompanyStatus status = TriageCompanyStatus.fromValue(token);
        if (status == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status: " + token);
        }
        return status;
    }
}
