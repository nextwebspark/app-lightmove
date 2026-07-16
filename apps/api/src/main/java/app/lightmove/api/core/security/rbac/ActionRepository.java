package app.lightmove.api.core.security.rbac;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, UUID> {

    List<Action> findByScope(RoleScope scope);
}
