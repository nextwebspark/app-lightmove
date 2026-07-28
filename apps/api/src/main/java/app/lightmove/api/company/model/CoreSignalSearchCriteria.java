package app.lightmove.api.company.model;

import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A strategy scope resolved into the terms a CoreSignal search understands: industry labels
 * (direct + adjacent sectors), free-text tags (inferred), ISO-3166 alpha-2 HQ countries, and the
 * selected size bands. The sector/tag pair is the anchor — {@link #hasAnchor()} false means the
 * scope matches nothing and no credit-costing search may run, mirroring the local query engine's
 * "sector anchor required" rule.
 */
public record CoreSignalSearchCriteria(
        List<String> industries,
        List<String> tags,
        List<String> countryIso2Codes,
        List<EmployeeBand> employeeBands,
        List<RevenueBand> revenueBands
) {

    public boolean hasAnchor() {
        return !industries.isEmpty() || !tags.isEmpty();
    }

    /**
     * A stable text form for hashing: each dimension sorted (selection order must not change the
     * hash) and prefixed (an industry moving to the tag list must). The run service hashes this to
     * decide whether stored results still answer the current strategy.
     */
    public String canonicalString() {
        return "industries:" + sorted(industries)
                + "|tags:" + sorted(tags)
                + "|countries:" + sorted(countryIso2Codes)
                + "|employees:" + sorted(employeeBands.stream().map(Enum::name).toList())
                + "|revenue:" + sorted(revenueBands.stream().map(Enum::name).toList());
    }

    private static String sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }
}
