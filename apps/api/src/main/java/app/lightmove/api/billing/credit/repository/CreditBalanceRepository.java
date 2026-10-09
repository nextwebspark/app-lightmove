package app.lightmove.api.billing.credit.repository;

import app.lightmove.api.billing.credit.model.CreditBalance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_credit_balance (workspace_id) VALUES (:workspaceId)
            ON CONFLICT (workspace_id) DO NOTHING""")
    void openIfAbsent(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM CreditBalance b WHERE b.workspaceId = :workspaceId")
    Optional<CreditBalance> findForUpdate(UUID workspaceId);
}
