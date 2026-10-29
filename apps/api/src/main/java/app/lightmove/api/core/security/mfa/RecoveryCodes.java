package app.lightmove.api.core.security.mfa;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The codes that stand in for a lost phone: ten per set, each 80 random bits written as sixteen Base32 characters
 * in groups of four. 80 bits is what lets an unsalted SHA-256 of one stand in the table, as every token does.
 */
public final class RecoveryCodes {

    public static final int PER_SET = 10;

    private static final int CHARACTERS = 16;
    private static final int GROUP = 4;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    public static List<String> newSet() {
        List<String> codes = new ArrayList<>(PER_SET);
        for (int index = 0; index < PER_SET; index++) {
            codes.add(newCode());
        }
        return codes;
    }

    /** What a person typed, as it was hashed: groups, spaces and case do not count. */
    public static String normalise(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean looksLikeOne(String normalised) {
        return normalised.length() == CHARACTERS && normalised.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(CHARACTERS + CHARACTERS / GROUP);
        for (int index = 0; index < CHARACTERS; index++) {
            if (index > 0 && index % GROUP == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
