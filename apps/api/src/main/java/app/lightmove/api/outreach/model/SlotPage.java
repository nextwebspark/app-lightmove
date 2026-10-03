package app.lightmove.api.outreach.model;

import java.time.LocalDate;
import java.util.List;

/**
 * One page of free times: its working days, the first and last days paging may reach, and where the page before
 * this one starts (null on the first).
 */
public record SlotPage(LocalDate earliestDate, LocalDate latestDate, LocalDate previousFrom, List<SlotDay> days) {

    public SlotPage {
        days = List.copyOf(days);
    }
}
