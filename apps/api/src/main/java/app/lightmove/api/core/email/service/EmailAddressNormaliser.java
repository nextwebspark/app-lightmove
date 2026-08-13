package app.lightmove.api.core.email.service;

import tools.jackson.databind.util.StdConverter;

/**
 * Canonicalises an email address as it is deserialized, before Bean Validation ever sees it.
 *
 * <p>Ordering is the whole point. Jakarta's {@code @Email} runs during argument resolution, and the
 * services' own {@link EmailAddressValidator#normalise} runs after the controller has already accepted
 * the request — so an address pasted with a trailing space, which mail clients and mobile keyboards
 * add routinely, was reported to the user as malformed by a normaliser that never got to see it.
 *
 * <p>Applied to address fields only, never to a password: trimming a secret changes the secret.
 */
public class EmailAddressNormaliser extends StdConverter<String, String> {

    @Override
    public String convert(String value) {
        return EmailAddressValidator.normalise(value);
    }
}
