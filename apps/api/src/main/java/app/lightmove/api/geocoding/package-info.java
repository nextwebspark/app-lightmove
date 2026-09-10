/**
 * <b>Geocoding — a place becomes a point, once.</b> Nothing in this schema carries a coordinate: a
 * company snapshots a city and a country, an executive carries the same pair, and the Apollo universe
 * publishes an address and no latitude. The Companies screen's map needs a point per row, so this
 * package resolves the pair through a vendor and remembers the answer in {@code app_lm_geocoded_place}.
 *
 * <p><b>The cache is the feature.</b> "Dubai, United Arab Emirates" is the same point for every
 * mandate that ever asks, so the vendor is called only for a place nobody has resolved yet, and a miss
 * is remembered too — a city the vendor cannot place must not be re-asked on every read. The table is
 * deliberately not tenant-scoped: a centroid is not client data and carries no PII.
 *
 * <p><b>It stores nothing of a mandate's.</b> The caller hands over city/country pairs and gets
 * points back; which company or person asked never reaches this package or the vendor. Every vendor
 * call goes through {@link app.lightmove.api.core.resilience} and, as everywhere else, outside any
 * transaction — {@code GeocodedPlaceStore} is the separate bean that owns the transactions, for the
 * reason {@code AuditEventWriter} is.
 *
 * <p>Mapbox's terms distinguish a <i>temporary</i> geocode, which may not be stored indefinitely,
 * from a <i>permanent</i> one the account must be entitled to. {@code lightmove.mapbox.permanent-geocoding}
 * says which this deployment holds: without it a cached row is re-asked after
 * {@code lightmove.mapbox.cache-ttl}; with it the request says {@code permanent=true} and the row
 * never expires.
 */
package app.lightmove.api.geocoding;
