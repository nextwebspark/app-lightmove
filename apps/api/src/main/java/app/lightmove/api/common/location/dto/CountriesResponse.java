package app.lightmove.api.common.location.dto;

import app.lightmove.api.common.location.model.Country;
import java.util.List;

/**
 * The country vocabulary every picker in the SPA renders, and the short list the Strategy filter's
 * Location chips draw.
 *
 * <p>Two lists rather than a flag per row, because they answer different questions. {@code countries}
 * is where an executive or a company can be — the world, since a mandate maps people the market never
 * carried. {@code markets} is where this deployment's universe actually holds companies, counted off
 * it largest first, so a chip can never offer a market the pipeline has not loaded.
 */
public record CountriesResponse(List<Country> countries, List<String> markets) {}
