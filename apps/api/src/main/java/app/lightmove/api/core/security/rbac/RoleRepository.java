package app.lightmove.api.core.security.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByScopeAndName(RoleScope scope, String name);

    List<Role> findByScope(RoleScope scope);

    /** Native because the assignment table is written by an ops script and has no entity. */
    @Query(value = """
            select distinct granted.name
            from app_lm_user_platform_role held
            join app_lm_role_action grant_row on grant_row.role_id = held.role_id
            join app_lm_action granted on granted.id = grant_row.action_id
            where held.user_id = :userId
            """, nativeQuery = true)
    List<String> findPlatformActionNames(@Param("userId") UUID userId);
}
