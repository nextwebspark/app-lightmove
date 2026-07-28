package app.lightmove.api.project.service;

import app.lightmove.api.company.constant.CompanySizeAxis;
import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.project.constant.GeographyMarket;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.model.StrategySizeBand;
import app.lightmove.api.project.repository.StrategyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a stored {@link Strategy} into the scope a CoreSignal run needs — one component so the
 * run service (hashing, tier classification) and the async executor (the search) can never
 * disagree about what a strategy means. The resolution rules deliberately duplicate
 * {@link SourcingService}'s private helpers rather than extracting them: the local-table flow is
 * left byte-for-byte untouched on this POC branch, and ~30 lines of duplication is the cheaper
 * price.
 *
 * <p>Targets and off-limits lists are deliberately ignored — out of the POC's scope by decision.
 */
@Service
@RequiredArgsConstructor
public class SourcingCriteriaResolver {

    private final StrategyRepository strategies;

    /**
     * A strategy's scope with the sector buckets kept apart: the search merges direct + adjacent
     * into one industry list, but tier classification needs to know which bucket a collected
     * company's industry came through.
     */
    public record ResolvedScope(List<String> directSectors, List<String> adjacentSectors,
                                List<String> tags, List<String> markets,
                                List<EmployeeBand> employeeBands, List<RevenueBand> revenueBands) {

        public CoreSignalSearchCriteria toCriteria() {
            return new CoreSignalSearchCriteria(
                    Stream.concat(directSectors.stream(), adjacentSectors.stream()).toList(),
                    tags, markets, employeeBands, revenueBands);
        }
    }

    /**
     * Load the project's strategy and resolve it, inside this bean's own read transaction — the
     * strategy's element collections are lazy, and the callers (the deliberately non-transactional
     * {@code start}, the async executor) have no session of their own to load them in.
     */
    @Transactional(readOnly = true)
    public ResolvedScope resolveForProject(UUID projectId) {
        Strategy strategy = strategies.findByProjectId(projectId)
                .orElseGet(() -> Strategy.forProject(projectId));
        return new ResolvedScope(
                labelsOf(strategy, StrategySectorKind.DIRECT),
                labelsOf(strategy, StrategySectorKind.ADJACENT),
                labelsOf(strategy, StrategySectorKind.INFERRED),
                strategy.getMarketNames().stream().map(name -> GeographyMarket.valueOf(name).value()).toList(),
                bandsOf(strategy, CompanySizeAxis.EMPLOYEE, EmployeeBand::valueOf),
                bandsOf(strategy, CompanySizeAxis.REVENUE, RevenueBand::valueOf));
    }

    /** SHA-256 of the criteria's canonical form — the "are these results still this strategy?" key. */
    public String hashOf(CoreSignalSearchCriteria criteria) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(criteria.canonicalString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static List<String> labelsOf(Strategy strategy, StrategySectorKind kind) {
        List<String> labels = new ArrayList<>();
        for (var sector : strategy.getSectors()) {
            if (sector.isSelected() && sector.getKind() == kind) {
                labels.add(sector.getLabel());
            }
        }
        return labels;
    }

    private static <T> List<T> bandsOf(Strategy strategy, CompanySizeAxis axis,
                                       Function<String, T> valueOf) {
        List<T> bands = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == axis) {
                bands.add(valueOf.apply(band.getBand()));
            }
        }
        return bands;
    }
}
