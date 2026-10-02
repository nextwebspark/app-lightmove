import { NylasScheduling } from "@nylas/react";

/**
 * Nylas's scheduler, on its own so the page can load it lazily: the library is large, and nothing but the
 * booking link's page ever draws it.
 */
export default function BookingScheduler({
  configurationId,
  schedulerApiUrl,
}: {
  configurationId: string;
  schedulerApiUrl: string;
}) {
  return <NylasScheduling configurationId={configurationId} schedulerApiUrl={schedulerApiUrl} />;
}
