import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import { useAuth } from "../../auth/AuthProvider";
import * as poolApi from "../api/poolApi";
import type { PoolFilters } from "../api/types";
import { ActivityView } from "../components/pool/ActivityView";
import { PeopleView } from "../components/pool/PeopleView";
import { NO_POOL_FILTERS } from "../lib/poolFilters";
import { PersonDrawer, type PersonDrawerTab } from "../components/pool/PersonDrawer";

const DRAWER_TABS: readonly PersonDrawerTab[] = ["profile", "notes", "timeline"];

/**
 * The workspace's Candidates: everyone the team has mapped on any position, as `Candidates.dc.html`
 * draws them — the People list and the Activity feed, each opening a person's drawer. Staff-only: the
 * route is behind RequireStaff and every read behind CANDIDATE_POOL_MANAGE.
 *
 * <p>The view, the open person and their tab live in the URL, so a link to someone's timeline is a
 * link; the filters are the page's own and reset with it.
 */
export function CandidatesPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState<PoolFilters>(NO_POOL_FILTERS);
  const activity = params.get("view") === "activity";
  const personId = params.get("person");
  const tabParam = params.get("tab");
  const tab: PersonDrawerTab = DRAWER_TABS.includes(tabParam as PersonDrawerTab)
    ? (tabParam as PersonDrawerTab)
    : "profile";

  const size = useQuery({
    queryKey: poolApi.POOL_SIZE_KEY,
    queryFn: ({ signal }) => poolApi.listPool(NO_POOL_FILTERS, 0, 1, signal),
    select: (page) => page.poolSize,
  });

  const navigate = useCallback(
    (change: Record<string, string | null>) =>
      setParams((current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(change)) {
          if (value === null) next.delete(key);
          else next.set(key, value);
        }
        return next;
      }),
    [setParams],
  );

  const exporting = useMutation({
    mutationFn: async (personIds: string[]) => {
      saveBlob(await poolApi.exportPool(filters, personIds), `uncava-candidates-${new Date().toISOString().slice(0, 10)}.csv`);
      return personIds.length;
    },
    onSuccess: (count) =>
      toast(
        count > 0
          ? `Exported ${count} ${count === 1 ? "person" : "people"} as a CSV — recorded in the audit trail`
          : "Exported as a CSV — recorded in the audit trail",
      ),
    onError: (error) => toast(messageFor(error)),
  });

  const workspaceName = user?.workspace?.name ?? "your workspace";
  const subtitle =
    size.data === undefined
      ? "Everyone your team has mapped"
      : `${size.data} ${size.data === 1 ? "person" : "people"} · shared across every position in ${workspaceName}`;

  return (
    <>
      <PageHeader
        title="Candidates"
        subtitle={subtitle}
        action={
          !activity && (
            <Button
              variant="secondary"
              loading={exporting.isPending}
              title="Download every person this view shows as a CSV — recorded in the audit trail"
              onClick={() => exporting.mutate([])}
            >
              <Icon d={ICONS.exportOut} size={14} />
              Export
            </Button>
          )
        }
      />

      <div role="tablist" aria-label="Candidates views" className="mb-4 flex gap-5 border-b border-u-border">
        {[
          { key: "people", label: "People", icon: ICONS.candidates, selected: !activity },
          { key: "activity", label: "Activity", icon: ICONS.activity, selected: activity },
        ].map((view) => (
          <button
            key={view.key}
            type="button"
            role="tab"
            aria-selected={view.selected}
            onClick={() => navigate({ view: view.key === "activity" ? "activity" : null })}
            className={cn(
              "-mb-px flex items-center gap-1.5 border-b-2 pb-2.5 text-[13.5px] font-semibold",
              view.selected ? "border-u-accent text-u-text" : "border-transparent text-u-text3 hover:text-u-text2",
            )}
          >
            <Icon d={view.icon} size={14} />
            {view.label}
          </button>
        ))}
      </div>

      {activity ? (
        <ActivityView onOpen={(id) => navigate({ person: id, tab: "timeline" })} />
      ) : (
        <PeopleView
          filters={filters}
          onFiltersChange={setFilters}
          onOpen={(id) => navigate({ person: id, tab: null })}
          onExport={(personIds) => exporting.mutate(personIds)}
          exporting={exporting.isPending}
        />
      )}

      <PersonDrawer
        personId={personId}
        tab={tab}
        onTabChange={(next) => navigate({ tab: next === "profile" ? null : next })}
        onClose={() => navigate({ person: null, tab: null })}
      />
    </>
  );
}
