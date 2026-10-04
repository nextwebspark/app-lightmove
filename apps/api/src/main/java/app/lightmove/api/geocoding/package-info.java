/**
 * <b>Geocoding</b> — a city/country pair resolved once through a vendor and remembered, misses too, in
 * {@code app_lm_geocoded_place}. Deliberately not tenant-scoped: a centroid is not client data, and
 * which mandate asked never reaches this package or the vendor. Mapbox's terms forbid storing a
 * temporary geocode indefinitely, so without {@code lightmove.mapbox.permanent-geocoding} a row is
 * re-asked after {@code lightmove.mapbox.cache-ttl}.
 */
package app.lightmove.api.geocoding;
