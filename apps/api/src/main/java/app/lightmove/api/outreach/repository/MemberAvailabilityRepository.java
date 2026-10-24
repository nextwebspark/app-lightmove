package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.MemberAvailability;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Consultants' own working weeks and email caps. Every read carries the workspace and the consultant. */
public interface MemberAvailabilityRepository extends JpaRepository<MemberAvailability, UUID> {

    Optional<MemberAvailability> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** An empty row where there is none, waiting on a racing insert rather than failing beside it. */
    @Modifying
    @Query(value = "INSERT INTO app_lm_member_availability (workspace_id, user_id) VALUES (:workspaceId, :userId) "
            + "ON CONFLICT (workspace_id, user_id) DO NOTHING", nativeQuery = true)
    void insertIfMissing(UUID workspaceId, UUID userId);

    /** A workspace's cap lowered under a consultant's own lowers theirs to it: a consultant only ever goes lower. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MemberAvailability member SET member.dailyEmailCap = :cap "
            + "WHERE member.workspaceId = :workspaceId AND member.dailyEmailCap > :cap")
    int lowerCapsAbove(UUID workspaceId, int cap);
}
