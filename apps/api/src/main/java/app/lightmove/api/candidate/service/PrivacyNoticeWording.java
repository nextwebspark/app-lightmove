package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.LawfulBasis;

/**
 * How a privacy notice, and every outreach email's footer, words where a person's details came from and why they are
 * held (#923, #924). Fixed phrases a person can read, never the firm's internal names for its doors.
 */
public final class PrivacyNoticeWording {

    private PrivacyNoticeWording() {
    }

    public static String sourceOf(CandidateSource source) {
        return switch (source) {
            case MANUAL -> "our own research";
            case CSV -> "a list our team imported from its own research";
            case EXTENSION -> "your public professional profile";
            case AI_SOURCED, PEOPLE_SEARCH -> "a search of public professional profiles";
        };
    }

    public static String basisOf(LawfulBasis basis) {
        return switch (basis) {
            case LEGITIMATE_INTEREST -> "our legitimate interest in finding people for senior roles";
            case CONSENT -> "your consent";
            case CONTRACT -> "a contract with you";
        };
    }
}
