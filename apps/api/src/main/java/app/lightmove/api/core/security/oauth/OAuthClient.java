package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An AI client allowed to ask for a grant (V115). Always public: no secret, PKCE, code and refresh grants only. */
@Entity
@Table(name = "app_lm_oauth_client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthClient extends BaseEntity {

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "client_uri")
    private String clientUri;

    @Column(name = "logo_uri")
    private String logoUri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_uris", nullable = false)
    private List<String> redirectUris = new ArrayList<>();

    /** {@code ApiKeyScope} wire tokens: the most this client may ever ask for. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false)
    private List<String> scopes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private OAuthClientSource source;

    public static OAuthClient registered(String clientId, String clientName, String clientUri, String logoUri,
                                         List<String> redirectUris, List<String> scopes, OAuthClientSource source) {
        OAuthClient client = new OAuthClient();
        client.clientId = clientId;
        client.clientName = clientName;
        client.clientUri = clientUri;
        client.logoUri = logoUri;
        client.redirectUris = new ArrayList<>(redirectUris);
        client.scopes = new ArrayList<>(scopes);
        client.source = source;
        return client;
    }
}
