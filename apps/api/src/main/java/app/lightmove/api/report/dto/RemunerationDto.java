package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter three: what the mandate offers against what the market has actually disclosed.
 *
 * <p>Both bands are annual and in {@code currency}, the brief's own; either is null while the brief
 * states no base salary band. A disclosure is an executive whose base salary is on file in that same
 * currency — one recorded in another is not converted but counted in {@code otherCurrency}, so the
 * screen can say how many it is not showing rather than mix them in.
 */
public record RemunerationDto(String currency, CompensationBandDto fixedBand, CompensationBandDto packageBand,
                              List<DisclosureDto> disclosures, int otherCurrency) {}
