import { useState } from "react";
import { Button, Notice, useToast } from "../../../components/ui";
import { deviceTimeZone, isOfferedTimeZone, zoneCityOf } from "../../../lib/datetime";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { clearTimeZoneMove, useTimeZoneMove } from "../../auth/timeZoneMove";

/** One line when an Automatic clock has just moved with its device; the move can be undone by pinning the old zone. */
export function TimeZoneMoveNotice() {
  const { user, reload } = useAuth();
  const toast = useToast();
  const move = useTimeZoneMove();
  const [isPinning, setPinning] = useState(false);

  const dateTime = user?.dateTime;
  if (!user || !dateTime || dateTime.own.timeZone || !move) return null;
  if (move.from === move.to || move.to !== deviceTimeZone()) return null;
  // A pin must come from the fixed list; a device's zone need not (a Madrid laptop is Madrid).
  const canStay = isOfferedTimeZone(move.from);

  const handleStay = async () => {
    setPinning(true);
    try {
      await authApi.updateProfile({
        fullName: user.fullName,
        title: user.title,
        timezone: move.from,
        dateFormat: dateTime.own.dateFormat,
        timeFormat: dateTime.own.timeFormat,
        locale: user.locale,
      });
      clearTimeZoneMove();
      await reload();
      toast(`Staying on ${zoneCityOf(move.from)} time`);
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
          Now on {zoneCityOf(move.to)} time. Sequences, your digest and booking hours follow it.
        </span>
        <span className="flex flex-none gap-2">
          <Button size="xs" variant="secondary" onClick={clearTimeZoneMove}>
            Keep {zoneCityOf(move.to)}
          </Button>
          {canStay && (
            <Button size="xs" variant="secondary" loading={isPinning} onClick={handleStay}>
              Stay on {zoneCityOf(move.from)} time
            </Button>
          )}
        </span>
      </Notice>
    </div>
  );
}
