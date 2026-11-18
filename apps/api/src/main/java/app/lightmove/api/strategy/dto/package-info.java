/**
 * The Strategy screen's HTTP contract. Anything derived from a mandate's stored filter is seat-gated, since its scope
 * reveals the mandate's thinking; the universe's own facets and suggestions live in {@code universe.dto}.
 */
@NamedInterface(name = "api", propagate = false)
package app.lightmove.api.strategy.dto;

import org.springframework.modulith.NamedInterface;
