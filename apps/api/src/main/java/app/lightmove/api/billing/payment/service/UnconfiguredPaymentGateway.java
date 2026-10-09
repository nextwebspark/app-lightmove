package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.PaymentCard;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.SeatQuantityChange;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Optional;
import java.util.UUID;

/** A deployment without Stripe: every workspace is invoiced, nothing is sold online and every webhook is refused. */
public class UnconfiguredPaymentGateway implements PaymentGateway {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public String createCustomer(UUID workspaceId, String name) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public boolean hasLiveSubscription(String customerId) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public String subscriptionCheckout(SubscriptionCheckout checkout) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public String creditsCheckout(CreditsCheckout checkout) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public String portal(String customerId, String returnUrl) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public long seatsOf(String subscriptionId) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public SeatQuantityChange updateSeats(String subscriptionId, long seats) {
        throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
    }

    @Override
    public Optional<PaymentCard> cardOf(String subscriptionId) {
        return Optional.empty();
    }

    @Override
    public PaymentEvent eventOf(byte[] payload, String signature) {
        throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
    }
}
