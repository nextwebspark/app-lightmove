package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.SlotDay;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One working day of free times; no starts means the day is fully booked. */
public record SlotDayResponse(LocalDate date, List<Instant> starts) {

    public static List<SlotDayResponse> listOf(List<SlotDay> days) {
        return days.stream().map(day -> new SlotDayResponse(day.date(), day.starts())).toList();
    }
}
