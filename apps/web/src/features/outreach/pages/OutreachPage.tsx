import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState, Skeleton, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { messageFor, messageForCode } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as mailboxApi from "../api/mailboxApi";
import type { ConnectedMailbox, Mailbox } from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import type { Sequence } from "../api/sequenceApi";
import { scheduleLabelOf } from "../lib/sendSchedule";
import { CandidateDrawerById } from "../../candidates/components/CandidateDrawerById";
import { PeopleInOutreach } from "../components/PeopleInOutreach";
import { MailboxTimeZoneSelect } from "../components/MailboxTimeZoneSelect";
import { SequenceStatePill } from "../components/SequenceStatePill";
import { ZoomConnectControl } from "../components/ZoomConnectControl";
import { connectMailboxInPopup } from "../lib/mailboxPopup";
import { useMailbox } from "../lib/useMailbox";

/**
 * A position's Outreach page (`claude-design/Outreach.dc.html`): the consultant's own mailbox, the
 * counts, the position's sequences and the people in them, each opening their drawer.
 */
export function OutreachPage() {
  const { user } = useAuth();
  const isClient = isPureClient(user?.workspace?.roles ?? []);

  if (isClient) {
    return <NotYetBuilt />;
  }
  return <StaffOutreachPage />;
}

function StaffOutreachPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [connectingProvider, setConnectingProvider] = useState<string | null>(null);
  const [openCandidateId, setOpenCandidateId] = useState<string | null>(null);
  const abandonConnect = useRef<() => void>(() => {});

  const { project } = useOutletContext<ProjectOutletContext>();
  const mailbox = useMailbox();

  useEffect(() => () => abandonConnect.current(), []);

  const sendTest = useMutation({
    mutationFn: mailboxApi.sendMailboxTest,
    onSuccess: () => toast("Test email sent. Check your inbox."),
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    },
  });

  const [isConfirmingDisconnect, setIsConfirmingDisconnect] = useState(false);
  const disconnect = useMutation({
    mutationFn: mailboxApi.disconnectMailbox,
    onSuccess: () => {
      setIsConfirmingDisconnect(false);
      toast("Mailbox disconnected.");
      void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleConnect = (provider: string) => {
    abandonConnect.current();
    setConnectingProvider(provider);
    const finish = () => setConnectingProvider(null);
    abandonConnect.current = connectMailboxInPopup(
      async () => (await mailboxApi.startMailboxConnect(provider)).authorizationUrl,
      {
        onConnected: () => {
          finish();
          toast("Mailbox connected.");
          void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
        },
        onError: (code) => {
          finish();
          toast.error(messageForCode(code));
        },
        onCancel: finish,
      },
    );
  };

  const connection = mailbox.data?.connection ?? null;

  return (
    <div className="mx-auto max-w-[1440px] px-4 pb-16 pt-7 md:px-7">
      <div className="mb-[18px] flex flex-wrap items-start gap-4">
        <div className="min-w-0">
          <h1 className="text-[19px]/[1.25] font-semibold">Outreach</h1>
          <p className="mt-1 font-mono text-[12px] text-u-text3">
            Personal email from your own mailbox. A sequence stops the moment someone replies.
          </p>
        </div>
        {connection?.status === "ACTIVE" && (
          <ConnectedMailboxPill
            connection={connection}
            isSendingTest={sendTest.isPending}
            isDisconnecting={disconnect.isPending}
            onSendTest={() => sendTest.mutate()}
            onDisconnect={() => {
              // Runs end without this page knowing — a stop here, a reply, a bounce — so the count is asked again.
              void mailbox.refetch();
              setIsConfirmingDisconnect(true);
            }}
          />
        )}
        {connection && (
          <ConfirmDialog
            open={isConfirmingDisconnect}
            title={`Disconnect ${connection.address}?`}
            confirmLabel="Disconnect"
            pending={disconnect.isPending}
            onConfirm={() => disconnect.mutate()}
            onClose={() => setIsConfirmingDisconnect(false)}
          >
            {connection.liveSequences > 0 && (
              <p>
                <strong className="font-semibold text-u-text">{liveRunsLine(connection)} will stop sending.</strong>
              </p>
            )}
            <p>
              You can reconnect later, but sequences that stop don&rsquo;t restart.
            </p>
          </ConfirmDialog>
        )}
        {connection?.status === "ACTIVE" && <ZoomConnectControl />}
      </div>

      {mailbox.isError && (
        <p role="alert" className="mb-[18px] rounded-[8px] border border-u-border bg-u-surface px-4 py-3 text-[13px] text-u-text3">
          {messageFor(mailbox.error)}
        </p>
      )}
      {mailbox.isSuccess && (
        <MailboxState
          mailbox={mailbox.data}
          connectingProvider={connectingProvider}
          onConnect={handleConnect}
        />
      )}

      <PeopleInOutreach projectId={project.id} onOpenCandidate={setOpenCandidateId}>
        <SequenceCards projectId={project.id} />
      </PeopleInOutreach>
      <CandidateDrawerById
        project={project}
        candidateId={openCandidateId}
        onClose={() => setOpenCandidateId(null)}
        onChanged={() => void queryClient.invalidateQueries({ queryKey: ["outreach", project.id] })}
      />
    </div>
  );
}

function SequenceCards({ projectId }: { projectId: string }) {
  const sequences = useQuery({
    queryKey: sequenceApi.SEQUENCES_KEY(projectId),
    queryFn: ({ signal }) => sequenceApi.getSequences(projectId, signal),
  });
  const newSequencePath = `/projects/${projectId}/outreach/sequences/new`;

  if (sequences.isError) {
    return (
      <p role="alert" className="text-[13px] text-u-text3">
        {messageFor(sequences.error)}
      </p>
    );
  }
  if (sequences.isPending) {
    return <Skeleton className="h-[96px] w-full" />;
  }
  if (sequences.data.length === 0) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.outreach} size={22} />}
        title="No outreach on this position yet"
        body="Write a sequence — a first email and up to two follow-ups in the same thread — then add people to it from In universe, Shortlisted or an executive's drawer."
      >
        <Link
          to={newSequencePath}
          className="rounded-[8px] bg-u-accent-solid px-3.5 py-2 text-[13px] font-semibold text-u-bg hover:bg-u-accent-solid-hover"
        >
          Write your first sequence
        </Link>
      </EmptyState>
    );
  }
  return (
    <section>
      <div className="mb-2.5 flex items-center gap-3">
        <h2 className="type-label text-u-text3">Sequences</h2>
        <Link
          to={newSequencePath}
          className="ms-auto rounded-[7px] border border-u-border px-3 py-1.5 text-[12.5px] font-medium text-u-text2 hover:text-u-text"
        >
          New sequence
        </Link>
      </div>
      <div className="grid gap-3 [grid-template-columns:repeat(auto-fill,minmax(300px,1fr))]">
        {sequences.data.map((sequence) => (
          <SequenceCard key={sequence.id} projectId={projectId} sequence={sequence} />
        ))}
      </div>
    </section>
  );
}

