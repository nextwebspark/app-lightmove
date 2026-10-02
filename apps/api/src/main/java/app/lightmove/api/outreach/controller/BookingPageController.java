package app.lightmove.api.outreach.controller;

import app.lightmove.api.outreach.dto.BookingPageResponse;
import app.lightmove.api.outreach.service.BookingPages;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The booking link's page, opened from an email by an executive who holds no session: public by design. */
@RestController
@RequestMapping("/api/v1/outreach/booking")
@RequiredArgsConstructor
public class BookingPageController {

    private final BookingPages bookingPages;

    @GetMapping("/{slug}")
    public BookingPageResponse open(@PathVariable String slug, HttpServletRequest request) {
        return bookingPages.open(slug, request);
    }
}
