import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, FormError, Skeleton } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/datetime";
import { useAuth } from "../../auth/AuthProvider";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as privacyPageApi from "../api/privacyPageApi";
import type { PrivacyPage } from "../api/privacyPageApi";
import {
  basisSentence,
  categoryLabel,
  CHOICES,
  confirmationOf,
  doneOf,
  initialsOf,
  sourceSentence,
  type PrivacyChoice,
} from "../lib/privacyPageCopy";

type Stage = { step: "notice" } | { step: "confirm"; choice: PrivacyChoice } | { step: "done"; choice: PrivacyChoice };

/**
 * The page a privacy notice links to (`/privacy/:token`, `Privacy.dc.html`, #923). Public, since the person it was
 * sent to holds no session; the firm speaks as controller and Uncava appears only as the software. It lists the kinds
 * of data held and where they came from, never the values or the person's name, because a link can be forwarded.
 * Every choice is a request the firm acts on; nothing is deleted or sent from here. An outreach email's stop link
 * (`?action=opt-out`, #924) opens on its confirmation, never on a send: mail scanners open links.
 */
export default function PrivacyLinkPage() {
  const { token = "" } = useParams();
  // Asked once the boot-time session check has settled: a visitor with no session ends it by clearing the whole query
  // cache, which would strand a read already in flight and leave the page loading forever.
  const { loading: checkingSession } = useAuth();
  const page = useQuery({
    queryKey: privacyPageApi.PRIVACY_PAGE_KEY(token),
    queryFn: ({ signal }) => privacyPageApi.getPrivacyPage(token, signal),
    retry: false,
    enabled: !checkingSession,
  });

  const firm = page.data?.controllerName ?? null;
  return (
    <div className="min-h-screen bg-u-bg px-4 pb-8 pt-10 text-u-text">
      <div className="mx-auto w-full max-w-[600px]">
        {firm && (
          <div className="mb-4 flex items-center gap-3">
            <span className="grid size-10 flex-none place-items-center rounded-u-card border border-u-border bg-u-raised text-note font-bold text-u-text">
              {initialsOf(firm)}
            </span>
            <div className="min-w-0">
              <div className="text-body font-semibold">{firm}</div>
              <div className="font-mono text-meta text-u-text3">Privacy notice</div>
            </div>
          </div>
        )}
        <div className="rounded-u-modal border border-u-border-strong bg-u-surface p-7 shadow-u-e3">
          {page.isPending ? (
            <Skeleton className="h-[360px] w-full" />
          ) : page.isError ? (
            <Unavailable />
          ) : page.data.expired ? (
            <Expired page={page.data} />
          ) : (
            <Notice token={token} page={page.data} />
          )}
        </div>
        {firm && (
          <p className="mx-1 mt-4 font-mono text-meta text-u-text3">
            {firm} is responsible for your data. This page is provided by Uncava, the software they use.
            {!page.data?.expired && page.data?.expiresAt
              ? ` This link works until ${formatInstantDate(page.data.expiresAt)}.`
              : ""}
          </p>
        )}
      </div>
    </div>
  );
}