function SequenceCard({ projectId, sequence }: { projectId: string; sequence: Sequence }) {
  return (
    <Link
      to={`/projects/${projectId}/outreach/sequences/${sequence.id}`}
      className="block rounded-[10px] border border-u-border bg-u-surface px-4 py-3.5 hover:border-u-border-strong"
    >
      <div className="flex items-center gap-2">
        <span className="min-w-0 flex-1 truncate text-[14px] font-semibold">{sequence.name}</span>
        <SequenceStatePill isLive={sequence.enrolledCount > 0} />
      </div>
      <div className="mt-1 font-mono text-[11.5px] text-u-text3">{sequenceMetaOf(sequence)}</div>
      <div className="mt-2.5 flex gap-4 font-mono text-[12px] font-medium text-u-text2">
        <span>{sequence.enrolledCount} enrolled</span>
        <span>{sequence.sentCount} sent</span>
        <span className="text-u-direct">{sequence.repliedCount} replied</span>
      </div>
    </Link>
  );
}

/** "3 steps · day 0, +3, +5 sending days · Mon–Fri, 08:00–18:00 · by Yara Haddad", as the mockup's card meta reads. */
function sequenceMetaOf(sequence: Sequence): string {
  const steps = `${sequence.steps.length} ${sequence.steps.length === 1 ? "step" : "steps"}`;
  const days = sequence.steps.map((step, index) => (index === 0 ? "day 0" : `+${step.delayWorkingDays}`)).join(", ");
  const timing = sequence.steps.length > 1 ? ` · ${days} sending days` : "";
  const schedule = ` · ${scheduleLabelOf(sequence.schedule)}`;
  return `${steps}${timing}${schedule}${sequence.createdByName ? ` · by ${sequence.createdByName}` : ""}`;
}

