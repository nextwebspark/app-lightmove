import { NylasScheduling } from "@nylas/react";

/** The widget draws in its own shadow root; a custom property crosses it, so it takes our accent. */
const UNCAVA_THEME = { "--nylas-primary": "var(--color-u-accent-solid)" };

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
  return (
    <NylasScheduling
      configurationId={configurationId}
      schedulerApiUrl={schedulerApiUrl}
      themeConfig={UNCAVA_THEME}
    />
  );
}
