package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The hiring entity a mandate is run for. Minimal on purpose: today a client exists to hang projects
 * off, created inline from the New-project modal — the registry form is a separate build.
 */
@Entity
@Table(name = "app_lm_client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "hq_country", length = 64)
    private String hqCountry;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static Client create(UUID workspaceId, String name, String hqCountry, UUID createdBy) {
        Client client = new Client();
        client.workspaceId = workspaceId;
        client.name = name.trim();
        client.hqCountry = hqCountry;
        client.createdBy = createdBy;
        return client;
    }
}
