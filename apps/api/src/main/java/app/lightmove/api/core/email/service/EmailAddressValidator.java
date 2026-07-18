package app.lightmove.api.core.email.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import java.util.Hashtable;
import java.util.Locale;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks that an address is real and deliverable, and extracts the domain that groups its colleagues.
 *
 * <p>Four layers, cheapest first, all free:
 *
 * <ol>
 *   <li><b>Shape</b> — one {@code @}, a domain with a dot.
 *   <li><b>Not a consumer provider</b> — configurable; see {@link PublicEmailDomains}.
 *   <li><b>Not disposable</b> — see {@link DisposableDomains}.
 *   <li><b>MX records</b> — one DNS lookup proving the domain can receive mail. Catches the common
 *       typo ({@code nextwebspark.co}) before it costs a send, a bounce, and a user who never gets
 *       their link and blames us.
 * </ol>
 *
 * <p>What this deliberately does <b>not</b> do is prove the mailbox exists. That needs a paid API
 * (~$0.004–0.008/address) or an SMTP probe most providers now refuse. It is unnecessary because the
 * verification email <i>is</i> the proof: an address that cannot receive it never becomes a verified
 * account, and an unverified account reaches no workspace data.
 */
@Component
@Slf4j
public class EmailAddressValidator {

    /**
     * Deliberately permissive. RFC 5322 in full permits addresses no real mailbox uses, and rejecting
     * a valid-but-unusual address is a worse failure than accepting one that later bounces.
     */
    private static final String SHAPE = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$";

    private static final String DNS_TIMEOUT_MS = "3000";
    private static final String DNS_RETRIES = "1";

    private final LightMoveProperties.Email.Validation config;
    private final DisposableDomains disposableDomains;
    private final PublicEmailDomains configuredPublicDomains;

    public EmailAddressValidator(LightMoveProperties properties) {
        this.config = properties.email().validation();
        this.disposableDomains = new DisposableDomains(config.extraDisposableDomains());
        this.configuredPublicDomains =
                new PublicEmailDomains(config.publicDomains(), config.extraPublicDomains());
    }

    /**
     * Validates the address and returns its domain.
     *
     * @return the lower-cased domain, e.g. {@code nextwebspark.com}.
     * @throws ApiException if the address is malformed, undeliverable, disposable, or — when blocking
     *                      is enabled — a consumer provider rather than a company.
     */
    public String validateWorkEmail(String email) {
        if (email == null || !email.matches(SHAPE)) {
            throw ApiException.of(ErrorCode.EMAIL_UNDELIVERABLE);
        }

        String domain = domainOf(email);

        // Checked before deliverability: gmail.com has perfectly good MX records. It is refused for what
        // it means, not for whether it works.
        if (config.blockPublicDomains() && configuredPublicDomains.contains(domain)) {
            throw new ApiException(ErrorCode.EMAIL_NOT_WORK_ADDRESS, "Consumer email domain: " + domain);
        }

        if (config.blockDisposableDomains() && disposableDomains.contains(domain)) {
            throw new ApiException(ErrorCode.EMAIL_DISPOSABLE, "Disposable domain: " + domain);
        }

        if (config.mxCheckEnabled() && !hasMailExchanger(domain)) {
            throw new ApiException(ErrorCode.EMAIL_UNDELIVERABLE, "No MX record for: " + domain);
        }

        return domain;
    }

    /** The domain of an address. Assumes it has already been validated. */
    public static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    }

    /** Lower-cased and trimmed; {@code ""} for null. The canonical form emails are stored and matched in. */
    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * True if the domain publishes an MX record — or if we could not find out.
     *
     * <p>Failing open is deliberate. A resolver that is slow, rate-limited or briefly unreachable is
     * our problem, not the user's, and treating our own outage as "your email is fake" would block
     * every legitimate signup for as long as it lasted. An address that slips through merely fails to
     * receive its verification email, which is what would have happened anyway.
     */
    private boolean hasMailExchanger(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", DNS_TIMEOUT_MS);
        env.put("com.sun.jndi.dns.timeout.retries", DNS_RETRIES);

        InitialDirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(domain, new String[]{"MX"});
            return attributes.get("MX") != null;
        } catch (NamingException ex) {
            // NameNotFoundException means the domain genuinely has no MX; anything else means the
            // lookup failed. JNDI does not distinguish them reliably across resolvers, so the whole
            // class is treated as inconclusive and the address is let through.
            log.debug("MX lookup inconclusive for {}: {}", domain, ex.getMessage());
            return true;
        } finally {
            closeQuietly(context);
        }
    }

    private static void closeQuietly(InitialDirContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (NamingException ignored) {
            // Nothing useful to do, and nothing depends on it.
        }
    }
}
