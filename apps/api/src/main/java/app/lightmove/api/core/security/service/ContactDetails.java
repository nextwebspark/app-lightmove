package app.lightmove.api.core.security.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.PhoneNumbers;

/** How a person is reached, as Settings → Profile takes it: blank clears a field, anything else must be what it says. */
final class ContactDetails {

    private ContactDetails() {
    }

    static String phoneOrNull(String phone) {
        return PhoneNumbers.typedOrNull(phone, "phone");
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
