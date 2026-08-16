package app.lightmove.api.project.dto;

import java.util.List;

/**
 * The strategy's whole scope, section by section. The screen holds each section's whole selection
 * and PUTs it back as a snapshot (matching the position autosave model): sectors travel split by
 * kind — direct, adjacent, inferred — and the service flattens them into one ordered list on write;
 * the fixed-catalog sections (company size, geography, ownership) travel as selected values only.
 */
public record StrategyResponse(
        List<ChipDto> direct,
        List<ChipDto> adjacent,
        List<ChipDto> inferred,
        // The company-size scope carries only the *selected* band values per axis (the range strings,
        // e.g. "51-200" / "5M-25M"); the client renders the full catalog from its own mirror and marks
        // these in scope. Empty lists mean nothing selected, not "no such axis".
        List<String> employee,
        List<String> revenue,
        // Geography and ownership follow the same selected-values-only model. markets carries ISO
        // country codes ("AE", "SA"), structures carries org_type values ("Privately Held") — the
        // client's catalog mirror owns the display names.
        List<String> markets,
        List<String> structures,
        // The company lists, in stored (user) order, snapshots included.
        List<CompanyRefDto> targets,
        List<CompanyRefDto> offLimits
) {}
