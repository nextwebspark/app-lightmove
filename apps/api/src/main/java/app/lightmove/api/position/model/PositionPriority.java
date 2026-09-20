package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.FieldSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One strategic priority offered against the mandate: a name somebody wrote, and whether the mandate
 * is aligned to it. An unselected row is a chip on the screen nobody has lit — kept rather than
 * dropped, because the palette a consultant builds is worth as much as the choices made from it.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionPriority {

    @Column(name = "priority", nullable = false, length = 120)
    private String name;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    public static PositionPriority of(String name, boolean selected, FieldSource source) {
        PositionPriority priority = new PositionPriority();
        priority.name = name.trim();
        priority.selected = selected;
        priority.source = source;
        return priority;
    }
}
