package app.lightmove.api.screentext.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Every override as of one publish. Never changed: a rollback publishes an old release's entries again. */
@Entity
@Immutable
@Table(name = "app_lm_screen_text_release")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreenTextRelease {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String locale;

    @Column(nullable = false, updatable = false)
    private int number;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> entries = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_keys", nullable = false, updatable = false)
    private List<String> changedKeys = List.of();

    @Column(updatable = false)
    private String note;

    @Column(name = "restored_from", updatable = false)
    private Integer restoredFrom;

    @Column(name = "published_by", updatable = false)
    private UUID publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    public static ScreenTextRelease of(String locale, int number, Map<String, String> entries, List<String> changedKeys,
                                       String note, Integer restoredFrom, UUID userId, Instant at) {
        ScreenTextRelease release = new ScreenTextRelease();
        release.locale = locale;
        release.number = number;
        release.entries = Map.copyOf(entries);
        release.changedKeys = List.copyOf(changedKeys);
        release.note = note;
        release.restoredFrom = restoredFrom;
        release.publishedBy = userId;
        release.publishedAt = at;
        return release;
    }
}
