import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Button, Modal } from "../../../components/ui";
import { onRequestRefused } from "../../../lib/apiClient";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { Member } from "../../workspace/api/types";
import type { Billing } from "../api/types";
import * as billingApi from "../api/billingApi";
import { BuyCreditsDialog } from "./BuyCreditsDialog";
import { PlansDialog } from "./PlansDialog";
import {
  billingRefusalOf,
  buyOptionOf,
  creditsLabel,
  FAIR_USE_FEATURES,
  formatBillingDate,
  formatResetDate,
  mailtoBilling,
  planOptionOf,
  trialOf,
  type BillingRefusal,
} from "../lib/billingView";
import { useBilling, useIsWorkspaceAdmin } from "../lib/useBilling";

/**
 * The out-of-credits, trial-ended and fair-use sheets, opened by a 402 `INSUFFICIENT_CREDITS` or `TRIAL_ENDED`, or a
 * 429 `FAIR_USE_REACHED`, from any request on any screen. Mounted once, above the routes; a caller that meets any of
 * them leaves it to this.
 */
export function BillingRefusalSheets() {
  const queryClient = useQueryClient();
  const [refusal, setRefusal] = useState<BillingRefusal | null>(null);
  const [next, setNext] = useState<NextDialog | null>(null);

  useEffect(
    () =>
      onRequestRefused((error) => {
        const read = billingRefusalOf(error);
        if (!read) return;
        setRefusal(read);
        void queryClient.invalidateQueries({ queryKey: billingApi.BILLING_KEY });
      }),
    [queryClient],
  );

  const close = () => setRefusal(null);
  const open = (dialog: NextDialog) => {
    close();
    setNext(dialog);
  };
  if (next) return <DialogFromSheet dialog={next} onClose={() => setNext(null)} />;
  if (refusal?.kind === "credits") return <OutOfCreditsSheet refusal={refusal} onClose={close} onOpen={open} />;
  if (refusal?.kind === "trialEnded") return <TrialEndedSheet refusal={refusal} onClose={close} onOpen={open} />;
  if (refusal?.kind === "fairUse") return <FairUseSheet refusal={refusal} onClose={close} />;
  return null;
}

/** Where a sheet's primary action leads: the packs, or the plans. */
type NextDialog = "buy" | "plans";

function OutOfCreditsSheet({
  refusal,
  onClose,
  onOpen,
}: {
  refusal: Extract<BillingRefusal, { kind: "credits" }>;
  onClose: () => void;
  onOpen: (dialog: NextDialog) => void;
}) {
  const isAdmin = useIsWorkspaceAdmin();
  const billing = useBilling();
  const { admin, settled } = useFirstAdmin(!isAdmin);
  const resetsAt = billing.data?.credits.resetsAt ?? refusal.resetsAt;

  const cost = refusal.required !== null ? `This find needs ${creditsLabel(refusal.required)}` : "This find needs credits";
  const body =
    `${cost} and this month's are used up. Nothing was spent.` +
    (resetsAt ? ` They reset on ${formatResetDate(resetsAt)}.` : "") +
    (isAdmin
      ? ""
      : admin
        ? ` Only an admin can add more — ${admin.fullName}.`
        : settled
          ? " Only your workspace admin can add more. If you can't reach them, contact us."
          : " Only an admin can add more.");

  const primary = (
    <MoreCreditsAction
      isAdmin={isAdmin}
      billing={billing.data}
      admin={admin}
      adminSettled={settled}
      subject="Contact credits are used up"
      onClose={onClose}
      onOpen={onOpen}
    />
  );

  return <RefusalSheet title="No contact credits left" body={body} primary={primary} onClose={onClose} />;
}

function TrialEndedSheet({
  refusal,
  onClose,
  onOpen,
}: {
  refusal: Extract<BillingRefusal, { kind: "trialEnded" }>;
  onClose: () => void;
  onOpen: (dialog: NextDialog) => void;
}) {
  const isAdmin = useIsWorkspaceAdmin();
  const billing = useBilling();
  const { admin, settled } = useFirstAdmin(!isAdmin);
  const endedAt = billing.data?.trialEndsAt ?? refusal.endedAt;

  const body =
    `Your trial ended${endedAt ? ` on ${formatBillingDate(endedAt)}` : ""}. Everything your team mapped is still ` +
    "here; finding contacts, search and AI start again once " +
    (isAdmin
      ? "you choose a plan."
      : admin
        ? `an admin chooses a plan — ${admin.fullName}.`
        : settled
          ? "your workspace admin chooses a plan. If you can't reach them, contact us."
          : "an admin chooses a plan.");

  const primary = (
    <MoreCreditsAction
      isAdmin={isAdmin}
      billing={billing.data}
      admin={admin}
      adminSettled={settled}
      subject="Our Uncava trial has ended"
      onClose={onClose}
      onOpen={onOpen}
    />
  );

  return <RefusalSheet title="Your trial has ended" body={body} primary={primary} onClose={onClose} />;
}

