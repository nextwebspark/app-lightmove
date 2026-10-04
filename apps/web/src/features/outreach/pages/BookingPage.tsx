import { useQuery } from "@tanstack/react-query";
import { lazy, Suspense } from "react";
import { useParams } from "react-router-dom";
import { Logo, Skeleton } from "../../../components/ui";
import * as bookingApi from "../api/bookingApi";
import { DirectBookingForm } from "../components/DirectBookingForm";

const BookingScheduler = lazy(() => import("../components/BookingScheduler"));

/**
 * The booking link's page (`/book/:slug`): an executive picks a free time on a consultant's calendar — Nylas's
 * scheduler for a Nylas mailbox, Uncava's own form for a direct one.
 * Public, since it is opened from an email; it shows the consultant's name and the calendar, and nothing
 * else about them or their firm. A link that leads nowhere says so, whatever the reason.
 */
export default function BookingPage() {
  const { slug = "" } = useParams();
  const page = useQuery({
    queryKey: bookingApi.BOOKING_PAGE_KEY(slug),
    queryFn: ({ signal }) => bookingApi.getBookingPage(slug, signal),
    retry: false,
  });

  return (
    <div className="min-h-screen bg-u-bg px-4 py-10 text-u-text">
      <div className="mx-auto flex max-w-[760px] flex-col items-center gap-6">
        <Logo />
        {page.isPending ? (
          <Skeleton className="h-[420px] w-full" />
        ) : page.isError ? (
          <p role="alert" className="text-center text-[14px] text-u-text2">
            This booking link is no longer available. Reply to the email it came in and we'll find a time.
          </p>
        ) : (
          <>
            <h1 className="text-center text-[20px] font-semibold">
              {page.data.consultantName ? `Book a call with ${page.data.consultantName}` : "Book a call"}
            </h1>
            <div className="w-full">
              {page.data.kind === "DIRECT" ? (
                <DirectBookingForm slug={slug} consultantName={page.data.consultantName} />
              ) : (
                <Suspense fallback={<Skeleton className="h-[420px] w-full" />}>
                  <BookingScheduler
                    configurationId={page.data.configurationId ?? ""}
                    schedulerApiUrl={page.data.schedulerApiUrl ?? ""}
                  />
                </Suspense>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
