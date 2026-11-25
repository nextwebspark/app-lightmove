import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { TabList, type TabOption } from "../../../../components/ui/TabList";
import { tabPanelProps } from "../../../../components/ui/tabPanelProps";
import { EVAL_RUNS_PATH, EVALS_PATH } from "../lib/evalLabels";

export type EvalsTab = "datasets" | "runs";

const TAB_ID = "evals";

const TABS: readonly TabOption<EvalsTab>[] = [
  { value: "datasets", label: "Datasets" },
  { value: "runs", label: "Runs" },
];

/** The Evals head and its two tabs, which a dataset's cases are drawn under as the Datasets tab's own. */
export function EvalsFrame({ tab, action, children }: { tab: EvalsTab; action?: ReactNode; children: ReactNode }) {
  const navigate = useNavigate();
  return (
    <div>
      <PageHeader
        title="Evals"
        subtitle="Score a prompt version on the same cases as the live one before putting it live. Every case is synthetic: no workspace's people, notes or documents are used."
        action={action}
      />
      <TabList
        label="Evals"
        idPrefix={TAB_ID}
        tabs={TABS}
        value={tab}
        onChange={(next) => navigate(next === "runs" ? EVAL_RUNS_PATH : EVALS_PATH)}
        className="mb-4"
      />
      <div {...tabPanelProps(TAB_ID, tab)}>{children}</div>
    </div>
  );
}
