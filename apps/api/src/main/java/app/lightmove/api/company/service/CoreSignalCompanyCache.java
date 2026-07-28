package app.lightmove.api.company.service;

import app.lightmove.api.company.model.CoreSignalCompany;
import app.lightmove.api.company.model.CoreSignalCompanyRecord;
import app.lightmove.api.company.repository.CoreSignalCompanyRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The collect-credit safeguard: membership here is what decides "already paid for, never collect
 * again". Also the transactional boundary for stores — the run executor calls {@link #store} from
 * a plain {@code @Async} thread, and crossing into this separate bean is what makes the
 * {@code @Transactional} proxy real (the same self-invocation trap {@code AuditEventWriter}
 * documents). Each store commits alone, so a half-finished batch still leaves every collected
 * company cached and visible to pollers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoreSignalCompanyCache {

    private final CoreSignalCompanyRepository companies;

    /** Which of these ids are already collected — the set the executor never pays for again. */
    @Transactional(readOnly = true)
    public Set<Long> cachedIds(Collection<Long> coresignalIds) {
        if (coresignalIds.isEmpty()) {
            return Set.of();
        }
        return companies.findByCoresignalIdIn(coresignalIds).stream()
                .map(CoreSignalCompany::getCoresignalId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<CoreSignalCompany> findAllByIds(Collection<Long> coresignalIds) {
        if (coresignalIds.isEmpty()) {
            return List.of();
        }
        return companies.findByCoresignalIdIn(coresignalIds);
    }

    /**
     * Upsert one collected record by its CoreSignal id. {@code REQUIRES_NEW} so each collected
     * company commits (and becomes visible to the polling read) the moment it lands, independent
     * of its batch siblings.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(CoreSignalCompanyRecord record) {
        try {
            companies.findByCoresignalId(record.coresignalId())
                    .ifPresentOrElse(
                            existing -> existing.refreshFrom(record),
                            () -> companies.save(CoreSignalCompany.from(record)));
        } catch (DataIntegrityViolationException ex) {
            // Two projects collected the same company concurrently and the other insert won the
            // unique index. The cached row exists either way, which is all the caller needs.
            log.debug("Concurrent cache insert for CoreSignal company {} — already stored", record.coresignalId());
        }
    }
}
