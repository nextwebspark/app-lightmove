package app.lightmove.api.common.location.dto;

import app.lightmove.api.common.location.model.OfferedCurrency;
import java.util.List;

/** Every currency a country uses today, by code. */
public record CurrenciesResponse(List<OfferedCurrency> currencies) {}
