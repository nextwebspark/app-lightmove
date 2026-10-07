package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.constant.CreditEntryKind;
import app.lightmove.api.billing.credit.model.CreditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditEntryRepository extends JpaRepository<CreditEntry, Long> {

    List<CreditEntry> findByHoldIdAndKindOrderById(UUID holdId, CreditEntryKind kind);
}
