package app.lightmove.api.core.security.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByScopeAndName(RoleScope scope, String name);

    List<Role> findByScope(RoleScope scope);
}
