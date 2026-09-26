package app.lightmove.api.core.email.service;

import tools.jackson.databind.util.StdConverter;

/**
 * Canonicalises an email address during deserialization, before {@code @Email} runs — otherwise a
 * pasted trailing space was reported as malformed before {@link EmailAddressValidator#normalise} ran.
 * Address fields only, never a password: trimming a secret changes the secret.
 */
public class EmailAddressNormaliser extends StdConverter<String, String> {

    @Override
    public String convert(String value) {
        return EmailAddressValidator.normalise(value);
    }
}
