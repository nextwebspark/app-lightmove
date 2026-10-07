package app.lightmove.api.billing.credit.service;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.billing.credit.dto.CreditGrantRequest;
import app.lightmove.api.billing.credit.dto.CreditGrantResponse;
import app.lightmove.api.billing.credit.model.CreditBalanceSummary;
import app.lightmove.api.billing.credit.model.CreditGrantCommand;
import app.lightmove.api.billing.credit.model.CreditGrantReceipt;
import app.lightmove.api.billing.plan.repository.WorkspaceSubscriptionRepository;
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

/** Credits given to a workspace by hand, for an invoiced customer or as a gesture; each one audited. */
@Service
@RequiredArgsConstructor
public class PlatformCreditGrantService {

    private final CreditLedger ledger;
    private final WorkspaceSubscriptionRepository subscriptions;
    private final AuditService audit;
    private final Clock clock;

    public CreditGrantResponse grant(UUID actorId, UUID workspaceId, CreditGrantRequest request,
                                     HttpServletRequest httpRequest) {
        if (!request.source().isGivenByHand()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "source",
                    "Only manual, promo and goodwill credits are granted by hand");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "expiresAt", "Choose an expiry in the future");
        }
        if (!subscriptions.workspaceExists(workspaceId)) {
            throw ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        CreditGrantReceipt receipt = ledger.grant(new CreditGrantCommand(workspaceId, request.source(),
                request.credits(), null, request.expiresAt(), costPerCredit(request), blankToNull(request.externalRef()),
                actorId, blankToNull(request.note())));
        if (!receipt.alreadyGranted()) {
            audit.event(PlatformEventType.CREDITS_GRANTED).actor(actorId).workspace(workspaceId)
                    .target("credit_grant", receipt.grantId())
                    .detail("source", receipt.source().name())
                    .detail("credits", receipt.credits())
                    .from(httpRequest)
                    .record();
        }
        CreditBalanceSummary balance = ledger.balanceOf(workspaceId);
        return new CreditGrantResponse(receipt.grantId(), receipt.source(), receipt.credits(), receipt.expiresAt(),
                receipt.alreadyGranted(), balance.available(), balance.held());
    }

    private static BigDecimal costPerCredit(CreditGrantRequest request) {
        if (request.source() != CreditGrantSource.MANUAL || request.filsPerCredit() == null) {
            return BigDecimal.ZERO;
        }
        return request.filsPerCredit();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