function DialogFromSheet({ dialog, onClose }: { dialog: NextDialog; onClose: () => void }) {
  const billing = useBilling();
  if (!billing.data) return null;
  return dialog === "plans" ? (
    <PlansDialog billing={billing.data} onClose={onClose} />
  ) : (
    <BuyCreditsDialog billing={billing.data} onClose={onClose} />
  );
}

/**
 * An admin's way to more credits — the packs, or the plans on a trial — or a member's way to ask an admin for them;
 * with no admin to ask, Uncava, so the sheet never offers only "Not now".
 */
function MoreCreditsAction({
  isAdmin,
  billing,
  admin,
  adminSettled,
  subject,
  onClose,
  onOpen,
}: {
  isAdmin: boolean;
  billing: Billing | undefined;
  admin: Member | null;
  adminSettled: boolean;
  /** The email's subject, saying which refusal it is about. */
  subject: string;
  onClose: () => void;
  onOpen: (dialog: NextDialog) => void;
}) {
  if (!isAdmin) {
    if (!admin) {
      if (!adminSettled || !billing) return null;
      return (
        <PrimaryLink href={mailtoBilling(billing, subject)} onClick={onClose}>
          Contact Uncava
        </PrimaryLink>
      );
    }
    const askFor = billing && trialOf(billing) ? "choose a plan" : "add more";
    return (
      <PrimaryLink href={askAdminHref(admin.email, subject)} onClick={onClose}>
        Ask {admin.fullName.split(" ")[0]} to {askFor}
      </PrimaryLink>
    );
  }
  if (!billing) return null;
  // A trial with no plan on record has nothing to buy, but it can still choose one.
  const buy = buyOptionOf(billing, isAdmin) ?? planOptionOf(billing, isAdmin);
  if (!buy) {
    return (
      <PrimaryLink href={mailtoBilling(billing, subject)} onClick={onClose}>
        Contact Uncava
      </PrimaryLink>
    );
  }
  if (buy.kind === "contact") {
    return (
      <PrimaryLink href={buy.href} onClick={onClose}>
        {buy.label}
      </PrimaryLink>
    );
  }
  return (
    <Button type="button" onClick={() => onOpen(buy.kind === "plans" ? "plans" : "buy")}>
      {buy.label}
    </Button>
  );
}

function FairUseSheet({
  refusal,
  onClose,
}: {
  refusal: Extract<BillingRefusal, { kind: "fairUse" }>;
  onClose: () => void;
}) {
  const billing = useBilling();
  const feature = refusal.use ? FAIR_USE_FEATURES[refusal.use] : "This feature";
  const body =
    `${feature} is part of your plan, up to a generous monthly amount per seat. Your team has gone past it this ` +
    `month, so it pauses${refusal.resetsAt ? ` until ${formatResetDate(refusal.resetsAt)}` : " until the month resets"}.` +
    (refusal.use === "PEOPLE_SEARCH_PAGE" ? " Pages you already have stay open." : "") +
    " Talk to us if your team needs more.";

  return (
    <RefusalSheet
      title="You've reached this month's fair use"
      body={body}
      primary={
        billing.data && (
          <PrimaryLink href={mailtoBilling(billing.data, `More fair use for ${feature}`)} onClick={onClose}>
            Talk to us
          </PrimaryLink>
        )
      }
      onClose={onClose}
    />
  );
}

function RefusalSheet({
  title,
  body,
  primary,
  onClose,
}: {
  title: string;
  body: string;
  primary: ReactNode;
  onClose: () => void;
}) {
  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      className="md:w-[420px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Not now
          </Button>
          {primary}
        </>
      }
    >
      <p className="text-[13px]/[1.55] text-u-text2">{body}</p>
    </Modal>
  );
}

function PrimaryLink({ href, onClick, children }: { href: string; onClick: () => void; children: ReactNode }) {
  return (
    <a
      href={href}
      onClick={onClick}
      className="flex items-center justify-center gap-2 rounded-[6px] border border-u-accent-solid bg-u-accent-solid px-3.5 py-2.5 text-[13.5px] font-medium text-white transition hover:border-u-accent-solid-hover hover:bg-u-accent-solid-hover"
    >
      {children}
    </a>
  );
}

/**
 * The admin a member asks: the roster's first, read only when a member's sheet opens. `settled` tells "no admin" from
 * "not read yet", so a member is never pointed at Uncava for the moment the roster takes to arrive.
 */
function useFirstAdmin(enabled: boolean): AdminLookup {
  const members = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: () => workspaceApi.members(),
    enabled,
  });
  return {
    admin: members.data?.find((member) => member.roles.includes("ADMIN")) ?? null,
    settled: members.isSuccess || members.isError,
  };
}

interface AdminLookup {
  admin: Member | null;
  settled: boolean;
}

function askAdminHref(email: string, subject: string): string {
  return `mailto:${email}?subject=${encodeURIComponent(subject)}`;
}
