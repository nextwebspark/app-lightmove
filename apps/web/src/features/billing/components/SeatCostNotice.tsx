import { Notice } from "../../../components/ui";
import type { WorkspaceRole } from "../../auth/api/types";
import { formatAed, paysByCard, seatCostOf } from "../lib/billingView";
import { useBillingRead } from "../lib/useBilling";

/**
 * What a staff invitation adds to a Stripe bill, said before it is sent. A client representative takes no seat, and
 * an invoiced workspace's seats are agreed with Uncava, so neither is told anything.
 */
export function SeatCostNotice({ role }: { role: WorkspaceRole }) {
  const billing = useBillingRead(role !== "CLIENT");
  const cost = billing.data && paysByCard(billing.data) ? seatCostOf(billing.data) : null;
  if (role === "CLIENT" || cost === null) return null;
  return (
    <Notice>
      Adds {formatAed(cost)} a month{billing.data?.interval === "ANNUAL" ? ", billed yearly" : ""}, before VAT — a new
      staff seat.
    </Notice>
  );
}
