package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.PaymentCard;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.SeatQuantityChange;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.core.config.StripeSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionListParams;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe over {@code stripe-java}, whose release pins the API version. Every Checkout collects the address and TRN
 * Stripe Tax needs for UAE VAT; webhooks are read by {@link StripeEventReader}.
 */
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private static final String WORKSPACE_KEY = "workspace_id";
    private static final Set<String> LIVE_STATUSES = Set.of("active", "trialing", "past_due", "unpaid", "paused");

    private final StripeClient client;
    private final StripeEventReader reader;

    public StripePaymentGateway(StripeSettings settings) {
        this.client = StripeClient.builder()
                .setApiKey(settings.secretKey())
                .setConnectTimeout(10_000)
                .setReadTimeout(30_000)
                .setMaxNetworkRetries(2)
                .build();
        this.reader = new StripeEventReader(settings.webhookSecret());
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    /** Keyed on the name too: Stripe refuses a key reused with other parameters, as after a rename. */
    @Override
    public String createCustomer(UUID workspaceId, String name) {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(name)
                .putMetadata(WORKSPACE_KEY, workspaceId.toString())
                .build();
        UUID nameKey = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        RequestOptions once = RequestOptions.builder()
                .setIdempotencyKey("customer-" + workspaceId + "-" + nameKey)
                .build();
        try {
            return client.v1().customers().create(params, once).getId();
        } catch (StripeException failure) {
            throw unavailable("create a customer", failure);
        }
    }

    @Override
    public boolean hasLiveSubscription(String customerId) {
        SubscriptionListParams params = SubscriptionListParams.builder()
                .setCustomer(customerId)
                .setStatus(SubscriptionListParams.Status.ALL)
                .setLimit(100L)
                .build();
        try {
            return client.v1().subscriptions().list(params).getData().stream()
                    .anyMatch(subscription -> LIVE_STATUSES.contains(subscription.getStatus()));
        } catch (StripeException failure) {
            throw unavailable("list the customer's subscriptions", failure);
        }
    }

    @Override
    public String subscriptionCheckout(SubscriptionCheckout checkout) {
        expireOpenSubscriptionCheckouts(checkout.customerId());
        SessionCreateParams params = taxed(SessionCreateParams.builder())
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(checkout.customerId())
                .setClientReferenceId(checkout.workspaceId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(checkout.priceId())
                        .setQuantity(checkout.seats())
                        .build())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata(WORKSPACE_KEY, checkout.workspaceId().toString())
                        .build())
                .putMetadata(WORKSPACE_KEY, checkout.workspaceId().toString())
                .setSuccessUrl(checkout.successUrl())
                .setCancelUrl(checkout.cancelUrl())
                .build();
        return sessionUrl(params, "open a subscription checkout");
    }

    @Override
    public String creditsCheckout(CreditsCheckout checkout) {
        Map<String, String> metadata = Map.of(
                WORKSPACE_KEY, checkout.workspaceId().toString(),
                StripeEventReader.PACK_KEY, checkout.packCode());
        SessionCreateParams params = taxed(SessionCreateParams.builder())
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomer(checkout.customerId())
                .setClientReferenceId(checkout.workspaceId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(checkout.priceId())
                        .setQuantity(1L)
                        .build())
                .setInvoiceCreation(SessionCreateParams.InvoiceCreation.builder().setEnabled(true).build())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().putAllMetadata(metadata).build())
                .putAllMetadata(metadata)
                .setSuccessUrl(checkout.successUrl())
                .setCancelUrl(checkout.cancelUrl())
                .build();
        return sessionUrl(params, "open a credits checkout");
    }

    @Override
    public String portal(String customerId, String returnUrl) {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .build();
        try {
            return client.v1().billingPortal().sessions().create(params).getUrl();
        } catch (StripeException failure) {
            throw unavailable("open the customer portal", failure);
        }
    }

    @Override
    public long seatsOf(String subscriptionId) {
        try {
            return seatOf(subscriptionId).getQuantity();
        } catch (StripeException failure) {
            throw unavailable("read the seat quantity", failure);
        }
    }

    /** Absolute, so a sync run twice sets one quantity once; no idempotency key, which would replay a stale quantity. */
    @Override
    public SeatQuantityChange updateSeats(String subscriptionId, long seats) {
        try {
            SubscriptionItem seat = seatOf(subscriptionId);
            long previous = seat.getQuantity();
            if (previous == seats) {
                return new SeatQuantityChange(previous, seats);
            }
            client.v1().subscriptions().update(subscriptionId, SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder().setId(seat.getId()).setQuantity(seats).build())
                    .setProrationBehavior(seats > previous
                            ? SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE
                            : SubscriptionUpdateParams.ProrationBehavior.NONE)
                    .build());
            return new SeatQuantityChange(previous, seats);
        } catch (StripeException failure) {
            throw unavailable("change the seat quantity", failure);
        }
    }

    /** Quiet on a failure: the page then says "Paid by card", which is still true. */
    @Override
    public Optional<PaymentCard> cardOf(String subscriptionId) {
        SubscriptionRetrieveParams params = SubscriptionRetrieveParams.builder()
                .addExpand("default_payment_method")
                .addExpand("customer.invoice_settings.default_payment_method")
                .build();
        try {
            Subscription subscription = client.v1().subscriptions().retrieve(subscriptionId, params);
            PaymentMethod method = subscription.getDefaultPaymentMethodObject();
            if (method == null && subscription.getCustomerObject() != null
                    && subscription.getCustomerObject().getInvoiceSettings() != null) {
                method = subscription.getCustomerObject().getInvoiceSettings().getDefaultPaymentMethodObject();
            }
            if (method == null || method.getCard() == null) {
                return Optional.empty();
            }
            PaymentMethod.Card card = method.getCard();
            String brand = card.getDisplayBrand() != null ? card.getDisplayBrand() : card.getBrand();
            return Optional.of(new PaymentCard(brand, card.getLast4()));
        } catch (StripeException failure) {
            log.warn("Stripe refused to read the card of {}: {} (request {})", subscriptionId, failure.getMessage(),
                    failure.getRequestId());
            return Optional.empty();
        }
    }

    @Override
    public PaymentEvent eventOf(byte[] payload, String signature) {
        return reader.read(payload, signature);
    }

    private SubscriptionItem seatOf(String subscriptionId) throws StripeException {
        return client.v1().subscriptions().retrieve(subscriptionId).getItems().getData().getFirst();
    }

    private void expireOpenSubscriptionCheckouts(String customerId) {
        SessionListParams params = SessionListParams.builder()
                .setCustomer(customerId)
                .setStatus(SessionListParams.Status.OPEN)
                .setLimit(100L)
                .build();
        try {
            for (Session open : client.v1().checkout().sessions().list(params).getData()) {
                if ("subscription".equals(open.getMode())) {
                    client.v1().checkout().sessions().expire(open.getId());
                }
            }
        } catch (StripeException failure) {
            throw unavailable("expire an open checkout", failure);
        }
    }

    private static SessionCreateParams.Builder taxed(SessionCreateParams.Builder builder) {
        return builder
                .setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
                .setTaxIdCollection(SessionCreateParams.TaxIdCollection.builder().setEnabled(true).build())
                .setCustomerUpdate(SessionCreateParams.CustomerUpdate.builder()
                        .setAddress(SessionCreateParams.CustomerUpdate.Address.AUTO)
                        .setName(SessionCreateParams.CustomerUpdate.Name.AUTO)
                        .build());
    }

    private String sessionUrl(SessionCreateParams params, String attempted) {
        try {
            return client.v1().checkout().sessions().create(params).getUrl();
        } catch (StripeException failure) {
            throw unavailable(attempted, failure);
        }
    }

    private static ApiException unavailable(String attempted, StripeException failure) {
        log.warn("Stripe refused to {}: {} (request {})", attempted, failure.getMessage(), failure.getRequestId());
        return new ApiException(ErrorCode.BILLING_UNAVAILABLE, "Stripe refused to " + attempted);
    }
}
