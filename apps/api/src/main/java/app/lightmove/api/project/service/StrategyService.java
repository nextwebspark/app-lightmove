package app.lightmove.api.project.service;

import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.model.CompanyRefRow;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.CompanySizeAxis;
import app.lightmove.api.project.constant.EmployeeBand;
import app.lightmove.api.project.constant.GeographyMarket;
import app.lightmove.api.project.constant.OwnershipStructure;
import app.lightmove.api.project.constant.RevenueBand;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.dto.StrategyDtos.ChipDto;
import app.lightmove.api.project.dto.StrategyDtos.CompanyKeyDto;
import app.lightmove.api.project.dto.StrategyDtos.CompanyRefDto;
import app.lightmove.api.project.dto.StrategyDtos.PutCompanySizeRequest;
import app.lightmove.api.project.dto.StrategyDtos.PutGeographyRequest;
import app.lightmove.api.project.dto.StrategyDtos.PutOffLimitsRequest;
import app.lightmove.api.project.dto.StrategyDtos.PutOwnershipRequest;
import app.lightmove.api.project.dto.StrategyDtos.PutSectorsRequest;
import app.lightmove.api.project.dto.StrategyDtos.PutTargetsRequest;
import app.lightmove.api.project.dto.StrategyDtos.StrategyResponse;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.model.StrategyCompanyRef;
import app.lightmove.api.project.model.StrategySector;
import app.lightmove.api.project.model.StrategySizeBand;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.StrategyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The search strategy behind a project. Every load is scoped through the project's
 * {@code (id, workspaceId)} lookup — the workspace id comes from the principal, so a foreign project
 * 404s before any strategy row is touched. Seeded empty on first read (there is no template — an
 * empty scope is the honest start), and written as a whole-scope snapshot by the screen's autosave.
 */
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final AuditService audit;
    // A deliberate cross-feature dependency, documented in CLAUDE.md's dependency rule: list writes
    // resolve their snapshots from the universe, so only companies it actually holds can be stored.
    private final CompanyQueryService companies;

    @Transactional
    public StrategyResponse get(UUID workspaceId, UUID projectId) {
        return toResponse(load(projectId, workspaceId));
    }

    @Transactional
    public StrategyResponse putSectors(UUID userId, UUID workspaceId, UUID projectId,
                                       PutSectorsRequest request, HttpServletRequest httpRequest) {
        rejectDuplicatesWithin(request.direct(), StrategySectorKind.DIRECT);
        rejectDuplicatesWithin(request.adjacent(), StrategySectorKind.ADJACENT);
        rejectDuplicatesWithin(request.inferred(), StrategySectorKind.INFERRED);

        Strategy strategy = load(projectId, workspaceId);
        List<StrategySector> sectors = new ArrayList<>();
        appendKind(sectors, request.direct(), StrategySectorKind.DIRECT);
        appendKind(sectors, request.adjacent(), StrategySectorKind.ADJACENT);
        appendKind(sectors, request.inferred(), StrategySectorKind.INFERRED);
        strategy.replaceSectors(sectors);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "sectors")
                .record();
        return toResponse(strategy);
    }

    @Transactional
    public StrategyResponse putCompanySize(UUID userId, UUID workspaceId, UUID projectId,
                                           PutCompanySizeRequest request, HttpServletRequest httpRequest) {
        List<StrategySizeBand> bands = new ArrayList<>();
        appendBands(bands, request.employee(), CompanySizeAxis.EMPLOYEE, value -> {
            EmployeeBand band = EmployeeBand.fromValue(value);
            return band == null ? null : band.name();
        });
        appendBands(bands, request.revenue(), CompanySizeAxis.REVENUE, value -> {
            RevenueBand band = RevenueBand.fromValue(value);
            return band == null ? null : band.name();
        });

        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceSizeBands(bands);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "companySize")
                .record();
        return toResponse(strategy);
    }

    @Transactional
    public StrategyResponse putGeography(UUID userId, UUID workspaceId, UUID projectId,
                                         PutGeographyRequest request, HttpServletRequest httpRequest) {
        List<String> marketNames = resolveCatalogSelection(request.markets(), value -> {
            GeographyMarket market = GeographyMarket.fromValue(value);
            return market == null ? null : market.name();
        }, "market");

        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceMarkets(marketNames);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "geography")
                .record();
        return toResponse(strategy);
    }

    @Transactional
    public StrategyResponse putOwnership(UUID userId, UUID workspaceId, UUID projectId,
                                         PutOwnershipRequest request, HttpServletRequest httpRequest) {
        List<String> structureNames = resolveCatalogSelection(request.structures(), value -> {
            OwnershipStructure structure = OwnershipStructure.fromValue(value);
            return structure == null ? null : structure.name();
        }, "ownership structure");

        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceStructures(structureNames);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "ownership")
                .record();
        return toResponse(strategy);
    }

    @Transactional
    public StrategyResponse putTargets(UUID userId, UUID workspaceId, UUID projectId,
                                       PutTargetsRequest request, HttpServletRequest httpRequest) {
        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceTargetCompanies(resolveCompanyList(request.companies(),
                strategy.getTargetCompanies(), strategy.getOffLimitsCompanies(), "off-limits"));

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "targets")
                .record();
        return toResponse(strategy);
    }

    @Transactional
    public StrategyResponse putOffLimits(UUID userId, UUID workspaceId, UUID projectId,
                                         PutOffLimitsRequest request, HttpServletRequest httpRequest) {
        Strategy strategy = load(projectId, workspaceId);
        strategy.replaceOffLimitsCompanies(resolveCompanyList(request.companies(),
                strategy.getOffLimitsCompanies(), strategy.getTargetCompanies(), "target"));

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "offLimits")
                .record();
        return toResponse(strategy);
    }

    /**
     * Turn one list's requested keys into the refs to store. A key already on this list keeps its
     * stored snapshot untouched — re-resolving it would make removing one company fail the whole
     * save the day another vanishes upstream. Only new keys are resolved against the universe, and
     * an unknown one is rejected: the client may only pick companies that exist. A key on the other
     * list is rejected too — targeting a company and barring it at once is a contradiction, not a
     * scope.
     */
    private List<StrategyCompanyRef> resolveCompanyList(List<CompanyKeyDto> requested,
                                                        List<StrategyCompanyRef> stored,
                                                        List<StrategyCompanyRef> otherList,
                                                        String otherListLabel) {
        Map<String, StrategyCompanyRef> storedByKey = byKey(stored);
        Map<String, StrategyCompanyRef> otherByKey = byKey(otherList);

        // One pass: reject in-request duplicates and cross-list conflicts, and gather the keys not
        // already stored (only those hit the universe — a stored ref keeps its snapshot, so a company
        // that vanished upstream never fails a save that merely removes a sibling).
        Set<String> seen = new HashSet<>();
        List<CompanyKey> newKeys = new ArrayList<>();
        for (CompanyKeyDto key : requested) {
            String mapKey = keyOf(key);
            if (!seen.add(mapKey)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate company in the list: " + label(key));
            }
            StrategyCompanyRef conflict = otherByKey.get(mapKey);
            if (conflict != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        conflict.getName() + " is already on the " + otherListLabel + " list");
            }
            if (!storedByKey.containsKey(mapKey)) {
                newKeys.add(new CompanyKey(key.source(), key.sourceId()));
            }
        }

        Map<String, CompanyRefRow> resolved = new HashMap<>();
        for (CompanyRefRow row : companies.refsByKeys(newKeys)) {
            resolved.put(keyOf(row.source(), row.sourceId()), row);
        }

        // Assemble in request order: keep stored snapshots, snapshot new ones, reject unknowns.
        List<StrategyCompanyRef> refs = new ArrayList<>(requested.size());
        for (CompanyKeyDto key : requested) {
            String mapKey = keyOf(key);
            StrategyCompanyRef kept = storedByKey.get(mapKey);
            if (kept != null) {
                refs.add(kept);
                continue;
            }
            CompanyRefRow row = resolved.get(mapKey);
            if (row == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Not in the universe: " + label(key));
            }
            refs.add(snapshotOf(row));
        }
        return refs;
    }

    /** The universe row as a stored list snapshot. */
    private static StrategyCompanyRef snapshotOf(CompanyRefRow row) {
        return StrategyCompanyRef.of(row.source(), row.sourceId(), row.name(), row.domain(),
                row.slogan(), row.logo(), row.hqCity(), row.hqCountry());
    }

    private static Map<String, StrategyCompanyRef> byKey(List<StrategyCompanyRef> refs) {
        Map<String, StrategyCompanyRef> map = new HashMap<>();
        for (StrategyCompanyRef ref : refs) {
            map.put(keyOf(ref.getSource(), ref.getSourceId()), ref);
        }
        return map;
    }

    private static String keyOf(CompanyKeyDto key) {
        return keyOf(key.source(), key.sourceId());
    }

    /** A key as "source/sourceId" for a validation message. */
    private static String label(CompanyKeyDto key) {
        return key.source() + "/" + key.sourceId();
    }

    /** NUL appears in neither half, so the concatenation never collides across the boundary. */
    private static String keyOf(String source, String sourceId) {
        return source + "\0" + sourceId;
    }

    private Strategy load(UUID projectId, UUID workspaceId) {
        requireProject(projectId, workspaceId);
        return strategies.findByProjectId(projectId)
                .orElseGet(() -> strategies.save(Strategy.forProject(projectId)));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * Two chips with the same label in one group are a client bug, not a scope. The schema cannot
     * reject them (an element collection is rewritten in place mid-flush, so a unique index could
     * fire on a transient state), so the guard lives here — case-insensitively, since the labels are
     * verbatim taxonomy strings the user never retypes.
     */
    private static void rejectDuplicatesWithin(List<ChipDto> chips, StrategySectorKind kind) {
        Set<String> seen = new HashSet<>();
        for (ChipDto chip : chips) {
            if (!seen.add(chip.label().trim().toLowerCase(Locale.ROOT))) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate sector in the " + kind.name().toLowerCase(Locale.ROOT) + " group");
            }
        }
    }

    private static void appendKind(List<StrategySector> target, List<ChipDto> chips,
                                   StrategySectorKind kind) {
        for (ChipDto chip : chips) {
            target.add(StrategySector.of(kind, chip.label(), chip.selected()));
        }
    }

    /**
     * Validate one axis of a company-size request and append it. {@code toBandName} resolves a wire
     * value against the axis's enum, returning its stored name or {@code null} for an unknown value;
     * a duplicate value within the axis is a client bug (a fixed catalog is never legitimately doubled).
     */
    private static void appendBands(List<StrategySizeBand> target, List<String> values,
                                    CompanySizeAxis axis, Function<String, String> toBandName) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String bandName = toBandName.apply(value);
            if (bandName == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Unknown company-size band: " + value);
            }
            if (!seen.add(bandName)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate band in the " + axis.name().toLowerCase(Locale.ROOT) + " axis");
            }
            target.add(StrategySizeBand.of(axis, bandName));
        }
    }

    /**
     * Validate one fixed-catalog section's selection and return the names to store, in request order.
     * {@code toStoredName} resolves a wire value against the section's enum, returning the stored name
     * or {@code null} for an unknown value; a duplicate value is a client bug (a fixed catalog is never
     * legitimately doubled). The insertion-ordered set preserves the request's order for storage.
     */
    private static List<String> resolveCatalogSelection(List<String> values,
                                                        Function<String, String> toStoredName,
                                                        String catalogLabel) {
        Set<String> storedNames = new LinkedHashSet<>();
        for (String value : values) {
            String storedName = toStoredName.apply(value);
            if (storedName == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Unknown " + catalogLabel + ": " + value);
            }
            if (!storedNames.add(storedName)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate " + catalogLabel + ": " + value);
            }
        }
        return new ArrayList<>(storedNames);
    }

    private static StrategyResponse toResponse(Strategy strategy) {
        return new StrategyResponse(
                chipsOf(strategy, StrategySectorKind.DIRECT),
                chipsOf(strategy, StrategySectorKind.ADJACENT),
                chipsOf(strategy, StrategySectorKind.INFERRED),
                employeeValues(strategy),
                revenueValues(strategy),
                marketValues(strategy),
                structureValues(strategy),
                companyRefs(strategy.getTargetCompanies()),
                companyRefs(strategy.getOffLimitsCompanies()));
    }

    /** A company list as the wire carries it — stored (user) order, snapshots included. */
    private static List<CompanyRefDto> companyRefs(List<StrategyCompanyRef> refs) {
        return refs.stream()
                .map(ref -> new CompanyRefDto(ref.getSource(), ref.getSourceId(), ref.getName(),
                        ref.getDomain(), ref.getSlogan(), ref.getLogo(), ref.getHqCity(),
                        ref.getHqCountry()))
                .toList();
    }

    private static List<ChipDto> chipsOf(Strategy strategy, StrategySectorKind kind) {
        return strategy.getSectors().stream()
                .filter(sector -> sector.getKind() == kind)
                .map(sector -> new ChipDto(sector.getLabel(), sector.isSelected()))
                .toList();
    }

    /** Selected employee bands as range-string values, ordered by the enum's declaration (not by storage). */
    private static List<String> employeeValues(Strategy strategy) {
        Set<String> selected = selectedBandNames(strategy, CompanySizeAxis.EMPLOYEE);
        List<String> values = new ArrayList<>();
        for (EmployeeBand band : EmployeeBand.values()) {
            if (selected.contains(band.name())) {
                values.add(band.value());
            }
        }
        return values;
    }

    /** Selected revenue bands as range-string values, ordered by the enum's declaration. */
    private static List<String> revenueValues(Strategy strategy) {
        Set<String> selected = selectedBandNames(strategy, CompanySizeAxis.REVENUE);
        List<String> values = new ArrayList<>();
        for (RevenueBand band : RevenueBand.values()) {
            if (selected.contains(band.name())) {
                values.add(band.value());
            }
        }
        return values;
    }

    private static Set<String> selectedBandNames(Strategy strategy, CompanySizeAxis axis) {
        Set<String> names = new HashSet<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == axis) {
                names.add(band.getBand());
            }
        }
        return names;
    }

    /** Selected markets as ISO country codes, ordered by the enum's declaration (not by storage). */
    private static List<String> marketValues(Strategy strategy) {
        Set<String> selected = new HashSet<>(strategy.getMarketNames());
        List<String> values = new ArrayList<>();
        for (GeographyMarket market : GeographyMarket.values()) {
            if (selected.contains(market.name())) {
                values.add(market.value());
            }
        }
        return values;
    }

    /** Selected ownership structures as stable tokens, ordered by the enum's declaration. */
    private static List<String> structureValues(Strategy strategy) {
        Set<String> selected = new HashSet<>(strategy.getStructureNames());
        List<String> values = new ArrayList<>();
        for (OwnershipStructure structure : OwnershipStructure.values()) {
            if (selected.contains(structure.name())) {
                values.add(structure.value());
            }
        }
        return values;
    }
}
