package app.lightmove.api;

import app.lightmove.api.billing.payment.model.CreditsCheckout;
import app.lightmove.api.billing.payment.model.PaymentEvent;
import app.lightmove.api.billing.payment.model.SeatQuantityChange;
import app.lightmove.api.billing.payment.model.SubscriptionCheckout;
import app.lightmove.api.billing.payment.service.PaymentGateway;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A Stripe that never leaves the JVM: it remembers every customer and Checkout asked of it, and a webhook delivery
 * signed {@link #VALID_SIGNATURE} reads as whichever event a test queued with {@link #nextEvent}. A subscription's
 * seat quantity is whatever a test said it holds, then whatever the app last set.
 */
public class RecordingPaymentGateway implements PaymentGateway {

    public static final String VALID_SIGNATURE = "t=1,v1=signed-by-stripe";

    private final List<UUID> customersCreated = new CopyOnWriteArrayList<>();
    private final List<SubscriptionCheckout> subscriptionCheckouts = new CopyOnWriteArrayList<>();
    private final List<CreditsCheckout> creditsCheckouts = new CopyOnWriteArrayList<>();
    private final Map<String, String> openSubscriptionCheckouts = new ConcurrentHashMap<>();
    private final List<String> expiredCheckouts = new CopyOnWriteArrayList<>();
    private final Set<String> customersPaying = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> seats = new ConcurrentHashMap<>();
    private final AtomicInteger seatUpdatesRefused = new AtomicInteger();
    private volatile boolean refusingSeatUpdates;
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile PaymentEvent nextEvent;

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String createCustomer(UUID workspaceId, String name) {
        customersCreated.add(workspaceId);
        return "cus_" + UUID.randomUUID();
    }

    @Override
    public boolean hasLiveSubscription(String customerId) {
        return customersPaying.contains(customerId);
    }

    @Override
    public String subscriptionCheckout(SubscriptionCheckout checkout) {
        subscriptionCheckouts.add(checkout);
        String url = "https://checkout.stripe.test/c/" + sequence.incrementAndGet();
        String replaced = openSubscriptionCheckouts.put(checkout.customerId(), url);
        if (replaced != null) {
            expiredCheckouts.add(replaced);
        }
        return url;
    }

    @Override
    public String creditsCheckout(CreditsCheckout checkout) {
        creditsCheckouts.add(checkout);
        return "https://checkout.stripe.test/c/" + sequence.incrementAndGet();
    }

    @Override
    public String portal(String customerId, String returnUrl) {
        return "https://billing.stripe.test/p/" + customerId;
    }

    @Override
    public SeatQuantityChange updateSeats(String subscriptionId, long quantity) {
        if (refusingSeatUpdates) {
            seatUpdatesRefused.incrementAndGet();
            throw ApiException.of(ErrorCode.BILLING_UNAVAILABLE);
        }
        Long previous = seats.put(subscriptionId, quantity);
        return new SeatQuantityChange(previous == null ? quantity : previous, quantity);
    }

    @Override
    public PaymentEvent eventOf(byte[] payload, String signature) {
        if (!VALID_SIGNATURE.equals(signature) || nextEvent == null) {
            throw ApiException.of(ErrorCode.BILLING_WEBHOOK_REJECTED);
        }
        return nextEvent;
    }

    /** A subscription Stripe has made for the customer but whose events have not reached us. */
    public void customerPays(String customerId) {
        customersPaying.add(customerId);
    }

    public void subscriptionHolds(String subscriptionId, long quantity) {
        seats.put(subscriptionId, quantity);
    }

    public Long seatsOf(String subscriptionId) {
        return seats.get(subscriptionId);
    }

    public void refuseSeatUpdates(boolean refusing) {
        this.refusingSeatUpdates = refusing;
    }

    public int seatUpdatesRefused() {
        return seatUpdatesRefused.get();
    }

    public boolean wasExpired(String checkoutUrl) {
        return expiredCheckouts.contains(checkoutUrl);
    }

    public void nextEvent(PaymentEvent event) {
        this.nextEvent = event;
    }

    public long customersCreatedFor(UUID workspaceId) {
        return customersCreated.stream().filter(workspaceId::equals).count();
    }

    public List<SubscriptionCheckout> subscriptionCheckoutsOf(UUID workspaceId) {
        return subscriptionCheckouts.stream().filter(checkout -> checkout.workspaceId().equals(workspaceId)).toList();
    }

    public List<CreditsCheckout> creditsCheckoutsOf(UUID workspaceId) {
        return creditsCheckouts.stream().filter(checkout -> checkout.workspaceId().equals(workspaceId)).toList();
    }

    @TestConfiguration
    public static class Config {

        /** {@code @Primary} so it wins over the unconfigured gateway a test profile without a Stripe key builds. */
        @Bean
        @Primary
        public RecordingPaymentGateway recordingPaymentGateway() {
            return new RecordingPaymentGateway();
        }
    }
}