function Notice({ token, page }: { token: string; page: PrivacyPage }) {
  const firm = page.controllerName ?? "We";
  const [searchParams] = useSearchParams();
  const [stage, setStage] = useState<Stage>(() =>
    searchParams.get("action") === "opt-out" ? { step: "confirm", choice: "optOut" } : { step: "notice" },
  );
  const [error, setError] = useState<string | null>(null);
  const sending = useMutation({
    mutationFn: (choice: PrivacyChoice) => {
      switch (choice) {
        case "object":
          return privacyPageApi.objectFromPrivacyPage(token);
        case "optOut":
          return privacyPageApi.optOutFromPrivacyPage(token);
        default:
          return privacyPageApi.requestFromPrivacyPage(token, choice);
      }
    },
    onSuccess: (_, choice) => setStage({ step: "done", choice }),
    onError: (failure) =>
      setError(
        codeOf(failure) === "PRIVACY_LINK_EXPIRED"
          ? `This link has expired. Write to ${page.privacyContactEmail ?? "us"} instead.`
          : messageFor(failure),
      ),
  });

  if (stage.step === "confirm") {
    const confirmation = confirmationOf(stage.choice, firm);
    return (
      <>
        <h1 className="m-0 text-title font-semibold">{confirmation.title}</h1>
        <p className="mt-2 text-body text-u-text2">{confirmation.body}</p>
        <FormError message={error} />
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="md" disabled={sending.isPending} onClick={() => setStage({ step: "notice" })}>
            {confirmation.back}
          </Button>
          <Button size="md" loading={sending.isPending} onClick={() => sending.mutate(stage.choice)}>
            {confirmation.cta}
          </Button>
        </div>
      </>
    );
  }

  if (stage.step === "done") {
    const done = doneOf(stage.choice, firm);
    return (
      <div role="status">
        <span className="grid size-10 place-items-center rounded-full bg-u-direct-tint text-u-direct">
          <Icon d={ICONS.check} size={18} />
        </span>
        <h1 className="mt-3.5 text-title font-semibold">{done.title}</h1>
        <p className="mt-2 text-body text-u-text2">{done.body}</p>
        <button
          type="button"
          onClick={() => {
            setError(null);
            setStage({ step: "notice" });
          }}
          className="mt-4 text-note font-medium text-u-accent hover:underline"
        >
          See your other choices
        </button>
      </div>
    );
  }

  return (
    <>
      <h1 className="m-0 text-title font-semibold">What we hold about you, and your choices</h1>
      <p className="mt-2 text-body text-u-text2">
        {firm} holds some of your professional details so we can consider you for senior roles our clients ask us to
        fill. This page says what we hold, where it came from, and what you can do about it.
      </p>

      <h2 className="type-label mt-5 text-u-text3">What we hold</h2>
      <ul aria-label="What we hold" className="mt-2 flex flex-wrap gap-1.5">
        {page.categories.map((category) => (
          <li key={category} className="rounded-full border border-u-border bg-u-raised px-2.5 py-1 text-note text-u-text2">
            {categoryLabel(category)}
          </li>
        ))}
      </ul>

      <div className="mt-5 grid gap-3.5 md:grid-cols-2">
        <div>
          <h2 className="type-label text-u-text3">Where it came from</h2>
          <p className="mt-1.5 text-note text-u-text2">{sourceSentence(page.source)}</p>
        </div>
        <div>
          <h2 className="type-label text-u-text3">Why we may hold it</h2>
          <p className="mt-1.5 text-note text-u-text2">{basisSentence(page.lawfulBasis)}</p>
        </div>
      </div>

      <p className="mt-4 text-note text-u-text2">
        {page.privacyContactEmail && (
          <>
            Questions go to{" "}
            <a className="text-u-accent hover:underline" href={`mailto:${page.privacyContactEmail}`}>
              {page.privacyContactEmail}
            </a>
            .{" "}
          </>
        )}
        {page.noticeUrl && (
          <>
            Our full privacy notice is at{" "}
            <a className="text-u-accent hover:underline" href={page.noticeUrl} target="_blank" rel="noreferrer">
              {page.noticeUrl.replace(/^https:\/\//, "")}
            </a>
            .
          </>
        )}
      </p>

      <h2 className="type-label mt-6 text-u-text3">Your choices</h2>
      <div className="mt-2 grid gap-2">
        {CHOICES.map((choice) => (
          <button
            key={choice.key}
            type="button"
            onClick={() => {
              setError(null);
              setStage({ step: "confirm", choice: choice.key });
            }}
            className="flex w-full items-center gap-3 rounded-u-card border border-u-border bg-u-raised px-3.5 py-3 text-start hover:border-u-text3"
          >
            <span className="min-w-0 flex-1">
              <span className="block text-body font-semibold text-u-text">{choice.label}</span>
              <span className="mt-0.5 block text-note text-u-text3">{choice.detail}</span>
            </span>
            <Icon d={ICONS.chevronRight} size={15} className="flex-none text-u-text3" />
          </button>
        ))}
      </div>
    </>
  );
}

function Expired({ page }: { page: PrivacyPage }) {
  return (
    <>
      <h1 className="m-0 text-title font-semibold">This link has expired</h1>
      <p className="mt-2 text-body text-u-text2">
        You can still object, ask for a copy of your data, or ask us to delete it.
        {page.privacyContactEmail ? (
          <>
            {" "}
            Write to{" "}
            <a className="text-u-accent hover:underline" href={`mailto:${page.privacyContactEmail}`}>
              {page.privacyContactEmail}
            </a>{" "}
            and we will reply within one month.
          </>
        ) : (
          " Reply to the email it came in and we will answer within one month."
        )}
      </p>
    </>
  );
}

/** An unknown token says nothing about anyone. */
function Unavailable() {
  return (
    <div role="alert">
      <h1 className="m-0 text-title font-semibold">This link isn&apos;t valid</h1>
      <p className="mt-2 text-body text-u-text2">
        Check that you copied the whole link from the email. If it still doesn&apos;t open, reply to the email it came
        in.
      </p>
    </div>
  );
}
