package app.lightmove.api.core.text.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Orders text the way a reader expects a list to read: by its letters before their case or accents, and a run of
 * digits by its value, so "Fund 2" comes before "Fund 10". Leading spaces, quotes and punctuation are not read — an
 * imported {@code "Aramco"} sorts under A, not ahead of every name in the list.
 */
public final class NaturalTextOrder implements Comparator<String> {

    public static final NaturalTextOrder INSTANCE = new NaturalTextOrder();

    private static final Collator LETTERS = collator(Collator.PRIMARY);

    private static final Collator CASE_AND_ACCENTS = collator(Collator.TERTIARY);

    private NaturalTextOrder() {
    }

    @Override
    public int compare(String first, String second) {
        String left = readable(first);
        String right = readable(second);
        int leftAt = 0;
        int rightAt = 0;
        while (leftAt < left.length() && rightAt < right.length()) {
            boolean leftDigits = isDigit(left.charAt(leftAt));
            boolean rightDigits = isDigit(right.charAt(rightAt));
            if (leftDigits != rightDigits) {
                return leftDigits ? -1 : 1;
            }
            int leftEnd = runEnd(left, leftAt, leftDigits);
            int rightEnd = runEnd(right, rightAt, rightDigits);
            String leftRun = left.substring(leftAt, leftEnd);
            String rightRun = right.substring(rightAt, rightEnd);
            int order = leftDigits ? compareNumbers(leftRun, rightRun) : LETTERS.compare(leftRun, rightRun);
            if (order != 0) {
                return order;
            }
            leftAt = leftEnd;
            rightAt = rightEnd;
        }
        int remaining = Integer.compare(left.length() - leftAt, right.length() - rightAt);
        if (remaining != 0) {
            return remaining;
        }
        return CASE_AND_ACCENTS.compare(left, right);
    }

    private static String readable(String text) {
        String stripped = text.strip();
        int start = 0;
        while (start < stripped.length() && !Character.isLetterOrDigit(stripped.codePointAt(start))) {
            start += Character.charCount(stripped.codePointAt(start));
        }
        return start == stripped.length() ? stripped : stripped.substring(start);
    }

    private static int runEnd(String text, int from, boolean digits) {
        int at = from;
        while (at < text.length() && isDigit(text.charAt(at)) == digits) {
            at++;
        }
        return at;
    }

    private static int compareNumbers(String left, String right) {
        String leftValue = withoutLeadingZeros(left);
        String rightValue = withoutLeadingZeros(right);
        int magnitude = Integer.compare(leftValue.length(), rightValue.length());
        return magnitude != 0 ? magnitude : leftValue.compareTo(rightValue);
    }

    private static String withoutLeadingZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static Collator collator(int strength) {
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        collator.setStrength(strength);
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        return collator;
    }
}
