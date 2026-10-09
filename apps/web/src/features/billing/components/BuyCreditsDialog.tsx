import { useState } from "react";
import { Button, Modal } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatNumber } from "../../../lib/format";
import * as billingApi from "../api/billingApi";
import type { Billing } from "../api/types";
import { rememberBoughtBefore } from "../lib/checkoutReturn";
import { formatAed, withVat } from "../lib/billingView";
import { useStripeRedirect } from "../lib/useStripeRedirect";

/**
 * Buy more credits (`Billing.dc.html`): one of the packs Stripe sells, its price with VAT, then Checkout. Bought
 * credits are spent after the month's and kept a year.
 */
export function BuyCreditsDialog({ billing, onClose }: { billing: Billing; onClose: () => void }) {
  const [code, setCode] = useState(billing.packs[0]?.code ?? "");
  const pack = billing.packs.find((candidate) => candidate.code === code) ?? null;
  const checkout = useStripeRedirect((packCode: string) => {
    rememberBoughtBefore(billing.credits.bought);
    return billingApi.startCreditsCheckout(packCode);
  });

  return (
    <Modal
      open
      onClose={onClose}
      title="Buy more credits"
      subtitle="Used after this month's credits run out, and kept for 12 months."
      className="md:w-[460px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="button"
            disabled={!pack}
            loading={checkout.isPending}
            onClick={() => pack && checkout.mutate(pack.code)}
          >
            {pack ? `Pay ${formatAed(withVat(pack.priceFils))}` : "Pay"}
          </Button>
        </>
      }
    >
      <div role="radiogroup" aria-label="Credits" className="flex flex-col gap-2">
        {billing.packs.map((candidate) => {
          const on = candidate.code === code;
          return (
            <button
              key={candidate.code}
              type="button"
              role="radio"
              aria-checked={on}
              onClick={() => setCode(candidate.code)}
              className={cn(
                "flex w-full items-center gap-3 rounded-[10px] border px-3.5 py-3 text-left transition hover:border-u-text3",
                on ? "border-u-signal bg-u-signal-tint" : "border-u-border bg-u-raised",
              )}
            >
              <span
                className={cn(
                  "grid size-4 flex-none place-items-center rounded-full border-[1.5px]",
                  on ? "border-u-signal" : "border-u-text3",
                )}
              >
                <span className={cn("size-2 rounded-full", on ? "bg-u-signal" : "bg-transparent")} />
              </span>
              <span className="flex-1">
                <span className="block text-sm font-medium">{formatNumber(candidate.credits)} credits</span>
                <span className="mt-0.5 block font-mono text-[11px] text-u-text3">
                  About {formatNumber(Math.floor(candidate.credits / Math.max(1, billing.prices.email)))} emails or{" "}
                  {formatNumber(Math.floor(candidate.credits / Math.max(1, billing.prices.phone)))} phones
                </span>
              </span>
              <span className="font-u-num text-sm font-medium">{formatAed(candidate.priceFils)}</span>
            </button>
          );
        })}
      </div>
      {pack && (
        <div className="mt-3 flex justify-between font-mono text-xs text-u-text3">
          <span>{formatAed(pack.priceFils)} + 5% VAT</span>
          <span className="font-u-num text-[13px] font-semibold text-u-text">{formatAed(withVat(pack.priceFils))}</span>
        </div>
      )}
    </Modal>
  );
}
