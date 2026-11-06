package app.lightmove.api.core.text.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.regex.Pattern;

/** A phone number as a person types their own — "+971 50 123 4567" and "050-1234567" both — once it reads as one. */
public final class PhoneNumbers {

    private static final Pattern PHONE_CHARACTERS = Pattern.compile("[0-9+()\\-. ]+");
    private static final int MIN_PHONE_DIGITS = 6;

    private PhoneNumbers() {
    }

    /** Blank is null; anything else is kept as typed, its spaces folded, or refused on {@code field}. */
    public static String typedOrNull(String phone, String field) {
        String typed = phone == null ? "" : phone.strip().replaceAll("\\s+", " ");
        if (typed.isEmpty()) {
            return null;
        }
        long digits = typed.chars().filter(Character::isDigit).count();
        if (!PHONE_CHARACTERS.matcher(typed).matches() || digits < MIN_PHONE_DIGITS || typed.indexOf('+', 1) >= 0) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field, "Enter a phone number");
        }
        return typed;
    }
}
