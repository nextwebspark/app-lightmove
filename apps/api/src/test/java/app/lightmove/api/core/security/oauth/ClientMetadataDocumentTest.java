package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a client's metadata document as its host publishes it. */
class ClientMetadataDocumentTest {

    private static final String CLAUDE = "https://claude.ai/oauth/mcp-oauth-client-metadata";

    @Test
    @DisplayName("Claude's published document is accepted though it lists a grant Uncava does not offer")
    void claudeDocumentAccepted() {
        ClientMetadataDocument document = ClientMetadataDocument.read(CLAUDE, """
                {"client_id":"https://claude.ai/oauth/mcp-oauth-client-metadata","client_name":"Claude",
                 "client_uri":"https://claude.ai","redirect_uris":["https://claude.ai/api/mcp/auth_callback"],
                 "grant_types":["authorization_code","refresh_token","urn:ietf:params:oauth:grant-type:jwt-bearer"],
                 "response_types":["code"],"token_endpoint_auth_method":"none"}""");

        assertThat(document.clientName()).isEqualTo("Claude");
        assertThat(document.redirectUris()).containsExactly("https://claude.ai/api/mcp/auth_callback");
    }

    @Test
    @DisplayName("a document without the code grant is refused")
    void documentWithoutCodeGrantRefused() {
        assertThatThrownBy(() -> ClientMetadataDocument.read(CLAUDE, """
                {"client_id":"https://claude.ai/oauth/mcp-oauth-client-metadata",
                 "redirect_uris":["https://claude.ai/api/mcp/auth_callback"],
                 "grant_types":["client_credentials"],"token_endpoint_auth_method":"none"}"""))
                .isInstanceOf(ClientMetadataUnavailable.class)
                .extracting(refused -> ((ClientMetadataUnavailable) refused).refusal())
                .isEqualTo(ClientMetadataRefusal.INVALID_DOCUMENT);
    }
}
