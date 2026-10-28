package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.Person;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a profile save did to each field, as the timeline shows it: the value as it read before and after, in the
 * words a researcher reads on the Career tab. Contacts are deliberately absent — an address quoted here would outlive
 * its removal from the ledger — and so is the employer, which is the position's mapping rather than the person's.
 */
final class ProfileChanges {

    /** The details key the changes are stored under, and read back from by {@code PersonTimelineService}. */
    static final String DETAIL_KEY = "changes";

    private static final int FREE_TEXT_LENGTH = 140;

    private final Map<String, String> before;
    private final List<?> careerBefore;

    private ProfileChanges(Person person) {
        this.before = valuesOf(person);
        this.careerBefore = List.copyOf(person.getProfile().career());
    }

    /** Taken before the save writes anything. */
    static ProfileChanges before(Person person) {
        return new ProfileChanges(person);
    }

    /** Each changed field in the Career tab's order; empty when the save changed nothing it records. */
    List<Map<String, String>> after(Person person) {
        Map<String, String> now = valuesOf(person);
        List<Map<String, String>> changed = new ArrayList<>();
        now.forEach((field, value) -> {
            String was = before.get(field);
            if (!Objects.equals(was, value)) {
                changed.add(change(field, was, value));
            }
        });
        List<?> careerNow = person.getProfile().career();
        if (!careerBefore.equals(careerNow)) {
            String was = rolesOf(careerBefore);
            String is = rolesOf(careerNow);
            changed.add(was.equals(is) ? change("career", null, null) : change("career", was, is));
        }
        return changed;
    }

    private static Map<String, String> valuesOf(Person person) {
        CandidateCompensation pay = person.compensation();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("fullName", person.getFullName());
        values.put("title", person.getTitle());
        values.put("seniority", person.getSeniorityLevel() == null ? null : person.getSeniorityLevel().value());
        values.put("location", joined(person.getLocationCity(), person.getLocationCountry()));
        values.put("nationality", person.getNationality());
        values.put("gender", person.getGender() == null ? null : capitalised(person.getGender().value()));
        values.put("yearsExperience", person.getYearsExperience() == null ? null
                : String.valueOf(person.getYearsExperience()));
        values.put("linkedinUrl", person.getLinkedinUrl());
        values.put("summary", clipped(person.getSummary()));
        values.put("currency", pay.currency());
        values.put("baseSalary", money(pay.currency(), pay.baseSalary()));
        values.put("bonus", money(pay.currency(), pay.bonus()));
        values.put("allowances", money(pay.currency(), pay.allowances()));
        values.put("longTermIncentive", money(pay.currency(), pay.longTermIncentive()));
        values.put("noticePeriod", pay.noticePeriod());
        values.put("languages", person.getProfile().languages().isEmpty() ? null
                : String.join(", ", person.getProfile().languages()));
        return values;
    }

    /** Null values are kept as keys, so a cleared field reads as cleared rather than as missing. */
    private static Map<String, String> change(String field, String from, String to) {
        Map<String, String> change = new LinkedHashMap<>();
        change.put("field", field);
        change.put("from", from);
        change.put("to", to);
        return change;
    }

    private static String money(String currency, Long amount) {
        if (amount == null) {
            return null;
        }
        String figure = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(amount);
        return currency == null ? figure : currency + " " + figure;
    }

    private static String rolesOf(Collection<?> career) {
        return career.size() == 1 ? "1 role" : career.size() + " roles";
    }

    private static String joined(String city, String country) {
        if (city == null) {
            return country;
        }
        return country == null ? city : city + ", " + country;
    }

    private static String clipped(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= FREE_TEXT_LENGTH ? flat : flat.substring(0, FREE_TEXT_LENGTH - 1).strip() + "…";
    }

    private static String capitalised(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
