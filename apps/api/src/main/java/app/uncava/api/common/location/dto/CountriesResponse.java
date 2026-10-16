package app.uncava.api.common.location.dto;

import app.uncava.api.common.location.model.Country;
import java.util.List;

/**
 * {@code countries} is everywhere a person or company can be; {@code markets} is where this
 * deployment's universe holds companies, largest first, so a chip never offers an unloaded market.
 */
public record CountriesResponse(List<Country> countries, List<String> markets) {}
