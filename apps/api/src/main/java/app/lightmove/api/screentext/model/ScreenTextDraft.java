package app.lightmove.api.screentext.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One key's proposed text, waiting for the next publish. Text equal to the default means "back to the default". */
@Entity
@Table(name = "app_lm_screen_text_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreenTextDraft extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String locale;

    @Column(name = "text_key", nullable = false, updatable = false)
    private String textKey;

    @Column(nullable = false)
    private String text;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ScreenTextDraft of(String locale, String textKey, String text, UUID userId) {
        ScreenTextDraft draft = new ScreenTextDraft();
        draft.locale = locale;
        draft.textKey = textKey;
        draft.text = text;
        draft.updatedBy = userId;
        return draft;
    }

    public void revise(String text, UUID userId) {
        this.text = text;
        this.updatedBy = userId;
    }
}
