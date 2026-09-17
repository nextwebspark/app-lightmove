package app.lightmove.api.report.service;

import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import java.util.List;

/**
 * What one executive's disclosed package comes to, shared by the chapters that read it.
 *
 * <p>Chapter three sets these against the brief's band and chapter two takes a hub's median, so the
 * arithmetic lives here rather than twice: two chapters of one report quoting a different figure for
 * the same person is the failure this prevents.
 */
final class Packages {

    private Packages() {
    }

    /** Base plus allowances — what the executive is paid whatever the year does. */
    static long fixedOf(CandidateCompensationDto compensation) {
        return compensation.baseSalary() + orZero(compensation.allowances());
    }

    /** Fixed plus bonus and long-term incentive. */
    static long totalOf(CandidateCompensationDto compensation) {
        return fixedOf(compensation) + orZero(compensation.bonus()) + orZero(compensation.longTermIncentive());
    }

    /** A package is readable once a base salary is on file; the rest of the elements may be absent. */
    static boolean isDisclosed(CandidateCompensationDto compensation) {
        return compensation != null && compensation.baseSalary() != null;
    }

    /** A package recorded without a currency is taken to be in the brief's: the drawer offers no other default. */
    static boolean isInCurrency(CandidateCompensationDto compensation, String currency) {
        return compensation.currency() == null || compensation.currency().equalsIgnoreCase(currency);
    }

    /** The middle of a sorted run, averaging the two middles of an even one. Null for nothing to rank. */
    static Long medianOf(List<Long> figures) {
        if (figures.isEmpty()) {
            return null;
        }
        List<Long> sorted = figures.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static long orZero(Long figure) {
        return figure == null ? 0 : figure;
    }
}
