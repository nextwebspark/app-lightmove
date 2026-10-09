package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.dto.CreditGrantRequest;
import app.lightmove.api.billing.credit.dto.CreditGrantResponse;
import app.lightmove.api.billing.credit.model.CreditBalanceSummary;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.plan.service.BillingWorkspaces;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Credits a platform admin gives a workspace by hand, each one audited. */
@Service
@RequiredArgsConstructor
public class PlatformCreditGrantService {

    private final CreditLedger ledger;
    private final BillingWorkspaces workspaces;
    private final AuditService audit;
    private final Clock clock;

    public CreditGrantResponse grant(UUID actorId, UUID workspaceId, CreditGrantRequest request,
                                     HttpServletRequest httpRequest) {
        BigDecimal costPerCredit = validatedCostPerCredit(request);
        if (request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "expiresAt", "Choose an expiry in the future");
        }
        workspaces.requireExists(workspaceId);

        String externalRef = blankToNull(request.externalRef());
        CreditGrantReceipt receipt = ledger.grant(new CreditGrantCommand(workspaceId, request.source(),
                request.credits(), null, request.expiresAt(), costPerCredit, externalRef, actorId,
                blankToNull(request.note())));
        if (!receipt.alreadyGranted()) {
            audit.event(PlatformEventType.CREDITS_GRANTED).actor(actorId).workspace(workspaceId)
                    .target("credit_grant", receipt.grantId())
                    .detail("source", receipt.source().name())
                    .detail("credits", receipt.credits())
                    .detail("filsPerCredit", costPerCredit.toPlainString())
                    .detailIfPresent("expiresAt", receipt.expiresAt() == null ? null : receipt.expiresAt().toString())
                    .detailIfPresent("externalRef", externalRef)
                    .from(httpRequest)
                    .record();
        }
        CreditBalanceSummary balance = ledger.balanceOf(workspaceId);
        return new CreditGrantResponse(receipt.grantId(), receipt.source(), receipt.credits(), receipt.expiresAt(),
                receipt.alreadyGranted(), balance.available(), balance.held());
    }

    /** A manual grant is revenue and must say what each credit cost; a free one must not claim a cost. */
    private static BigDecimal validatedCostPerCredit(CreditGrantRequest request) {
        if (!request.source().isGivenByHand()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "source",
                    "Only manual, promo and goodwill credits are granted by hand");
        }
        if (request.source() == CreditGrantSource.MANUAL) {
            if (request.filsPerCredit() == null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "filsPerCredit",
                        "Enter what each credit cost the customer");
            }
            return request.filsPerCredit();
        }
        if (request.filsPerCredit() != null && request.filsPerCredit().signum() != 0) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "filsPerCredit",
                    "Promo and goodwill credits are free");
        }
        return BigDecimal.ZERO;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
