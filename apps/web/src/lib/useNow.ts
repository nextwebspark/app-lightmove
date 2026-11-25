import { useEffect, useState } from "react";

/** The current moment, renewed every `everyMs`: for a screen whose wording or order turns on the time of day. */
export function useNow(everyMs: number): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), everyMs);
    return () => window.clearInterval(timer);
  }, [everyMs]);
  return now;
}
