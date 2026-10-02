package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.crypto.model.EncryptionContext;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.RefreshedAccessToken;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** An access token from a stored refresh token: refreshed, reused while it lives, and a refusal withdrawing access. */
class MailboxTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
    private static final ProviderCredentials SHARED_GOOGLE = new ProviderCredentials(IntegrationProvider.GOOGLE,
            CredentialMode.SHARED, "uncava-google-client", "uncava-google-secret", null);

    private final MailboxConnectionRepository connections = mock(MailboxConnectionRepository.class);
    private final ProviderCredentialsResolver resolver = mock(ProviderCredentialsResolver.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ScriptedTokenClient tokenClient = new ScriptedTokenClient();
    private final MutableClock clock = new MutableClock(NOW);
    private final ReversingCipher cipher = new ReversingCipher();

    private MailboxConnection connection;
    private String grantId;
    private MailboxTokens tokens;

    @BeforeEach
    void connectAMailbox() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        grantId = MailboxGrants.mintDirect("google");
        connection = MailboxConnection.connected(workspaceId, userId,
                new GrantedMailbox(grantId, "yara@firm.example", "google", "refresh-1"),
                cipher.encrypt("refresh-1", MailboxConnection.refreshTokenContext(workspaceId, userId)), 50, NOW);
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        connection.holdRecallCalendar("recall-calendar-1");
        when(connections.findByGrantId(grantId)).thenReturn(List.of(connection));
        when(connections.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(resolver.resolve(workspaceId, IntegrationProvider.GOOGLE)).thenReturn(Optional.of(SHARED_GOOGLE));
        tokens = new MailboxTokens(connections, resolver, cipher, tokenClient,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), events, clock);
    }

    @Test
    @DisplayName("a refresh spends the decrypted refresh token with the workspace's app and answers the access token")
    void aRefreshSucceeds() {
        tokenClient.answer(new RefreshedAccessToken("access-1", Duration.ofHours(1), null));

        assertThat(tokens.accessToken(grantId)).isEqualTo("access-1");
        assertThat(tokenClient.spentTokens).containsExactly("refresh-1");
        assertThat(tokenClient.apps).containsExactly(SHARED_GOOGLE);
    }

    @Test
    @DisplayName("the access token is reused until shortly before it expires, then refreshed again")
    void theCachedTokenIsReused() {
        tokenClient.answer(new RefreshedAccessToken("access-1", Duration.ofHours(1), null));
        tokens.accessToken(grantId);

        clock.advance(Duration.ofMinutes(50));
        assertThat(tokens.accessToken(grantId)).isEqualTo("access-1");
        assertThat(tokenClient.spentTokens).hasSize(1);

        clock.advance(Duration.ofMinutes(9));
        tokenClient.answer(new RefreshedAccessToken("access-2", Duration.ofHours(1), null));
        assertThat(tokens.accessToken(grantId)).isEqualTo("access-2");
        assertThat(tokenClient.spentTokens).hasSize(2);
    }

    @Test
    @DisplayName("a refused refresh marks the mailbox for reconnecting and releases its Recall calendar")
    void aRefusedRefreshWithdrawsAccess() {
        tokenClient.refuse();

        assertThatThrownBy(() -> tokens.accessToken(grantId))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.CREDENTIALS));
        assertThat(connection.getStatus()).isEqualTo(MailboxStatus.ERROR);
        assertThat(connection.getRecallCalendarId()).isNull();
        verify(events).publishEvent(new RecallCalendarReleased("recall-calendar-1"));

        tokenClient.answer(new RefreshedAccessToken("access-1", Duration.ofHours(1), null));
        assertThatThrownBy(() -> tokens.accessToken(grantId)).isInstanceOf(VendorException.class);
        assertThat(tokenClient.spentTokens).hasSize(1);
    }

    @Test
    @DisplayName("a rotated refresh token replaces the stored one, sealed")
    void aRotatedTokenIsKept() {
        tokenClient.answer(new RefreshedAccessToken("access-1", Duration.ofHours(1), "refresh-2"));

        tokens.accessToken(grantId);

        assertThat(connection.getRefreshTokenEncrypted()).isNotEqualTo("refresh-2");
        assertThat(cipher.decrypt(connection.getRefreshTokenEncrypted(), connection.refreshTokenContext()))
                .isEqualTo("refresh-2");
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a forgotten grant is refreshed again rather than served from memory")
    void aForgottenGrantIsRefreshedAgain() {
        tokenClient.answer(new RefreshedAccessToken("access-1", Duration.ofHours(1), null));
        tokens.accessToken(grantId);

        tokens.forget(grantId);
        tokens.accessToken(grantId);

        assertThat(tokenClient.spentTokens).hasSize(2);
    }

    private static final class ScriptedTokenClient implements ProviderTokenClient {

        private final List<String> spentTokens = new ArrayList<>();
        private final List<ProviderCredentials> apps = new ArrayList<>();
        private RefreshedAccessToken next;
        private boolean refusing;

        void answer(RefreshedAccessToken token) {
            this.next = token;
            this.refusing = false;
        }

        void refuse() {
            this.refusing = true;
        }

        @Override
        public RefreshedAccessToken refresh(ProviderCredentials credentials, String refreshToken) {
            spentTokens.add(refreshToken);
            apps.add(credentials);
            if (refusing) {
                throw new RefreshTokenRefused("invalid_grant");
            }
            return next;
        }
    }

    /** Not encryption: only what lets a test tell a stored value from the plaintext and read it back. */
    private static final class ReversingCipher implements SecretCipher {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String encrypt(String plaintext, EncryptionContext context) {
            return "sealed:" + context.purpose() + ":" + new StringBuilder(plaintext).reverse();
        }

        @Override
        public String decrypt(String ciphertext, EncryptionContext context) {
            String prefix = "sealed:" + context.purpose() + ":";
            assertThat(ciphertext).startsWith(prefix);
            return new StringBuilder(ciphertext.substring(prefix.length())).reverse().toString();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
