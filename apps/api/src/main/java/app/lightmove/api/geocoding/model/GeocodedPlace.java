package app.lightmove.api.geocoding.model;

import app.lightmove.api.geocoding.constant.GeoPrecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One cached answer: a normalised city+country pair and where the vendor put it, or that it could not.
 *
 * <p>Not a {@code BaseEntity}: nothing edits a cache row, it is replaced whole by the upsert in
 * {@code GeocodedPlaceStore}, so it carries no version and no updated-at. A null point is a stored
 * miss — the row exists so the same unplaceable city is not re-asked on every read.
 */
@Entity
@Table(name = "app_lm_geocoded_place")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeocodedPlace {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_key", nullable = false, updatable = false)
    private String placeKey;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "precision", nullable = false, length = 8)
    private GeoPrecision precision;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Empty for a stored miss. */
    public Optional<GeoPoint> point() {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(latitude, longitude, precision));
    }
}
