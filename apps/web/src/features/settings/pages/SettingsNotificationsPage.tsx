import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { CardsSkeleton, Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as notificationsApi from "../../notifications/api/notificationsApi";
import type { NotificationPreferenceKind, NotificationPreferences } from "../../notifications/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskRules } from "../../tasks/api/types";

/** The eight events, in the mockup's order, and the words each is switched under. */
const EVENTS: readonly { kind: NotificationPreferenceKind; label: string }[] = [
  { kind: "task_assigned", label: "A task is assigned to me" },
  { kind: "task_due", label: "A task of mine is due soon or overdue" },
  { kind: "mentioned", label: "Someone @mentions me" },
  { kind: "outreach_replied", label: "An executive replies to my sequence" },
  { kind: "outreach_bounced", label: "An email of mine bounces" },
  { kind: "call_booked", label: "A call is booked through my link" },
  { kind: "added_to_team", label: "I'm added to a position's team" },
  { kind: "tasks_moved", label: "Tasks are moved to me" },
];

/** Only these are ever emailed one by one; the rest reach an inbox in the daily digest. */
const EMAILED_KINDS: ReadonlySet<NotificationPreferenceKind> = new Set(["task_assigned", "mentioned"]);

const AUTOMATIC_TASKS: readonly { key: keyof TaskRules; trigger: string; task: string }[] = [
  { key: "reply", trigger: "Reply", task: "Reply to {name}" },
  { key: "bounce", trigger: "Bounce", task: "Find a working email for {name}" },
  { key: "bookedCall", trigger: "Booked call", task: "Debrief {name}" },
];

type Channel = "inApp" | "email";

/**
 * Settings → Notifications (`Settings.dc.html`, `notifications`): what reaches this person, and where. Every
 * switch saves the moment it is flipped. An admin also switches the workspace's automatic tasks here.
 */
export function SettingsNotificationsPage() {
  const { user } = useAuth();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const queryClient = useQueryClient();
  const toast = useToast();

  const preferences = useQuery({
    queryKey: notificationsApi.NOTIFICATION_PREFERENCES_KEY,
    queryFn: ({ signal }) => notificationsApi.getNotificationPreferences(signal),
  });

  const savingPreferences = useMutation({
    mutationFn: (next: NotificationPreferences) => notificationsApi.putNotificationPreferences(next),
    onMutate: async (next) => {
      await queryClient.cancelQueries({ queryKey: notificationsApi.NOTIFICATION_PREFERENCES_KEY });
      queryClient.setQueryData(notificationsApi.NOTIFICATION_PREFERENCES_KEY, next);
    },
    onSuccess: () => toast("Saved"),
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: notificationsApi.NOTIFICATION_PREFERENCES_KEY });
    },
  });

  const current = preferences.data;
  const channelOf = (kind: NotificationPreferenceKind, channel: Channel) =>
    current?.kinds.find((entry) => entry.kind === kind)?.[channel] ?? true;

  const handleChannel = (kind: NotificationPreferenceKind, channel: Channel, on: boolean) => {
    if (!current) return;
    const kinds = current.kinds.some((entry) => entry.kind === kind)
      ? current.kinds.map((entry) => (entry.kind === kind ? { ...entry, [channel]: on } : entry))
      : [...current.kinds, { kind, inApp: channelOf(kind, "inApp"), email: channelOf(kind, "email"), [channel]: on }];
    savingPreferences.mutate({ ...current, kinds });
  };

  return (
    <>
      <PageHeader title="Notifications" subtitle="What reaches you, and where" />

      {preferences.isError ? (
        <p className="text-note text-u-text3">{messageFor(preferences.error)}</p>
      ) : preferences.isPending ? (
        <CardsSkeleton />
      ) : (
        <>
          <section aria-label="Events" className="rounded-u-card border border-u-border bg-u-raised px-5 pb-3.5 pt-1">
            <div className="grid grid-cols-[minmax(0,1fr)_72px_72px] items-center gap-3 border-b border-u-border pb-2.5 pt-3 font-mono type-micro-label text-u-text3">
              <span>Event</span>
              <span className="text-center">In app</span>
              <span className="text-center">Email</span>
            </div>
            <ul className="divide-y divide-u-border">
              {EVENTS.map((event) => (
                <li key={event.kind} className="grid grid-cols-[minmax(0,1fr)_72px_72px] items-center gap-3 py-2.5">
                  <span className="text-body text-u-text">{event.label}</span>
                  <span className="flex justify-center">
                    <Toggle
                      checked={channelOf(event.kind, "inApp")}
                      label={`${event.label}, in app`}
                      onChange={(on) => handleChannel(event.kind, "inApp", on)}
                    />
                  </span>
                  <span className="flex justify-center">
                    {EMAILED_KINDS.has(event.kind) ? (
                      <Toggle
                        checked={channelOf(event.kind, "email")}
                        label={`${event.label}, by email`}
                        onChange={(on) => handleChannel(event.kind, "email", on)}
                      />
                    ) : (
                      <span className="text-center font-mono text-meta text-u-text3">Daily digest</span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex items-center justify-end gap-1.5 font-mono text-meta text-u-text3">
              <Icon d={ICONS.mail} size={12} />
              Emailed only if still unread after 10 minutes.
            </div>
          </section>

          <section aria-label="Daily digest" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <div className="text-body font-semibold text-u-text">Daily digest</div>
                <div className="mt-0.5 font-mono text-meta text-u-text3">
                  Every weekday at 08:00 ({preferences.data.timeZone})
                </div>
              </div>
              <Toggle
                checked={preferences.data.digest}
                label="Daily digest"
                onChange={(digest) => savingPreferences.mutate({ ...preferences.data, digest })}
              />
            </div>
            {preferences.data.digest && <DigestPreview />}
          </section>

          {isAdmin && <AutomaticTasksCard />}
        </>
      )}
    </>
  );
}

/** Today's digest as it stands: what is due, what is late, what is unread. */
function DigestPreview() {
  const myTasks = useQuery({
    queryKey: tasksApi.MY_TASKS_KEY("today"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("today", signal),
  });
  const unread = useQuery({
    queryKey: notificationsApi.UNREAD_COUNT_KEY,
    queryFn: ({ signal }) => notificationsApi.getUnreadCount(signal),
  });
  const parts = [formatInstantDate(new Date().toISOString())];
  if (myTasks.data) parts.push(`${myTasks.data.counts.today} due today`, `${myTasks.data.counts.overdue} overdue`);
  if (unread.data) parts.push(`${unread.data.unreadCount} unread`);

  return (
    <div className="mt-3.5 flex flex-wrap items-center gap-2.5 rounded-lg border border-u-border bg-u-surface px-3.5 py-2.5">
      <span className="font-mono type-micro-label text-u-text3">Preview</span>
      <span className="font-mono text-note text-u-text2">{parts.join(" · ")}</span>
    </div>
  );
}

/** The workspace's switches for the tasks outreach files by itself. Admin-only, as the write behind it is. */
function AutomaticTasksCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const rules = useQuery({ queryKey: tasksApi.TASK_RULES_KEY, queryFn: ({ signal }) => tasksApi.getTaskRules(signal) });

  const saving = useMutation({
    mutationFn: (next: TaskRules) => tasksApi.putTaskRules(next),
    onMutate: async (next) => {
      await queryClient.cancelQueries({ queryKey: tasksApi.TASK_RULES_KEY });
      queryClient.setQueryData(tasksApi.TASK_RULES_KEY, next);
    },
    onSuccess: () => toast("Saved for the whole workspace"),
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: tasksApi.TASK_RULES_KEY });
    },
  });

  return (
    <section aria-label="Automatic tasks for the workspace" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body font-semibold text-u-text">Automatic tasks for the workspace</span>
        <span className="rounded bg-u-surface px-1.5 py-px font-mono text-meta font-medium text-u-text3">Admin</span>
      </div>
      <div className="mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">
        When outreach needs a person, Uncava files a task for whoever started the sequence.
      </div>
      {rules.isError ? (
        <p className="mt-3 text-note text-u-text3">{messageFor(rules.error)}</p>
      ) : (
        <ul className="mt-2 divide-y divide-u-border">
          {AUTOMATIC_TASKS.map((rule) => (
            <li key={rule.key} className="flex items-center gap-3 py-2.5">
              <span className="w-24 flex-none font-mono text-note font-medium text-u-text2">{rule.trigger}</span>
              <Icon d={ICONS.arrowRight} size={13} className="flex-none text-u-text3" />
              <span className="min-w-0 flex-1 text-body text-u-text">{rule.task}</span>
              <Toggle
                checked={rules.data?.[rule.key] ?? true}
                disabled={!rules.data}
                label={`Automatic task on ${rule.trigger}`}
                onChange={(on) => rules.data && saving.mutate({ ...rules.data, [rule.key]: on })}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
