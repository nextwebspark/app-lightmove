package app.lightmove.api.company.repository;

import app.lightmove.api.company.model.CoreSignalCompany;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The CoreSignal collect cache. Unscoped finders are correct here — the cache is shared reference
 * data with no tenant column, the same stance as {@code app_lm_companies} (see
 * {@link CoreSignalCompany}).
 */
public interface CoreSignalCompanyRepository extends JpaRepository<CoreSignalCompany, UUID> {

    Optional<CoreSignalCompany> findByCoresignalId(long coresignalId);

    List<CoreSignalCompany> findByCoresignalIdIn(Collection<Long> coresignalIds);
}
