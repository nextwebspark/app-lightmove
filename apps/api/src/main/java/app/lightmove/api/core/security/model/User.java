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

/**
 * A person who can sign in.
 *
 * <p>Deliberately tenant-agnostic — a user belongs to zero or more workspaces through
 * {@code WorkspaceMember}, never directly. That is what lets one human join a second firm later
 * without us cloning their account.
 *
 * <p>The lockout and verification rules live on this class rather than in a service. They are
 * invariants of what it means to be a user, and keeping them here means no caller can forget to
 * apply them.
 */
@Entity
@Table(name = "app_lm_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** Null for a Google-only user: there is no local password to check, and that is not an error. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Setter
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    /** Job title, e.g. "Managing Partner". Descriptive; carries no authority. Authority is the workspace role. */
    @Setter
    @Column(length = 120)
    private String title;

    @Setter
    @Column(name = "avatar_url")
    private String avatarUrl;

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

    /** Signup with a password. The caller hashes; the domain never sees a plaintext credential. */
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

    /**
     * First sign-in through Google. Google has already proven the address, so the account starts
     * verified and active — sending our own confirmation email would be asking the user to prove
     * something we have just been told by a more authoritative source.
     */
    public static User registerFederated(String email, String fullName, String avatarUrl,
                                         Instant verifiedAt, String privacyPolicyVersion) {
        User user = new User();
        user.email = email;
        user.fullName = fullName;
        user.avatarUrl = avatarUrl;
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
            return; // Clicking the link twice is not an error worth surfacing to anyone.
        }
        this.emailVerifiedAt = now;
        if (status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /**
     * Counts a failed sign-in and locks the account once the threshold is crossed.
     *
     * <p>The lock is a fixed window rather than an escalating one: an attacker gains nothing from a
     * longer lock that the legitimate owner does not also suffer, and an indefinite lock hands them a
     * denial-of-service against any address they can guess.
     */
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

    /**
     * Attaches a local password to an account that has only ever used Google, so the same person can
     * later sign in either way rather than being locked out of their own workspace if Google access
     * is lost.
     */
    public void attachLocalPassword(String passwordHash) {
        if (hasPassword()) {
            throw new IllegalStateException("User already has a password");
        }
        this.passwordHash = passwordHash;
    }
}
