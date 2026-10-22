package app.lightmove.api.notetaker.controller;

import app.lightmove.api.notetaker.dto.NotetakerCanaryHearingResponse;
import app.lightmove.api.notetaker.dto.NotetakerCanaryReportRequest;
import app.lightmove.api.notetaker.service.NotetakerCanaryHearing;
import app.lightmove.api.notetaker.service.NotetakerCanaryReports;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the daily canary bot reports (#956) and has its pieces heard (#978). Public to the security chain: the bot's own
 * service account's Google ID token, in {@link NotetakerRunController#BOT_IDENTITY_HEADER}, is the whole credential.
 */
@RestController
@RequiredArgsConstructor
public class NotetakerCanaryController {

    public static final String PATH = "/api/v1/internal/notetaker/canary";
    public static final String HEARING_PATH = PATH + "/hearing";

    private final NotetakerCanaryReports reports;
    private final NotetakerCanaryHearing hearing;

    @PostMapping(PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@RequestHeader(name = NotetakerRunController.BOT_IDENTITY_HEADER, required = false)
                       String identity,
                       @Valid @RequestBody NotetakerCanaryReportRequest request,
                       HttpServletRequest httpRequest) {
        reports.receive(identity, request, httpRequest);
    }

    /** One audio piece the canary's bot recorded, as the raw WebM body; answers what the transcriber heard in it. */
    @PostMapping(HEARING_PATH)
    public NotetakerCanaryHearingResponse hear(@RequestHeader(name = NotetakerRunController.BOT_IDENTITY_HEADER,
                                                       required = false) String identity,
                                               HttpServletRequest httpRequest) throws IOException {
        return hearing.hear(identity, httpRequest.getInputStream(), httpRequest);
    }
}
