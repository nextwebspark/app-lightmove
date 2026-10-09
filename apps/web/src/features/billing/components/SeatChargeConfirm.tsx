import { formatAed, formatResetDate, type SeatCharge } from "../lib/billingView";

/** The confirm step before a staff invitation that will add a paid seat: what the card is charged, and when. */
export function SeatChargeConfirm({ email, roleLabel, charge }: { email: string; roleLabel: string; charge: SeatCharge }) {
  return (
    <div className="text-[13px]/[1.55] text-u-text2">
      <p>
        <span className="font-medium text-u-text">{email}</span> joins as {roleLabel}. When they accept, your card is
        charged for one {charge.planName} seat:
      </p>
      <ul className="my-3 space-y-1 rounded-lg border border-u-border bg-u-sunken px-4 py-3 font-mono text-xs text-u-text">
        <li>
          {formatAed(charge.monthlyFils)} a month
          {charge.annual ? `, billed yearly (${formatAed(charge.monthlyFils * 12)})` : ""}, before VAT
        </li>
        {charge.nowFils !== null && charge.until && (
          <li>
            About {formatAed(charge.nowFils)} at once for the rest of this period, to {formatResetDate(charge.until)}
          </li>
        )}
      </ul>
      <p>
        Your bill becomes {charge.seatsAfter} seats · {formatAed(charge.monthlyTotalFils)} a month
        {charge.annual ? ", billed yearly" : ""}. Removing them later refunds nothing.
      </p>
    </div>
  );
}
