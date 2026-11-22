import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import { useToast } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import * as poolApi from "../api/poolApi";
import type { PoolFilters } from "../api/types";
import { ActivityView } from "../components/pool/ActivityView";
import { PeopleView } from "../components/pool/PeopleView";
import { NO_POOL_FILTERS } from "../lib/poolFilters";
import { PersonDrawer } from "../components/pool/PersonDrawer";
import { drawerTabOf, rememberDrawerTab, rememberedDrawerTab, WORKSPACE_TABS, type DrawerTab } from "../lib/drawerTabs";
import { useAddressedPanel } from "../../../lib/useAddressedPanel";

const VIEWS = [
  { value: "people", label: "People" },
  { value: "activity", label: "Activity" },
] as const;

/**
 * The workspace's Candidates: the People list and Activity feed, staff-only (RequireStaff, CANDIDATE_POOL_MANAGE).
 * The view, open person and tab live in the URL; the filters reset with the page.
 */
export function CandidatesPage() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const [filters, setFilters] = useState<PoolFilters>(NO_POOL_FILTERS);
  const drawer = useAddressedPanel("person", ["tab"]);
  const activity = params.get("view") === "activity";
  const personId = drawer.value;
  const tabParam = params.get("tab");
  const tab = tabParam ? drawerTabOf(tabParam, WORKSPACE_TABS) : rememberedDrawerTab("workspace", WORKSPACE_TABS);

  // An Activity line opens its person on Activity in the address, without making it where every later drawer opens.
  const openPerson = (id: string, on: DrawerTab | null) => drawer.open(id, on ? { tab: on } : undefined);
  const changeTab = (next: DrawerTab) => {
    rememberDrawerTab("workspace", next);
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        updated.set("tab", next);
        return updated;
      },
      { replace: true, state: location.state },
    );
  };
  const switchView = (view: string) =>
    setParams((current) => {
      const updated = new URLSearchParams(current);
      if (view === "activity") updated.set("view", "activity");
      else updated.delete("view");
      return updated;
    });

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
    onError: (error) => toast.error(messageFor(error)),
  });

  const toggle = (
    <SegmentedControl
      label="Candidates view"
      options={VIEWS}
      value={activity ? "activity" : "people"}
      onChange={switchView}
    />
  );

  return (
    <>
      {activity ? (
        <ActivityView toggle={toggle} onOpen={(id) => openPerson(id, "activity")} />
      ) : (
        <PeopleView
          toggle={toggle}
          filters={filters}
          onFiltersChange={setFilters}
          onOpen={(id) => openPerson(id, null)}
          onExport={(personIds) => exporting.mutate(personIds)}
          exporting={exporting.isPending}
        />
      )}

      <PersonDrawer
        personId={personId}
        tab={tab}
        onTabChange={changeTab}
        onClose={drawer.close}
      />
    </>
  );
}
