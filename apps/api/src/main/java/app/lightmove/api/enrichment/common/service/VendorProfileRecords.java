package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.VendorProfileRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A vendor's own record made into what the person keeps (V229): the record as sent, its typed reading, and when the
 * vendor itself last read the profile — Bright Data's {@code timestamp}, Business Search's {@code scraped_at} (epoch
 * milliseconds), ContactOut's {@code updated_at} ("2026-09-22 00:00:00"). Null for a record that is not JSON.
 */
public final class VendorProfileRecords {

    private static final DateTimeFormatter SPACED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<Function<String, Instant>> SHAPES = List.of(
            text -> OffsetDateTime.parse(text).toInstant(),
            text -> LocalDateTime.parse(text, SPACED).toInstant(ZoneOffset.UTC),
            text -> LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC));

    private VendorProfileRecords() {
    }

    public static VendorProfileRecord of(String provider, String record, ObjectMapper json) {
        if (record == null || record.isBlank()) {
            return null;
        }
        JsonNode tree;
        try {
            tree = json.readTree(record);
        } catch (RuntimeException unreadable) {
            return null;
        }
        if (!tree.isObject()) {
            return null;
        }
        ResearchedProfileDetails details = ResearchedProfileDetailsReader.read(tree);
        return new VendorProfileRecord(provider, record, details, seenAtOf(tree));
    }

    static Instant seenAtOf(JsonNode record) {
        JsonNode scraped = record.path("scraped_at");
        if (scraped.isNumber()) {
            return Instant.ofEpochMilli(scraped.asLong());
        }
        for (String key : new String[] {"timestamp", "updated_at", "scraped_at"}) {
            JsonNode value = record.path(key);
            if (value.isString()) {
                Instant parsed = instantOf(value.asString().strip());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Instant instantOf(String text) {
        for (Function<String, Instant> shape : SHAPES) {
            Instant parsed = parsedOrNull(shape, text);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Instant parsedOrNull(Function<String, Instant> shape, String text) {
        try {
            return shape.apply(text);
        } catch (DateTimeParseException notThisShape) {
            return null;
        }
    }
}
