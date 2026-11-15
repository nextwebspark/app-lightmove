import { useMutation, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/datetime";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as privacyPageApi from "../api/privacyPageApi";
import type { PrivacyPage, PrivacyPageConsent } from "../api/privacyPageApi";
import {
  answeredBodyOf,
  closedBodyOf,
  consentTitleOf,
  manageLeadOf,
  renewalBlockTitleOf,
  renewalLeadOf,
  renewalSilenceOf,
  renewalTitleOf,
  REQUEST_SILENCE,
  requestLeadOf,
  requestTitleOf,
  withdrawConfirmationOf,
  withdrawnBodyOf,
} from "../lib/consentPageCopy";

type YesNo = "yes" | "no";

function day(instant: string | null): string {
  return formatInstantDate(instant) ?? "";
}

/** A no to a renewal leaves the consent running to its own date, so it says that rather than a bare No. */
function answerLineOf(consent: PrivacyPageConsent, said: YesNo): string {
  if (said === "no") {
    return consent.status === "given" && consent.validUntil ? `Not renewed, ends on ${day(consent.validUntil)}` : "No";
  }
  return consent.validUntil ? `Yes, until ${day(consent.validUntil)}` : "Yes";
}

/** What the server says was answered on this link, so a reopened link shows the answers rather than "closed". */
function answeredOnLinkOf(page: PrivacyPage): Record<string, YesNo> {
  return Object.fromEntries(
    page.consents.flatMap((consent) => (consent.answer ? [[consent.id, consent.answer === "given" ? "yes" : "no"]] : [])),
  );
}

function refusalOf(failure: unknown, contact: string | null): string {
  return codeOf(failure) === "PRIVACY_LINK_EXPIRED"
    ? `This link has expired. Write to ${contact ?? "us"} instead.`
    : messageFor(failure);
}

function Done({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <div role="status">
      <span className="grid size-10 place-items-center rounded-full bg-u-direct-tint text-u-direct">
        <Icon d={ICONS.check} size={18} />
      </span>
      <h1 className="mt-3.5 text-title font-semibold">{title}</h1>
      <p className="mt-2 text-body text-u-text2">{body}</p>
      {children}
    </div>
  );
}

/**
 * A consent request (#1267, `Privacy.dc.html`'s `consent`, `consent-renewal`, `consent-answered`, `consent-closed`):
 * a Yes and a No for each purpose, nothing pre-selected, and no answer changing nothing. A renewal asks once more about
 * a consent ending; a request past its 30 days says it closed.
 */
export function ConsentRequest({
  token,
  page,
  onSeeChoices,
}: {
  token: string;
  page: PrivacyPage;
  onSeeChoices?: () => void;
}) {
  const queryClient = useQueryClient();
  const firm = page.controllerName ?? "We";
  const [answers, setAnswers] = useState<Record<string, YesNo>>({});
  const [submitted, setSubmitted] = useState<Record<string, YesNo> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const open = page.consents.filter((consent) => consent.open);
  const sending = useMutation({
    mutationFn: (given: Record<string, YesNo>) =>
      privacyPageApi.answerConsents(
        token,
        Object.entries(given).map(([consentId, said]) => ({
          consentId,
          answer: said === "yes" ? "given" : "declined",
        })),
      ),
    onSuccess: async (_, given) => {
      await queryClient.invalidateQueries({ queryKey: privacyPageApi.PRIVACY_PAGE_KEY(token) });
      setSubmitted(given);
    },
    onError: (failure) => setError(refusalOf(failure, page.privacyContactEmail)),
  });

  const answeredOnLink = answeredOnLinkOf(page);
  const shownAnswers = submitted ?? (open.length === 0 ? answeredOnLink : {});
  if (Object.keys(shownAnswers).length > 0) {
    const rows = page.consents.filter((consent) => shownAnswers[consent.id]);
    return (
      <Done
        title="Thank you, your answers are saved"
        body={answeredBodyOf(firm, Object.values(shownAnswers).includes("yes"))}
      >
        <ul aria-label="Your answers" className="mt-4 flex flex-col gap-2">
          {rows.map((consent) => (
            <li
              key={consent.id}
              className="flex items-baseline gap-3 rounded-u-card border border-u-border bg-u-raised px-3.5 py-2.5"
            >
              <span className="min-w-0 flex-1 text-note text-u-text">{consentTitleOf(consent.purpose)}</span>
              <span
                className={cn(
                  "flex-none text-note font-semibold",
                  shownAnswers[consent.id] === "yes" ? "text-u-direct" : "text-u-text2",
                )}
              >
                {answerLineOf(consent, shownAnswers[consent.id])}
              </span>
            </li>
          ))}
        </ul>
      </Done>
    );
  }

  if (open.length === 0) {
    return (
      <>
        <h1 className="m-0 text-title font-semibold">This request has closed</h1>
        <p className="mt-2 text-body text-u-text2">{closedBodyOf(firm, day(page.expiresAt))}</p>
        {page.privacyContactEmail && (
          <p className="mt-2 text-body text-u-text2">
            If you&apos;d like to answer now, or have a question, write to{" "}
            <a className="text-u-accent hover:underline" href={`mailto:${page.privacyContactEmail}`}>
              {page.privacyContactEmail}
            </a>
            .
          </p>
        )}
      </>
    );
  }

  const renewal = open.find((consent) => consent.status === "given");
  const months = renewal?.keepOnFileMonths ?? 0;
  const answered = open.filter((consent) => answers[consent.id]).length;
  return (
    <>
      <h1 className="m-0 text-title font-semibold">
        {renewal ? renewalTitleOf(day(renewal.validUntil)) : requestTitleOf(firm)}
      </h1>
      <p className="mt-2 text-body text-u-text2">{renewal ? renewalLeadOf(firm, months) : requestLeadOf(firm)}</p>
      <div className="mt-5 grid gap-3">
        {open.map((consent) => (
          <div
            key={consent.id}
            role="group"
            aria-label={consent === renewal ? renewalBlockTitleOf(months) : consentTitleOf(consent.purpose)}
            className="rounded-u-card border border-u-border bg-u-raised px-4 py-3.5"
          >
            <div className="text-body font-semibold text-u-text">
              {consent === renewal ? renewalBlockTitleOf(months) : consentTitleOf(consent.purpose)}
            </div>
            {consent.wording && <p className="mt-1 text-note text-u-text2">{consent.wording}</p>}
            <div className="mt-3 flex gap-2">
              {(["yes", "no"] as const).map((said) => {
                const on = answers[consent.id] === said;
                return (
                  <button
                    key={said}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setAnswers((current) => ({ ...current, [consent.id]: said }))}
                    className={cn(
                      "min-w-[84px] rounded-u-chip border px-4 py-2 text-note font-semibold",
                      on
                        ? said === "yes"
                          ? "border-u-direct bg-u-direct-tint text-u-direct"
                          : "border-u-text2 bg-u-surface text-u-text"
                        : "border-u-border bg-u-surface text-u-text2",
                    )}
                  >
                    {said === "yes" ? "Yes" : "No"}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-4 text-note text-u-text3">
        {renewal ? renewalSilenceOf(day(renewal.validUntil)) : REQUEST_SILENCE}
      </p>
      <FormError message={error} />
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        {onSeeChoices && (
          <button type="button" onClick={onSeeChoices} className="text-note font-medium text-u-accent hover:underline">
            See what we hold and your other choices
          </button>
        )}
        <Button
          size="md"
          loading={sending.isPending}
          disabled={answered === 0}
          onClick={() => {
            setError(null);
            sending.mutate(answers);
          }}
        >
          {renewal ? "Send my answer" : "Send my answers"}
        </Button>
      </div>
    </>
  );
}

/**
 * The link a confirmation email carries (#1267, `Privacy.dc.html`'s `manage`, `confirm-withdraw`, `withdrawn`): what the
 * person agreed to that is in force, each withdrawn as easily as it was given.
 */
export function ManageConsents({ token, page }: { token: string; page: PrivacyPage }) {
  const queryClient = useQueryClient();
  const firm = page.controllerName ?? "We";
  const [confirming, setConfirming] = useState<PrivacyPageConsent | null>(null);
  const [withdrawn, setWithdrawn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const withdrawing = useMutation({
    mutationFn: (consent: PrivacyPageConsent) =>
      privacyPageApi.answerConsents(token, [{ consentId: consent.id, answer: "withdrawn" }]),
    onSuccess: async () => {
      setConfirming(null);
      setWithdrawn(true);
      await queryClient.invalidateQueries({ queryKey: privacyPageApi.PRIVACY_PAGE_KEY(token) });
    },
    onError: (failure) => setError(refusalOf(failure, page.privacyContactEmail)),
  });

  if (withdrawn) {
    return (
      <Done title="Your consent is withdrawn" body={withdrawnBodyOf(firm)}>
        <button
          type="button"
          onClick={() => setWithdrawn(false)}
          className="mt-4 text-note font-medium text-u-accent hover:underline"
        >
          See your other consents
        </button>
      </Done>
    );
  }

  if (confirming) {
    const confirmation = withdrawConfirmationOf(confirming.purpose);
    return (
      <>
        <h1 className="m-0 text-title font-semibold">{confirmation.title}</h1>
        <p className="mt-2 text-body text-u-text2">{confirmation.body}</p>
        <FormError message={error} />
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="md" disabled={withdrawing.isPending} onClick={() => setConfirming(null)}>
            Back
          </Button>
          <Button size="md" loading={withdrawing.isPending} onClick={() => withdrawing.mutate(confirming)}>
            Withdraw consent
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <h1 className="m-0 text-title font-semibold">Your consents</h1>
      <p className="mt-2 text-body text-u-text2">{manageLeadOf(firm)}</p>
      {page.consents.length === 0 ? (
        <p className="mt-5 text-body text-u-text2">Nothing you agreed to is in force.</p>
      ) : (
        <ul aria-label="Your consents" className="mt-5 flex flex-col gap-2">
          {page.consents.map((consent) => (
            <li
              key={consent.id}
              className="flex items-center gap-3 rounded-u-card border border-u-border bg-u-raised px-3.5 py-3"
            >
              <span className="min-w-0 flex-1">
                <span className="block text-body font-semibold text-u-text">{consentTitleOf(consent.purpose)}</span>
                <span className="mt-0.5 block font-mono text-meta text-u-text3">
                  Given on {day(consent.answeredAt)}
                  {consent.validUntil ? ` · until ${day(consent.validUntil)}` : ""}
                </span>
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setError(null);
                  setConfirming(consent);
                }}
              >
                Withdraw
              </Button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
