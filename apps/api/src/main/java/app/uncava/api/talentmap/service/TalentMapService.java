package app.uncava.api.talentmap.service;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.common.location.service.Countries;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.config.MapboxSettings;
import app.uncava.api.core.config.TalentMapSettings;
import app.uncava.api.core.security.rbac.ProjectAudience;
import app.uncava.api.geocoding.model.GeoPoint;
import app.uncava.api.geocoding.model.GeocodingResult;
import app.uncava.api.geocoding.model.PlaceKey;
import app.uncava.api.geocoding.service.GeocodingService;
import app.uncava.api.pairing.model.PairedStage;
import app.uncava.api.pairing.service.StagePairingService;
import app.uncava.api.talentmap.dto.MapLocationDto;
import app.uncava.api.talentmap.dto.TalentMapConfigResponse;
import app.uncava.api.talentmap.dto.TalentMapLocationsResponse;
import app.uncava.api.talentmap.dto.TalentMapResponse;
import app.uncava.api.triagecompany.constant.TriageCompanyStatus;
import app.uncava.api.triagecompany.dto.TriageCompaniesResponse;
import app.uncava.api.triagecompany.model.TriageCompanyFilters;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One stage of a mandate read as points, pairing people with companies. Not {@code @Transactional}:
 * the geocoding it triggers may call the vendor.
 */
@Service
public class TalentMapService {

    private final StagePairingService pairing;
    private final GeocodingService geocoding;
    private final MapboxSettings mapbox;
    private final TalentMapSettings caps;

    public TalentMapService(StagePairingService pairing, GeocodingService geocoding,
                            UncavaProperties properties) {
        this.pairing = pairing;
        this.geocoding = geocoding;
        this.mapbox = properties.mapbox();
        this.caps = properties.talentMap();
    }

    public TalentMapConfigResponse config() {
        return new TalentMapConfigResponse(mapbox.isConfigured(),
                mapbox.isConfigured() ? mapbox.publicToken() : null);
    }

    public TalentMapResponse read(UUID workspaceId, UUID projectId, String statusToken, ProjectAudience audience) {
        Placement placed = place(workspaceId, projectId, statusToken, audience);
        Locations located = locate(placed);
        return new TalentMapResponse(placed.companies().companies(), placed.companies().totalCount(),
                placed.people(), placed.totalCandidates(), located.locations(), located.pending());
    }

    /** The points alone, for the screen's poll while places resolve. */
    public TalentMapLocationsResponse readLocations(UUID workspaceId, UUID projectId, String statusToken,
                                                    ProjectAudience audience) {
        Locations located = locate(place(workspaceId, projectId, statusToken, audience));
        return new TalentMapLocationsResponse(located.locations(), located.pending());
    }

    private Placement place(UUID workspaceId, UUID projectId, String statusToken, ProjectAudience audience) {
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(statusToken);
        PairedStage paired = pairing.pair(workspaceId, projectId, status, TriageCompanyFilters.none(),
                caps.maxCompanies(), caps.maxCandidates(), audience);
        TriageCompaniesResponse companies = paired.companies();
        List<CandidateResponse> people = paired.people();

        // A company is drawn where its people are; HQ is the fallback, and first mapped wins.
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
        return new Placement(companies, people, paired.totalCandidates(), placeOfRow);
    }

    private Locations locate(Placement placed) {
        GeocodingResult resolved = geocoding.resolve(new HashSet<>(placed.placeOfRow().values()));
        Map<UUID, MapLocationDto> locations = new HashMap<>();
        placed.placeOfRow().forEach((rowId, place) -> Optional.ofNullable(resolved.points().get(place))
                .ifPresent(point -> locations.put(rowId, toDto(point, place))));
        return new Locations(locations, resolved.pending());
    }

    private record Placement(TriageCompaniesResponse companies, List<CandidateResponse> people,
                             long totalCandidates, Map<UUID, PlaceKey> placeOfRow) {}

    private record Locations(Map<UUID, MapLocationDto> locations, int pending) {}

    private static MapLocationDto toDto(GeoPoint point, PlaceKey place) {
        String isoCode = Countries.codeOf(place.country());
        String country = countryNameOf(place, isoCode);
        return new MapLocationDto(point.latitude(), point.longitude(), point.precision(),
                labelOf(place, country), country, isoCode);
    }

    /** One English spelling per country; a name the catalog does not know keeps its own, title-cased. */
    static String countryNameOf(PlaceKey place, String isoCode) {
        if (!place.hasCountry()) {
            return null;
        }
        return Countries.nameOfCode(isoCode).orElseGet(() -> titleCase(place.country()));
    }

    static String labelOf(PlaceKey place, String country) {
        StringBuilder label = new StringBuilder();
        if (place.hasCity()) {
            label.append(titleCase(place.city()));
        }
        if (country != null) {
            if (!label.isEmpty()) {
                label.append(", ");
            }
            label.append(country);
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
}
