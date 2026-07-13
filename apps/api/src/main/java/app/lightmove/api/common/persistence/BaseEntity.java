package app.lightmove.api.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Identity, timestamps and optimistic locking, shared by every mutable aggregate.
 *
 * <p>{@code updated_at} is also maintained by a database trigger. That is not redundancy for its own
 * sake: the trigger is what keeps the column honest when a row is touched by a migration or a manual
 * fix rather than by Hibernate.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Guards against lost updates when two requests race on the same row (e.g. concurrent logins). */
    @Version
    @Column(nullable = false)
    private long version;

    /**
     * Entity equality is identity equality, and only once persisted. Deliberately not Lombok's
     * {@code @EqualsAndHashCode}: comparing a transient entity by its fields makes two distinct
     * unsaved rows look equal, which quietly corrupts any {@code Set} they land in.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that) || id == null) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        // Constant, not id.hashCode(): the id is null before persist and non-null after, and an
        // entity whose hash changes mid-transaction goes missing from any HashSet holding it.
        return getClass().hashCode();
    }
}
