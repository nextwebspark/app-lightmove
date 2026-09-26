package app.lightmove.api.core.email.service;

import app.lightmove.api.core.config.EmailValidationSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import java.util.Hashtable;
import java.util.Locale;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks an address's shape, that it is not a consumer or disposable domain, and that the domain has
 * MX records; returns the domain. The verification email, not this, proves the mailbox exists.
 */
@Component
@Slf4j
public class EmailAddressValidator {

    /** Deliberately permissive: rejecting an unusual valid address is worse than a later bounce. */
    private static final String SHAPE = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$";

    private static final String DNS_TIMEOUT_MS = "3000";
    private static final String DNS_RETRIES = "1";

    private final EmailValidationSettings config;
    private final DisposableDomains disposableDomains;
    private final PublicEmailDomains configuredPublicDomains;

    public EmailAddressValidator(LightMoveProperties properties) {
        this.config = properties.email().validation();
        this.disposableDomains = new DisposableDomains(config.extraDisposableDomains());
        this.configuredPublicDomains =
                new PublicEmailDomains(config.publicDomains(), config.extraPublicDomains());
    }

    /** @return the lower-cased domain, e.g. {@code nextwebspark.com} */
    public String validateWorkEmail(String email) {
        if (email == null || !email.matches(SHAPE)) {
            throw ApiException.of(ErrorCode.EMAIL_UNDELIVERABLE);
        }

        String domain = domainOf(email);

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

    /** Assumes the address has already been validated. */
    public static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    }

    /** The canonical stored and matched form; {@code ""} for null. */
    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * True if the domain can receive mail, or if the resolver told us nothing (fail open on an outage).
     * An answer is not an outage: catching every {@link NamingException} together let NXDOMAIN through,
     * the very typo this check exists to catch.
     */
    private boolean hasMailExchanger(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", DNS_TIMEOUT_MS);
        env.put("com.sun.jndi.dns.timeout.retries", DNS_RETRIES);

        InitialDirContext context = null;
        try {
            context = new InitialDirContext(env);
            return acceptsMail(context.getAttributes(domain, new String[]{"MX"}));
        } catch (NameNotFoundException ex) {
            log.debug("No such domain: {}", domain);
            return false;
        } catch (NamingException ex) {
            log.debug("MX lookup inconclusive for {}: {}", domain, ex.getMessage());
            return true;
        } finally {
            closeQuietly(context);
        }
    }

    /** Read, not counted: the single record {@code 0 .} declares no mail accepted (RFC 7505), as example.com does. */
    static boolean acceptsMail(Attributes attributes) throws NamingException {
        Attribute mailExchangers = attributes.get("MX");
        if (mailExchangers == null || mailExchangers.size() == 0) {
            return false;
        }

        for (int index = 0; index < mailExchangers.size(); index++) {
            if (!isNullMailExchanger(String.valueOf(mailExchangers.get(index)))) {
                return true;
            }
        }
        return false;
    }

    /** An MX record is {@code "<preference> <exchange>"}; RFC 7505's null MX names the root, ".". */
    private static boolean isNullMailExchanger(String record) {
        String exchange = record.trim();
        int separator = exchange.indexOf(' ');
        if (separator >= 0) {
            exchange = exchange.substring(separator + 1).trim();
        }
        return exchange.equals(".") || exchange.isEmpty();
    }

    private static void closeQuietly(InitialDirContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (NamingException ignored) {
        }
    }
}
