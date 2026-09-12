/**
 * <b>Geocoding — a place becomes a point, once.</b> Nothing in this schema carries a coordinate, so
 * this package resolves a city/country pair through a vendor and remembers the answer in
 * {@code app_lm_geocoded_place}.
 *
 * <p><b>The cache is the feature.</b> "Dubai, United Arab Emirates" is the same point for every
 * mandate that ever asks, and a miss is remembered too — a city the vendor cannot place must not be
 * re-asked on every read. The table is deliberately not tenant-scoped: a centroid is not client data.
 *
 * <p>It stores nothing of a mandate's — which company or person asked never reaches this package or
 * the vendor. {@code GeocodedPlaceStore} owns the transactions, for the reason
 * {@code AuditEventWriter} does.
 *
 * <p>Mapbox's terms distinguish a <i>temporary</i> geocode, which may not be stored indefinitely, from
 * a <i>permanent</i> one the account must be entitled to.
 * {@code lightmove.mapbox.permanent-geocoding} says which this deployment holds: without it a cached
 * row is re-asked after {@code lightmove.mapbox.cache-ttl}.
 */
package app.lightmove.api.geocoding;
