import { useState } from "react";
import { Button, Notice, useToast } from "../../../components/ui";
import { computerTimeZone, zoneCityOf } from "../../../lib/datetime";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";

const DISMISSED_KEY = "lm-time-zone-move";

/**
 * One line when an Automatic clock has just moved with its device — a member who signed in last from Dubai now in
 * London. The screens already read London; sequences, the digest and booking hours follow it too, so the move is said
 * aloud and can be undone by pinning the zone they came from.
 */
export function TimeZoneMoveNotice() {
  const { user, reload } = useAuth();
  const toast = useToast();
  const [isPinning, setPinning] = useState(false);
  const [dismissed, setDismissed] = useState(readDismissed);

  const dateTime = user?.dateTime;
  const previous = dateTime?.deviceTimeZone ?? null;
  const current = computerTimeZone();
  if (!user || !dateTime || dateTime.own.timeZone || !previous || !current || previous === current) return null;
  const move = `${previous}>${current}`;
  if (dismissed === move) return null;

  const handleKeep = () => {
    writeDismissed(move);
    setDismissed(move);
  };

  const handleStay = async () => {
    setPinning(true);
    try {
      await authApi.updateProfile({
        fullName: user.fullName,
        title: user.title,
        timezone: previous,
        dateFormat: dateTime.own.dateFormat,
        timeFormat: dateTime.own.timeFormat,
        locale: user.locale,
      });
      writeDismissed(move);
      await reload();
      toast(`Staying on ${zoneCityOf(previous)} time`);
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setPinning(false);
    }
  };

  return (
    <div role="status" className="px-3.5 pb-2.5">
      <Notice className="mb-0 flex-wrap gap-y-2">
        <span className="min-w-0 flex-1">
          Now on {zoneCityOf(current)} time. Sequences, your digest and booking hours follow it.
        </span>
        <span className="flex flex-none gap-2">
          <Button size="xs" variant="secondary" onClick={handleKeep}>
            Keep {zoneCityOf(current)}
          </Button>
          <Button size="xs" variant="secondary" loading={isPinning} onClick={handleStay}>
            Stay on {zoneCityOf(previous)} time
          </Button>
        </span>
      </Notice>
    </div>
  );
}

function readDismissed(): string | null {
  try {
    return localStorage.getItem(DISMISSED_KEY);
  } catch {
    return null;
  }
}

function writeDismissed(move: string): void {
  try {
    localStorage.setItem(DISMISSED_KEY, move);
  } catch {
    // A private window keeps no choice; the notice comes back next session, which is harmless.
  }
}
