import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, Input, Skeleton } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { codeOf } from "../../../lib/errorCodes";
import * as bookingApi from "../api/bookingApi";
import { slotDayLabelOf, slotTimeOf } from "../lib/meetingTimes";
import { SlotDayTiles, SlotGroup, SlotPager } from "./BookCallTimes";

/**
 * A direct mailbox's booking page: the consultant's free times, read from their calendar, and a short form.
 * Times are in the consultant's own zone, which the page names, since that is the calendar they are read from.
 */
export function DirectBookingForm({ slug, consultantName }: { slug: string; consultantName: string | null }) {
  const [from, setFrom] = useState<string | null>(null);
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [slot, setSlot] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");

  const slots = useQuery({
    queryKey: bookingApi.BOOKING_SLOTS_KEY(slug, from),
    queryFn: ({ signal }) => bookingApi.getBookingSlots(slug, from, signal),
    placeholderData: (previous) => previous,
    retry: false,
  });

  const book = useMutation({
    mutationFn: () => bookingApi.bookOnPage(slug, { startsAt: slot as string, name: name.trim(), email: email.trim() }),
    onError: (error) => {
      if (codeOf(error) === "MEETING_SLOT_TAKEN") {
        setSlot(null);
        void slots.refetch();
      }
    },
  });

  const timeZone = slots.data?.timeZone;
  const minutes = slots.data?.minutes ?? 30;

  if (book.isSuccess && slot && timeZone) {
    const day = slots.data?.days.find((candidate) => candidate.starts.includes(slot));
    return (
      <div role="status" className="rounded-[10px] border border-u-border bg-u-raised px-5 py-6 text-center">
        <div className="text-[15px] font-semibold">You're booked</div>
        <p className="mt-2 text-[13px] text-u-text2">
          {day ? slotDayLabelOf(day.date) : "Your call"}, {slotTimeOf(slot, timeZone)} ({timeZone}), {minutes} minutes
          {consultantName ? ` with ${consultantName}` : ""}. An invite is on its way to {email.trim()}.
        </p>
      </div>
    );
  }

  if (slots.isPending) {
    return <Skeleton className="h-[420px] w-full" />;
  }
  if (slots.isError) {
    return (
      <p role="alert" className="text-center text-[14px] text-u-text2">
        The calendar couldn't be read just now. Try again in a moment.
      </p>
    );
  }

  const shownDays = slots.data.days;
  const activeDay =
    shownDays.find((day) => day.date === chosenDate) ?? shownDays.find((day) => day.starts.length > 0) ?? shownDays[0];
  const morning = activeDay && timeZone ? activeDay.starts.filter((start) => slotTimeOf(start, timeZone) < "12:00") : [];
  const afternoon = activeDay && timeZone ? activeDay.starts.filter((start) => slotTimeOf(start, timeZone) >= "12:00") : [];
  const canBook = slot !== null && name.trim() !== "" && /\S+@\S+\.\S+/.test(email.trim());

  const handleShowFrom = (day: string) => {
    setFrom(day);
    setChosenDate(null);
    setSlot(null);
  };

  return (
    <div className="grid w-full gap-6 rounded-[10px] border border-u-border bg-u-raised p-5 md:grid-cols-[minmax(0,1fr)_260px]">
      <section aria-label="Time" className="min-w-0">
        <div className="mb-3 flex items-center gap-2">
          <span className="type-label text-u-text3">{minutes} min · times in {timeZone}</span>
          {shownDays.length > 0 && (
            <SlotPager
              firstShown={shownDays[0].date}
              lastShown={shownDays[shownDays.length - 1].date}
              earliestDate={slots.data.earliestDate}
              latestDate={slots.data.latestDate}
              previousFrom={slots.data.previousFrom}
              isFetching={slots.isFetching}
              onShowFrom={handleShowFrom}
            />
          )}
        </div>
        <div
          aria-busy={slots.isPlaceholderData}
          className={cn("transition-opacity", slots.isPlaceholderData && "pointer-events-none opacity-50")}
        >
          <SlotDayTiles days={shownDays} activeDate={activeDay?.date} onChoose={setChosenDate} />
          <div className="min-h-[200px]">
            {activeDay && activeDay.starts.length === 0 ? (
              <p className="py-16 text-center text-[13px] text-u-text2">
                No free time on {slotDayLabelOf(activeDay.date)}. Pick another day, or page ahead.
              </p>
            ) : (
              <>
                <SlotGroup label="Morning" starts={morning} timeZone={timeZone} chosen={slot} onChoose={setSlot} />
                <SlotGroup label="Afternoon" starts={afternoon} timeZone={timeZone} chosen={slot} onChoose={setSlot} />
              </>
            )}
          </div>
        </div>
      </section>

      <section aria-label="Your details" className="border-t border-u-border pt-5 md:border-s md:border-t-0 md:ps-6 md:pt-0">
        <Field label="Your name">
          <Input aria-label="Your name" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
        </Field>
        <Field label="Your email" hint="The invite goes here.">
          <Input
            aria-label="Your email"
            type="email"
            value={email}
            maxLength={320}
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>
        {book.isError && (
          <p role="alert" className="mb-3 text-[12.5px] text-u-offlimits">
            {codeOf(book.error) === "MEETING_SLOT_TAKEN"
              ? "That time was just taken. Pick another."
              : "That couldn't be booked. Check your details and try again."}
          </p>
        )}
        <Button className="w-full" disabled={!canBook} loading={book.isPending} onClick={() => book.mutate()}>
          Book {slot && timeZone ? slotTimeOf(slot, timeZone) : "a time"}
        </Button>
      </section>
    </div>
  );
}
