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
 * One line of a brief's responsibilities: the text, and where it came from — the matched template, a
 * document reading, or typed by hand.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionResponsibility {

    @Column(name = "text", nullable = false, length = 200)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    public static PositionResponsibility of(String text, FieldSource source) {
        PositionResponsibility responsibility = new PositionResponsibility();
        responsibility.text = text.trim();
        responsibility.source = source;
        return responsibility;
    }
}
