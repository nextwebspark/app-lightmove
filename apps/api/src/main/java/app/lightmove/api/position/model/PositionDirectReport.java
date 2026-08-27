package app.lightmove.api.position.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One seat reporting into this one. Either field may be blank: a mandate usually knows the seat
 * ("Group Treasurer") long before it knows who sits in it, and refusing the half it has would make
 * the org chart unusable until the day it is complete.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionDirectReport {

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "name", length = 160)
    private String name;

    public static PositionDirectReport of(String title, String name) {
        PositionDirectReport report = new PositionDirectReport();
        report.title = trimmedOrNull(title);
        report.name = trimmedOrNull(name);
        return report;
    }

    /** True when neither half was filled in — a placeholder the screen has not got to yet. */
    public boolean isBlank() {
        return title == null && name == null;
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
