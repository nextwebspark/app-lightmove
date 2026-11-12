package app.lightmove.api.core.security.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One recovery code, as its SHA-256 only; spent by {@link MfaRecoveryCodeRepository#consume}, never read back. */
@Entity
@Table(name = "app_lm_user_mfa_recovery_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MfaRecoveryCode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MfaRecoveryCode issue(UUID userId, String codeHash, Instant now) {
        MfaRecoveryCode code = new MfaRecoveryCode();
        code.userId = userId;
        code.codeHash = codeHash;
        code.createdAt = now;
        return code;
    }
}
