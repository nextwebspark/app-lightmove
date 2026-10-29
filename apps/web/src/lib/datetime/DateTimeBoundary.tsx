import { Fragment, type ReactNode } from "react";
import { useDateTimePreferences } from "./preferences";

/**
 * Draws the routes again when the zone or a format moves. Most dates are formatted by plain functions that read the
 * preferences at render, which React has no reason to repeat; a new key remounts the screens beneath (the query cache,
 * the toasts and the assistant sit above, so a saved preference keeps its toast).
 */
export function DateTimeBoundary({ children }: { children: ReactNode }) {
  const { timeZone, dateFormat, timeFormat } = useDateTimePreferences();
  return <Fragment key={`${timeZone}|${dateFormat}|${timeFormat}`}>{children}</Fragment>;
}
