package app.lightmove.api.enrichment.contact.service;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateEmail;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.core.config.ContactOutSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.core.resilience.service.VendorRetryPredicate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Looks an executive's contacts up through ContactOut — one GET per channel, so a plain
 * {@link RestClient} for the reason {@code ResendEmailSender} gives.
 *
 * <p>The two calls differ only in their query, and that difference is what keeps the bill honest: the
 * email call omits {@code include_phone} entirely and is charged one email credit, the phone call asks
 * for {@code email_type=none} and is charged one phone credit. Asking for both at once would spend
 * both on every press.
 *
 * <p>A profile the provider has nothing on answers 404, which costs nothing, so it is swallowed into
 * an empty answer rather than raised.
 */
@Slf4j
public class ContactOutContactFinder implements ContactFinder {

    /** An indexed lookup on their side, not a scrape — a second is already slow for it. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    static final String VENDOR = "contactout";

    private final RestClient client;
    private final VendorCallGuard guard;

    public ContactOutContactFinder(ContactOutSettings config, VendorClientFactory clientFactory,
                                   VendorRateLimiter rateLimiter, VendorCallGuard guard,
                                   RestClient.Builder builder) {
        this.guard = guard;
        this.client = clientFactory.create(VendorClientSpec.header(VENDOR, config.baseUrl(),
                "token", config.apiKey(), READ_TIMEOUT, config.requestsPerSecond()),
                builder, rateLimiter);
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public FoundEmails findEmails(String linkedinUrl) {
        ContactOutResponse answer = ask("people-linkedin-email", uri -> uri
                .path("/v1/people/linkedin")
                .queryParam("profile", linkedinUrl)
                .queryParam("email_type", "personal,work"));
        if (answer == null || answer.profile() == null) {
            return FoundEmails.none(VENDOR);
        }
        return new FoundEmails(VENDOR, toEmails(answer.profile()));
    }

    @Override
    @Retryable(
            predicate = VendorRetryPredicate.class,
            maxRetriesString = "${lightmove.resilience.max-retries}",
            delayString = "${lightmove.resilience.retry-delay}",
            jitterString = "${lightmove.resilience.retry-jitter}",
            multiplierString = "${lightmove.resilience.retry-multiplier}",
            maxDelayString = "${lightmove.resilience.retry-max-delay}")
    public FoundPhones findPhones(String linkedinUrl) {
        ContactOutResponse answer = ask("people-linkedin-phone", uri -> uri
                .path("/v1/people/linkedin")
                .queryParam("profile", linkedinUrl)
                .queryParam("email_type", "none")
                .queryParam("include_phone", true));
        if (answer == null || answer.profile() == null) {
            return FoundPhones.none(VENDOR);
        }
        return new FoundPhones(VENDOR, toPhones(answer.profile()));
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    private ContactOutResponse ask(String operation, UnaryOperator<UriBuilder> query) {
        try {
            return guard.call(VendorCall.of(VENDOR, operation), () -> client.get()
                    .uri(uri -> query.apply(uri).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContactOutResponse.class));
        } catch (VendorException failed) {
            if (failed.getKind() != VendorFailureKind.NOT_FOUND) {
                throw failed;
            }
            log.debug("ContactOut holds no contact record for this profile");
            return null;
        }
    }

    /**
     * Work addresses first, then personal, then anything the provider listed without saying which —
     * the order {@code FoundEmails.primary} promotes from, and one an address is never dropped by.
     */
    static List<CandidateEmail> toEmails(ContactOutProfile profile) {
        List<CandidateEmail> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String address : orEmpty(profile.workEmail())) {
            add(found, seen, address, CandidateEmail.WORK, verificationOf(profile.workEmailStatus(), address));
        }
        for (String address : orEmpty(profile.personalEmail())) {
            add(found, seen, address, CandidateEmail.PERSONAL, null);
        }
        for (String address : orEmpty(profile.email())) {
            add(found, seen, address, null, null);
        }
        return List.copyOf(found);
    }

    static List<String> toPhones(ContactOutProfile profile) {
        List<String> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String number : orEmpty(profile.phone())) {
            if (number == null || number.isBlank()) {
                continue;
            }
            // Providers mix E.164, national and dashed forms, so two spellings of one number would
            // otherwise both be kept. The digits are the identity; the spelling is what we store.
            if (seen.add(CandidateContact.keyOf(ContactChannel.PHONE, number))) {
                found.add(number.trim());
            }
        }
        return List.copyOf(found);
    }

    /**
     * ContactOut sends this as an object keyed by address when it has statuses and as an empty JSON
     * <i>array</i> when it has none, so binding it to a Map throws on the empty case.
     */
    private static String verificationOf(Object statuses, String address) {
        if (!(statuses instanceof Map<?, ?> byAddress)) {
            return null;
        }
        Object status = byAddress.get(address);
        return status == null ? null : String.valueOf(status);
    }

    private static void add(List<CandidateEmail> found, Set<String> seen, String address, String kind,
                            String status) {
        if (address == null || address.isBlank() || !seen.add(address.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        found.add(new CandidateEmail(address, kind, status));
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    record ContactOutResponse(ContactOutProfile profile) {}

    /** {@code workEmailStatus} is typed as whatever arrived — see {@link #verificationOf}. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ContactOutProfile(String url, List<String> email, List<String> workEmail,
                             List<String> personalEmail, Object workEmailStatus, List<String> phone) {}
}
