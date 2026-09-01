package app.lightmove.api.feedback.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.feedback.dto.FeedbackRequest;
import app.lightmove.api.feedback.dto.FeedbackResponse;
import app.lightmove.api.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The in-app bug and feature reporter's one endpoint.
 *
 * <p><b>Deliberately reachable without a session</b>, and it is the only write in the application that
 * is. A tester who hits a bug on the login screen has no account to report it from, and that is
 * precisely when a report is most valuable — the alternative is that the worst bugs are the ones
 * nobody can tell us about. The fences are in {@code FeedbackService}: an on/off switch, an
 * anonymity switch, and a per-IP budget.
 *
 * <p>{@code principal} is null for such a caller. It is never a body field: a signed-in reporter is
 * described from their token, so nobody can put a colleague's name on an issue.
 *
 * <p>Multipart, with the typed half as a JSON part beside the images — one shape whether the report
 * carries a capture, three uploads, or nothing at all.
 */
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedback;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FeedbackResponse> submit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("report") @Valid FeedbackRequest report,
            @RequestPart(value = "screenshot", required = false) MultipartFile screenshot,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            HttpServletRequest httpRequest) {

        FeedbackResponse response =
                feedback.submit(principal, report, screenshot, attachments, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