function MailboxState({
  mailbox,
  connectingProvider,
  onConnect,
}: {
  mailbox: Mailbox;
  connectingProvider: string | null;
  onConnect: (provider: string) => void;
}) {
  if (!mailbox.offered) {
    return (
      <p className="mb-[18px] rounded-[10px] border border-u-border bg-u-raised px-5 py-4 text-[13px] text-u-text2">
        Outreach email is not set up on this deployment.
      </p>
    );
  }
  const connection = mailbox.connection;
  if (connection?.status === "ERROR") {
    return (
      <div role="alert" className="mb-[18px] flex flex-wrap items-center gap-3 rounded-[8px] bg-u-offlimits-tint px-3.5 py-2.5 text-[13px] text-u-text">
        <span>
          <b className="text-u-offlimits">{providerLabel(connection.provider)} disconnected.</b> Access to{" "}
          {connection.address} was withdrawn. Reconnect to send from it again.
        </span>
        <Button
          variant="secondary"
          className="ms-auto px-[11px] py-[5px] text-[12px]"
          loading={connectingProvider === connection.provider}
          onClick={() => onConnect(connection.provider)}
        >
          Reconnect
        </Button>
      </div>
    );
  }
  if (connection?.movesOffNylas) {
    return (
      <MoveOffNylasBanner
        connection={connection}
        isConnecting={connectingProvider === connection.provider}
        onReconnect={() => onConnect(connection.provider)}
      />
    );
  }
  if (connection) {
    return null;
  }
  return (
    <div className="mb-[18px] flex flex-wrap items-center gap-[18px] rounded-[10px] border border-u-border-strong bg-u-raised px-5 py-[18px]">
      <div className="grid size-10 flex-none place-items-center rounded-[10px] bg-u-accent-tint text-u-accent">
        <Icon d={ICONS.mail} size={20} />
      </div>
      <div className="min-w-[260px] flex-1">
        <div className="text-[14px] font-semibold">Connect your mailbox to send outreach</div>
        <div className="mt-[3px] text-[12.5px]/[1.5] text-u-text2">
          Emails go from your own address and replies land in your own inbox. Uncava only notices that
          someone replied, so it can stop their sequence. It never keeps what they wrote.
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {mailbox.providers.map((provider) => (
          <Button
            key={provider}
            variant="secondary"
            className="px-3.5 py-2 text-[13px] font-semibold text-u-text"
            loading={connectingProvider === provider}
            disabled={connectingProvider !== null}
            onClick={() => onConnect(provider)}
          >
            Connect {providerLabel(provider)}
          </Button>
        ))}
      </div>
    </div>
  );
}

/** Nylas keeps sending until the consultant reconnects once; a reconnect then goes through Uncava's own connection. */
function MoveOffNylasBanner({
  connection,
  isConnecting,
  onReconnect,
}: {
  connection: ConnectedMailbox;
  isConnecting: boolean;
  onReconnect: () => void;
}) {
  const runs = connection.runsStoppedByMove;
  return (
    <div className="mb-[18px] flex flex-wrap items-center gap-3 rounded-[8px] border border-u-border bg-u-raised px-3.5 py-2.5 text-[13px] text-u-text">
      <span className="min-w-[260px] flex-1">
        <b>Reconnect to move off Nylas.</b> Uncava now connects to {providerLabel(connection.provider)} directly.
        Reconnect {connection.address} once to keep sending from it.
        {runs > 0 && (
          <>
            {" "}
            <span className="text-u-offlimits">
              {runs === 1 ? "1 running sequence stops" : `${runs} running sequences stop`} at {runs === 1 ? "its" : "their"}{" "}
              next email; start {runs === 1 ? "it" : "them"} again afterwards.
            </span>
          </>
        )}
        {connection.bookingLink && (
          <> Your booking link stops working, so sequences that send it stop too.</>
        )}
      </span>
      <Button
        variant="secondary"
        className="ms-auto px-[11px] py-[5px] text-[12px]"
        loading={isConnecting}
        onClick={onReconnect}
      >
        Reconnect
      </Button>
    </div>
  );
}

function ConnectedMailboxPill({
  connection,
  isSendingTest,
  isDisconnecting,
  onSendTest,
  onDisconnect,
}: {
  connection: ConnectedMailbox;
  isSendingTest: boolean;
  isDisconnecting: boolean;
  onSendTest: () => void;
  onDisconnect: () => void;
}) {
  return (
    <div className="ms-auto flex flex-wrap items-center gap-2.5">
      <span
        title="Emails you send go from this address; replies land in your inbox"
        className="inline-flex items-center gap-2 rounded-[7px] border border-u-border px-2.5 py-1.5 font-mono text-[12px] text-u-text2"
      >
        <span aria-hidden="true" className="size-[7px] rounded-full bg-u-direct" />
        {connection.address}
        <span className="text-u-text3">· up to {connection.dailyCap} a day</span>
      </span>
      <MailboxTimeZoneSelect timeZone={connection.timeZone} />
      <Button variant="ghost" className="px-1.5 py-1 text-[12px]" loading={isSendingTest} onClick={onSendTest}>
        Send a test email
      </Button>
      <Button variant="ghost" className="px-1.5 py-1 text-[12px]" loading={isDisconnecting} onClick={onDisconnect}>
        Disconnect
      </Button>
    </div>
  );
}

function NotYetBuilt() {
  return (
    <EmptyState
      icon={<Icon d={ICONS.outreach} size={22} />}
      title="No outreach on this position yet"
      body="Sequences — a first email and up to two follow-ups in the same thread — arrive in the next phase."
    />
  );
}

/** The mail service's host names, as people know them. A host this list lacks reads as its own name. */
const PROVIDER_LABELS: Record<string, string> = { google: "Gmail", microsoft: "Outlook" };

function providerLabel(provider: string): string {
  return PROVIDER_LABELS[provider] ?? provider.charAt(0).toUpperCase() + provider.slice(1);
}

function liveRunsLine({ liveSequences, livePeople }: ConnectedMailbox): string {
  const sequences = `${liveSequences} live ${liveSequences === 1 ? "sequence" : "sequences"}`;
  return `${sequences} (${livePeople} ${livePeople === 1 ? "person" : "people"})`;
}
