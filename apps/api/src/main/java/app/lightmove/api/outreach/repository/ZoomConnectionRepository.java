package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.constant.ZoomConnectionStatus;
import app.lightmove.api.outreach.model.ZoomConnection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Consultants' own Zoom accounts. Every read carries the workspace and the consultant. */
public interface ZoomConnectionRepository extends JpaRepository<ZoomConnection, UUID> {

    Optional<ZoomConnection> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<ZoomConnection> findByWorkspaceId(UUID workspaceId);

    /** Live Zoom accounts made through {@code clientId}; a row from before V168 counts as made through it. */
    @Query("select count(z) from ZoomConnection z where z.workspaceId = :workspaceId and z.status = :status "
            + "and (z.appClientId is null or z.appClientId = :clientId)")
    long countLiveThrough(UUID workspaceId, ZoomConnectionStatus status, String clientId);

    /** As {@link MailboxConnectionRepository#withdrawLive}: one statement, so no versioned row fails the save. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ZoomConnection z set z.status = :withdrawn where z.id in :ids and z.status = :live")
    int withdrawLive(Collection<UUID> ids, ZoomConnectionStatus live, ZoomConnectionStatus withdrawn);
}
