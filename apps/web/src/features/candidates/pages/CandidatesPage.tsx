import { useMutation } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useToast } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import * as poolApi from "../api/poolApi";
import type { PoolFilters } from "../api/types";
import { ActivityView } from "../components/pool/ActivityView";
import { PeopleView } from "../components/pool/PeopleView";
import { NO_POOL_FILTERS } from "../lib/poolFilters";
import { PersonDrawer, type PersonDrawerTab } from "../components/pool/PersonDrawer";

const DRAWER_TABS: readonly PersonDrawerTab[] = ["profile", "contact", "records", "timeline"];
const RETIRED_TABS: Readonly<Record<string, PersonDrawerTab>> = { notes: "records", documents: "records" };

const VIEWS = [
  { value: "people", label: "People" },
  { value: "activity", label: "Activity" },
] as const;

/**
 * The workspace's Candidates: everyone the team has mapped on any position, laid out as Strategy is — a
 * toolbar, a filter rail hidden until asked for, and the grid filling the rest — as the People list and
 * the Activity feed, each opening a person's drawer. Staff-only: the route is behind RequireStaff and
 * every read behind CANDIDATE_POOL_MANAGE.
 *
 * <p>The view, the open person and their tab live in the URL, so a link to someone's timeline is a
 * link; the filters are the page's own and reset with it.
 */
export function CandidatesPage() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState<PoolFilters>(NO_POOL_FILTERS);
  const activity = params.get("view") === "activity";
  const personId = params.get("person");
  const tabParam = params.get("tab");
  const tab: PersonDrawerTab = DRAWER_TABS.includes(tabParam as PersonDrawerTab)
    ? (tabParam as PersonDrawerTab)
    : (RETIRED_TABS[tabParam ?? ""] ?? "profile");

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

  const toggle = (
    <SegmentedControl
      label="Candidates view"
      options={VIEWS}
      value={activity ? "activity" : "people"}
      onChange={(view) => navigate({ view: view === "activity" ? "activity" : null })}
    />
  );

  return (
    <>
      {activity ? (
        <ActivityView toggle={toggle} onOpen={(id) => navigate({ person: id, tab: "timeline" })} />
      ) : (
        <PeopleView
          toggle={toggle}
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
