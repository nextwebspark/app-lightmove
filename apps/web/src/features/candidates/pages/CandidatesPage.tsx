import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
import { TabList } from "../../../components/ui/TabList";
import { ToolbarButton } from "../../../components/ui/ToolbarButton";
import { tabPanelProps } from "../../../components/ui/tabPanelProps";
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

  const size = useQuery({ queryKey: poolApi.POOL_COUNT_KEY, queryFn: ({ signal }) => poolApi.poolCount(signal) });

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
      const file = personIds.length > 0 ? await poolApi.exportPeople(personIds) : await poolApi.exportPool(filters);
      saveBlob(file, `uncava-candidates-${new Date().toISOString().slice(0, 10)}.csv`);
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
            <ToolbarButton
              loading={exporting.isPending}
              title="Download every person this view shows as a CSV — recorded in the audit trail"
              onClick={() => exporting.mutate([])}
            >
              <Icon d={ICONS.exportOut} size={14} />
              Export
            </ToolbarButton>
          )
        }
      />

      <div className="mb-4 border-b border-u-border">
        <TabList
          label="Candidates views"
          idPrefix="candidates-view"
          className="gap-5"
          value={activity ? "activity" : "people"}
          onChange={(view) => navigate({ view: view === "activity" ? "activity" : null })}
          tabs={[
            { value: "people", label: "People", icon: <Icon d={ICONS.candidates} size={14} /> },
            { value: "activity", label: "Activity", icon: <Icon d={ICONS.activity} size={14} /> },
          ]}
        />
      </div>

      <div {...tabPanelProps("candidates-view", activity ? "activity" : "people")}>
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
      </div>

      <PersonDrawer
        personId={personId}
        tab={tab}
        onTabChange={(next) => navigate({ tab: next === "profile" ? null : next })}
        onClose={() => navigate({ person: null, tab: null })}
      />
    </>
  );
}
