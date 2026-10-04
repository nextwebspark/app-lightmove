package app.lightmove.api.outreach.controller;

import app.lightmove.api.outreach.dto.BookOnPageRequest;
import app.lightmove.api.outreach.dto.BookingPageResponse;
import app.lightmove.api.outreach.dto.BookingSlotsResponse;
import app.lightmove.api.outreach.service.BookingPages;
import app.lightmove.api.outreach.service.DirectBookingPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The booking link's page, opened from an email by an executive who holds no session: public by design. */
@RestController
@RequestMapping("/api/v1/outreach/booking")
@RequiredArgsConstructor
public class BookingPageController {

    private final BookingPages bookingPages;
    private final DirectBookingPage directBookingPage;

    @GetMapping("/{slug}")
    public BookingPageResponse open(@PathVariable String slug, HttpServletRequest request) {
        return bookingPages.open(slug, request);
    }

    @GetMapping("/{slug}/slots")
    public BookingSlotsResponse slots(@PathVariable String slug, @RequestParam(required = false) LocalDate from,
                                      HttpServletRequest request) {
        return directBookingPage.slots(slug, from, request);
    }

    @PostMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void book(@PathVariable String slug, @Valid @RequestBody BookOnPageRequest booking,
                     HttpServletRequest request) {
        directBookingPage.book(slug, booking, request);
    }
}
