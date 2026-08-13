package app.lightmove.api.project.service;

import app.lightmove.api.company.constant.CompanySizeAxis;
import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.service.CompanyQueryService.ScopeFilter;
import app.lightmove.api.project.constant.GeographyMarket;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.model.StrategyCompanyRef;
import app.lightmove.api.project.model.StrategySizeBand;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates a mandate's saved {@link Strategy} into the company-universe scope it defines. Every
 * screen that reads the universe through a project — Sourcing's list, the report's aggregates — has to
 * resolve the same criteria, and two copies of this translation would let those screens quietly
 * disagree about which companies are in the search.
 *
 * <p>Nothing here reads a request parameter: a mandate's chosen scope is stored, team-only content, and
 * the only thing a caller ever supplies is the name filter passed through to {@code nameQuery}.
 */
public final class StrategyScope {

    private StrategyScope() {
    }

    /** The universe scope this strategy defines, optionally narrowed by a caller's name filter. */
    public static ScopeFilter of(Strategy strategy, String nameQuery) {
        return new ScopeFilter(
                labelsOf(strategy, StrategySectorKind.DIRECT),
                labelsOf(strategy, StrategySectorKind.ADJACENT),
                labelsOf(strategy, StrategySectorKind.INFERRED),
                employeeBandsOf(strategy),
                revenueBandsOf(strategy),
                marketsOf(strategy),
                keysOf(strategy.getTargetCompanies()),
                keysOf(strategy.getOffLimitsCompanies()),
                nameQuery);
    }

    /** Selected sector labels of the given kinds — DIRECT+ADJACENT are sectors, INFERRED are tags. */
    private static List<String> labelsOf(Strategy strategy, StrategySectorKind... kinds) {
        List<String> labels = new ArrayList<>();
        for (var sector : strategy.getSectors()) {
            if (sector.isSelected() && isOneOf(sector.getKind(), kinds)) {
                labels.add(sector.getLabel());
            }
        }
        return labels;
    }

    private static boolean isOneOf(StrategySectorKind kind, StrategySectorKind[] kinds) {
        for (StrategySectorKind candidate : kinds) {
            if (candidate == kind) {
                return true;
            }
        }
        return false;
    }

    /** Selected employee bands, as their wire-format range strings. Presence in the list is selection. */
    private static List<String> employeeBandsOf(Strategy strategy) {
        List<String> bands = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == CompanySizeAxis.EMPLOYEE) {
                bands.add(EmployeeBand.valueOf(band.getBand()).value());
            }
        }
        return bands;
    }

    /** Selected revenue bands, as their wire-format range strings. Presence in the list is selection. */
    private static List<String> revenueBandsOf(Strategy strategy) {
        List<String> bands = new ArrayList<>();
        for (StrategySizeBand band : strategy.getSizeBands()) {
            if (band.getAxis() == CompanySizeAxis.REVENUE) {
                bands.add(RevenueBand.valueOf(band.getBand()).value());
            }
        }
        return bands;
    }

    /** Selected markets, resolved from the stored enum names to their wire ISO codes. */
    private static List<String> marketsOf(Strategy strategy) {
        return strategy.getMarketNames().stream().map(name -> GeographyMarket.valueOf(name).value()).toList();
    }

    private static List<CompanyKey> keysOf(List<StrategyCompanyRef> refs) {
        return refs.stream().map(ref -> new CompanyKey(ref.getSource(), ref.getSourceId())).toList();
    }
}
