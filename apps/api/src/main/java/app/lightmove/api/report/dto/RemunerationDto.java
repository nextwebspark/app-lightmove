package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter three: the brief's bands (annual, in its {@code currency}; null without a base band)
 * against what executives disclosed. A salary in another currency is counted in
 * {@code otherCurrency}, never converted.
 */
public record RemunerationDto(String currency, CompensationBandDto fixedBand, CompensationBandDto packageBand,
                              List<DisclosureDto> disclosures, int otherCurrency) {}
