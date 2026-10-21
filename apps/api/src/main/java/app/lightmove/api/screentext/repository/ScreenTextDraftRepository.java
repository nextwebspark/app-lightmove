package app.lightmove.api.screentext.repository;

import app.lightmove.api.screentext.model.ScreenTextDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenTextDraftRepository extends JpaRepository<ScreenTextDraft, UUID> {

    Optional<ScreenTextDraft> findByLocaleAndTextKey(String locale, String textKey);

    List<ScreenTextDraft> findAllByLocale(String locale);
}
