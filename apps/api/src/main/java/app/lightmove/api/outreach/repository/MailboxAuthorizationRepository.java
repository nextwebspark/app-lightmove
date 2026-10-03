package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.MailboxAuthorization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Mailbox connections in flight, found by the hash of the state the provider sends back. */
public interface MailboxAuthorizationRepository extends JpaRepository<MailboxAuthorization, UUID> {

    Optional<MailboxAuthorization> findByStateHash(String stateHash);

    /** One count, so two callbacks racing on one state cannot both redeem it: the second deletes nothing. */
    @Modifying
    @Transactional
    @Query("DELETE FROM MailboxAuthorization a WHERE a.id = :id")
    int redeem(UUID id);

    /** A mailbox attempt and a Zoom one are separate screens, so starting one never drops the other. */
    @Modifying
    @Transactional
    @Query("DELETE FROM MailboxAuthorization a WHERE a.userId = :userId AND a.provider <> 'zoom'")
    int forgetStartedBy(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM MailboxAuthorization a WHERE a.userId = :userId AND a.provider = 'zoom'")
    int forgetZoomStartedBy(UUID userId);
}
