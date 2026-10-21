package app.lightmove.api.screentext.repository;

import app.lightmove.api.screentext.model.ScreenTextRelease;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenTextReleaseRepository extends JpaRepository<ScreenTextRelease, UUID> {

    Optional<ScreenTextRelease> findFirstByLocaleOrderByNumberDesc(String locale);

    Optional<ScreenTextRelease> findByLocaleAndNumber(String locale, int number);

    List<ScreenTextRelease> findAllByLocaleOrderByNumberDesc(String locale, Pageable page);
}
