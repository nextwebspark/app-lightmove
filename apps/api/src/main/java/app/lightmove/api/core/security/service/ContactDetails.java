package app.lightmove.api.core.security.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.util.regex.Pattern;

/** How a person is reached, as Settings → Profile takes it: blank clears a field, anything else must be what it says. */
final class ContactDetails {

    private static final Pattern PHONE_CHARACTERS = Pattern.compile("[0-9+()\\-. ]+");
    private static final int MIN_PHONE_DIGITS = 6;

    private ContactDetails() {
    }

    /** As typed — "+971 50 123 4567" and "050-1234567" are both how people write a number — once it reads as one. */
    static String phoneOrNull(String phone) {
        String typed = phone.strip().replaceAll("\\s+", " ");
        if (typed.isEmpty()) {
            return null;
        }
        long digits = typed.chars().filter(Character::isDigit).count();
        if (!PHONE_CHARACTERS.matcher(typed).matches() || digits < MIN_PHONE_DIGITS || typed.indexOf('+', 1) >= 0) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "phone", "Enter a phone number");
        }
        return typed;
    }

    /** Stored canonical, so a signature links the profile whatever spelling was pasted. */
    static String linkedinProfileOrNull(String url) {
        String typed = url.strip();
        if (typed.isEmpty()) {
            return null;
        }
        String withScheme = typed.matches("(?i)^https?://.*") ? typed : "https://" + typed;
        String slug = LinkedInUrls.profileSlugOrNull(withScheme);
        if (slug == null || slug.length() > 100) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "linkedinUrl",
                    "Enter your LinkedIn profile address, like linkedin.com/in/your-name");
        }
        return "https://www.linkedin.com/in/" + slug;
    }
}
