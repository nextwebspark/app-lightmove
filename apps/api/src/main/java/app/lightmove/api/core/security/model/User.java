package app.lightmove.api.core.security.model;
import app.lightmove.api.core.security.constant.UserStatus;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A person who can sign in. Tenant-agnostic: workspaces are reached only through {@code WorkspaceMember}. */
@Entity
@Table(name = "app_lm_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** Null for a federated-only user. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Setter
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /** Descriptive only; authority is the workspace role. */
    @Setter
    @Column(length = 120)
    private String title;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** An uppercased registration id; no setter, as it is written only with its picture by {@link #adoptAvatarFrom}. */
    @Column(name = "avatar_source", length = 32)
    private String avatarSource;

    @Setter
    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Dubai";

    @Setter
    @Column(nullable = false, length = 16)
    private String locale = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "privacy_policy_version", length = 32)
    private String privacyPolicyVersion;

    /**
     * Only the current picture's source may replace it; anyone else may only fill an empty one —
     * otherwise a photo-less account's monogram replaced a real photo. A null source predates the column.
     *
     * @return whether the picture was taken
     */
    public boolean adoptAvatarFrom(String source, String url) {
        if (url == null || source == null) {
            return false;
        }
        boolean entitled = avatarUrl == null || avatarUrl.isBlank()
                || avatarSource == null
                || avatarSource.equals(source);
        if (!entitled) {
            return false;
        }

        this.avatarUrl = url;
        this.avatarSource = source;
        return true;
    }

    /** The caller hashes; the domain never sees a plaintext credential. */
    public static User registerLocal(String email, String passwordHash, String fullName,
                                     Instant termsAcceptedAt, String privacyPolicyVersion) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.status = UserStatus.PENDING_VERIFICATION;
        user.termsAcceptedAt = termsAcceptedAt;
        user.privacyPolicyVersion = privacyPolicyVersion;
        return user;
    }

    /** Starts verified and active: the provider has already proven the address. */
    public static User registerFederated(String email, String fullName, String avatarUrl,
                                         String avatarSource, Instant verifiedAt,
                                         String privacyPolicyVersion) {
        User user = new User();
        user.email = email;
        user.fullName = fullName;
        user.avatarUrl = avatarUrl;
        user.avatarSource = avatarUrl == null ? null : avatarSource;
        user.status = UserStatus.ACTIVE;
        user.emailVerifiedAt = verifiedAt;
        user.termsAcceptedAt = verifiedAt;
        user.privacyPolicyVersion = privacyPolicyVersion;
        return user;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void markEmailVerified(Instant now) {
        if (isEmailVerified()) {
            return;
        }
        this.emailVerifiedAt = now;
        if (status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /** A fixed lock window, not escalating: an indefinite lock hands an attacker a denial of service. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockDuration);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void attachLocalPassword(String passwordHash) {
        if (hasPassword()) {
            throw new IllegalStateException("User already has a password");
        }
        this.passwordHash = passwordHash;
    }
}
