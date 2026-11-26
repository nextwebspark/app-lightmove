package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.model.ConsentRegisterLine;
import app.lightmove.api.candidate.model.ConsentRegisterRow;
import app.lightmove.api.candidate.model.ObjectionRegisterRow;
import app.lightmove.api.core.text.service.CsvFormatter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/** Writes the consent register, one purpose of one person, or their objection, per line; never a contact detail. */
@Service
public class ConsentRegisterCsvWriter {

    /** Without a BOM Excel on Windows reads ANSI and mangles non-ASCII names. */
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final List<String> HEADERS = List.of("Name", "Person id", "Purpose", "Position", "Client named",
            "Status", "Asked on", "Asked by", "Answered on", "Answered", "Valid until", "Wording version", "Wording");

    public String write(List<ConsentRegisterLine> lines) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(row(HEADERS));
        for (ConsentRegisterLine line : lines) {
            csv.append(row(switch (line) {
                case ConsentRegisterRow consent -> cellsOf(consent);
                case ObjectionRegisterRow objection -> cellsOf(objection);
            }));
        }
        return csv.toString();
    }

    private static List<String> cellsOf(ConsentRegisterRow line) {
        return List.of(text(line.personName()), line.personId().toString(), purposeOf(line),
                text(line.positionTitle()), line.clientNamed() ? "Yes" : "", statusOf(line),
                day(line.requestedAt()), text(line.requestedByName()), day(line.answeredAt()), viaOf(line),
                day(line.validUntil()), text(line.wordingVersion()), text(line.wording()));
    }

    /** Nobody asked for an objection, so it is answered: on the day it was set, by the door it came through. */
    private static List<String> cellsOf(ObjectionRegisterRow line) {
        return List.of(text(line.personName()), line.personId().toString(), "Objection to contact", "", "",
                "Objected", "", "", day(line.objectedAt()), viaOf(line), "", "", "");
    }

    private static String purposeOf(ConsentRegisterRow line) {
        return switch (line.purpose()) {
            case KEEP_ON_FILE -> "Keep on file";
            case SHARE_WITH_CLIENT -> "Share with client";
            case MARKETING -> "Career and market updates";
        };
    }

    private static String statusOf(ConsentRegisterRow line) {
        return switch (line.status()) {
            case REQUESTED -> "Asked";
            case GIVEN -> "Given";
            case DECLINED -> "Declined";
            case WITHDRAWN -> "Withdrawn";
            case EXPIRED -> "Expired";
        };
    }

    private static String viaOf(ConsentRegisterRow line) {
        if (line.answeredVia() == null) {
            return "";
        }
        return switch (line.answeredVia()) {
            case PAGE -> "On their privacy page";
            case BY_HAND -> "Recorded by hand";
        };
    }

    private static String viaOf(ObjectionRegisterRow line) {
        return switch (line.objectedVia()) {
            case OBJECTED_ON_PRIVACY_PAGE -> "On their privacy page";
            case OPTED_OUT_BY_EMAIL -> "By the stop link in an email";
            case RECORDED_BY_TEAM -> line.recordedByName() == null ? "Recorded by the team"
                    : "Recorded by the team: " + line.recordedByName();
        };
    }

    private static String day(Instant instant) {
        return instant == null ? "" : DAY.format(instant);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String row(List<String> values) {
        return CsvFormatter.row(values.stream().map(CsvFormatter::defused).toList()) + "\r\n";
    }
}
