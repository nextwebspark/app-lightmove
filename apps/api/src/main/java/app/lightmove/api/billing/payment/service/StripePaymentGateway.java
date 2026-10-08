package app.lightmove.api.billing.payment.service;

import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.StripeSubscriptionState;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.billing.plan.constant.SubscriptionStatus;
import app.lightmove.api.core.config.StripeSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe over {@code stripe-java}, whose release pins the API version. Every Checkout collects the address and TRN
 * Stripe Tax needs for UAE VAT and writes them back to the customer; the workspace rides each object's metadata for
 * a reader in Stripe's dashboard, while webhooks are matched on the customer alone.
 */
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    static final String WORKSPACE_KEY = "workspace_id";
    static final String PACK_KEY = "pack";
    static final String CREDITS_KEY = "credits";

    private static final Set<String> PERIOD_REASONS =
            Set.of("subscription_create", "subscription_cycle", "subscription_update");

    private final StripeClient client;
    private final String webhookSecret;

    public StripePaymentGateway(StripeSettings settings) {
        this.client = StripeClient.builder()
                .setApiKey(settings.secretKey())
                .setConnectTimeout(10_000)
                .setReadTimeout(30_000)
                .setMaxNetworkRetries(2)
                .build();
        this.webhookSecret = settings.webhookSecret();
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String createCustomer(UUID workspaceId, String name) {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(name)
                .putMetadata(WORKSPACE_KEY, workspaceId.toString())
                .build();
        RequestOptions once = RequestOptions.builder().setIdempotencyKey("customer-" + workspaceId).build();
        try {
            return client.v1().customers().create(params, once).getId();
        } catch (StripeException failure) {
            throw unavailable("create a customer", failure);
        }
    }

    @Override
    public String subscriptionCheckout(SubscriptionCheckout checkout) {
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
                PACK_KEY, checkout.packCode(),
                CREDITS_KEY, Long.toString(checkout.credits()));
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
    public PaymentEvent eventOf(byte[] payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || payload == null || signature == null) {
            throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
        }
        Event event;
        try {
            event = Webhook.constructEvent(new String(payload, StandardCharsets.UTF_8), signature, webhookSecret);
        } catch (SignatureVerificationException forged) {
            throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
        }
        Instant at = Instant.ofEpochSecond(event.getCreated());
        return switch (event.getType()) {
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    new PaymentEvent.SubscriptionChanged(event.getId(), event.getType(), at,
                            stateOf((Subscription) objectOf(event)));
            case "invoice.paid" -> invoicePaid(event, at, (Invoice) objectOf(event));
            case "invoice.payment_failed" -> paymentFailed(event, at, (Invoice) objectOf(event));
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    creditsPaid(event, at, (Session) objectOf(event));
            default -> new PaymentEvent.Ignored(event.getId(), event.getType(), at);
        };
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

    /** An event on an API version other than the pinned one is read anyway: every field read here exists on both. */
    private static StripeObject objectOf(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException unreadable) {
                throw new IllegalStateException("Stripe event " + event.getId() + " could not be read", unreadable);
            }
        });
    }

    private static StripeSubscriptionState stateOf(Subscription subscription) {
        SubscriptionItem seat = subscription.getItems().getData().getFirst();
        return new StripeSubscriptionState(subscription.getCustomer(), subscription.getId(),
                statusOf(subscription.getStatus()), seat.getPrice().getId(), seat.getQuantity(),
                Instant.ofEpochSecond(seat.getCurrentPeriodStart()), Instant.ofEpochSecond(seat.getCurrentPeriodEnd()));
    }

    private static PaymentEvent invoicePaid(Event event, Instant at, Invoice invoice) {
        String subscriptionId = subscriptionOf(invoice);
        if (subscriptionId == null) {
            return new PaymentEvent.Ignored(event.getId(), event.getType(), at);
        }
        StripeSubscriptionState seats = seatLineOf(invoice)
                .map(line -> new StripeSubscriptionState(invoice.getCustomer(), subscriptionId,
                        SubscriptionStatus.ACTIVE, line.getPricing().getPriceDetails().getPrice(), line.getQuantity(),
                        Instant.ofEpochSecond(line.getPeriod().getStart()),
                        Instant.ofEpochSecond(line.getPeriod().getEnd())))
                .orElse(null);
        boolean startsPeriod = seats != null && PERIOD_REASONS.contains(invoice.getBillingReason());
        return new PaymentEvent.InvoicePaid(event.getId(), event.getType(), at, invoice.getId(), invoice.getCustomer(),
                subscriptionId, seats, startsPeriod);
    }

    private static PaymentEvent paymentFailed(Event event, Instant at, Invoice invoice) {
        String subscriptionId = subscriptionOf(invoice);
        if (subscriptionId == null) {
            return new PaymentEvent.Ignored(event.getId(), event.getType(), at);
        }
        return new PaymentEvent.InvoicePaymentFailed(event.getId(), event.getType(), at, invoice.getCustomer(),
                subscriptionId);
    }

    /** A subscription's own Checkout completes too; only a paid pack of ours carries credits. */
    private static PaymentEvent creditsPaid(Event event, Instant at, Session session) {
        Map<String, String> metadata = session.getMetadata() == null ? Map.of() : session.getMetadata();
        if (!"payment".equals(session.getMode()) || !"paid".equals(session.getPaymentStatus())
                || !metadata.containsKey(PACK_KEY) || !metadata.containsKey(CREDITS_KEY)) {
            return new PaymentEvent.Ignored(event.getId(), event.getType(), at);
        }
        long tax = session.getTotalDetails() == null || session.getTotalDetails().getAmountTax() == null
                ? 0 : session.getTotalDetails().getAmountTax();
        String paymentRef = session.getPaymentIntent() != null ? session.getPaymentIntent() : session.getId();
        return new PaymentEvent.CreditsPaid(event.getId(), event.getType(), at, session.getCustomer(), paymentRef,
                metadata.get(PACK_KEY), Long.parseLong(metadata.get(CREDITS_KEY)), session.getAmountTotal() - tax);
    }

    private static String subscriptionOf(Invoice invoice) {
        if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
            return null;
        }
        return invoice.getParent().getSubscriptionDetails().getSubscription();
    }

    /** The line billing the seats for a period, as opposed to a proration of a change made during one. */
    private static Optional<InvoiceLineItem> seatLineOf(Invoice invoice) {
        if (invoice.getLines() == null) {
            return Optional.empty();
        }
        return invoice.getLines().getData().stream()
                .filter(line -> line.getParent() != null && line.getParent().getSubscriptionItemDetails() != null)
                .filter(line -> !Boolean.TRUE.equals(line.getParent().getSubscriptionItemDetails().getProration()))
                .filter(line -> line.getPricing() != null && line.getPricing().getPriceDetails() != null)
                .findFirst();
    }

    private static SubscriptionStatus statusOf(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "paused" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "incomplete_expired" -> SubscriptionStatus.CANCELLED;
            default -> null;
        };
    }

    private static ApiException unavailable(String attempted, StripeException failure) {
        log.warn("Stripe refused to {}: {} (request {})", attempted, failure.getMessage(), failure.getRequestId());
        return new ApiException(ErrorCode.BILLING_UNAVAILABLE, "Stripe refused to " + attempted);
    }
}
