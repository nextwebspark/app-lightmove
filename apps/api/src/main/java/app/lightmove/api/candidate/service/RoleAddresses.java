package app.lightmove.api.candidate.service;

import java.util.Locale;
import java.util.Set;

/** A shared inbox (info@, hr@, …) names a team, not a person, so it is never the key two people are matched on. */
final class RoleAddresses {

    private static final Set<String> ROLE_LOCAL_PARTS = Set.of("info", "hr", "admin", "contact", "sales", "office",
            "careers", "jobs", "hello", "support", "team", "enquiries", "inquiries", "noreply", "no-reply",
            "recruitment", "recruiting", "talent", "mail", "general", "reception");

    private RoleAddresses() {
    }

    static boolean isRoleAddress(String email) {
        if (email == null) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return false;
        }
        String local = email.substring(0, at).trim().toLowerCase(Locale.ROOT);
        int tag = local.indexOf('+');
        return ROLE_LOCAL_PARTS.contains(tag < 0 ? local : local.substring(0, tag));
    }
}
