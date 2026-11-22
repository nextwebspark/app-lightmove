import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, Field, Select, useToast } from "../../../components/ui";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as dashboardsApi from "../../dashboards/api/dashboardsApi";
import * as projectsApi from "../../projects/api/projectsApi";
import { isArchived } from "../../projects/lib/filtering";
import * as startPageApi from "../api/startPageApi";
import type { PositionSection, SaveStartPage, StartPageKind } from "../api/types";
import { POSITION_SECTIONS, START_PAGE_OPTIONS } from "../lib/startPage";

const WORKSPACE_DASHBOARDS = { kind: "workspace" } as const;

interface Choice {
  kind: StartPageKind;
  dashboardId: string;
  projectId: string;
  section: PositionSection;
}

const sameChoice = (left: Choice, right: Choice) =>
  left.kind === right.kind
  && (left.kind !== "DASHBOARD" || left.dashboardId === right.dashboardId)
  && (left.kind !== "POSITION" || (left.projectId === right.projectId && left.section === right.section));

/** Settings → Profile → Start page: where Uncava opens in this workspace on a sign-in, or opened with no link. */
export function StartPageSection() {
  const { user, reload } = useAuth();
  const toast = useToast();
  const location = useLocation();
  const sectionRef = useRef<HTMLElement>(null);
  const client = isPureClient(user?.workspace?.roles ?? []);
  const startPage = user?.startPage ?? null;
  const saved: Choice = {
    kind: startPage?.available ? startPage.kind : "HOME",
    dashboardId: startPage?.available ? startPage.dashboardId ?? "" : "",
    projectId: startPage?.available ? startPage.projectId ?? "" : "",
    section: startPage?.available ? startPage.section ?? "UNIVERSE" : "UNIVERSE",
  };
  const [choice, setChoice] = useState<Choice>(saved);
  const [errors, setErrors] = useState<{ dashboard?: string; project?: string }>({});
  const [saving, setSaving] = useState(false);

  const options = START_PAGE_OPTIONS.filter((option) => !client || !option.staffOnly);
  const sections = POSITION_SECTIONS.filter((option) => !client || !option.staffOnly);
  const kinds = options.map((option) => option.kind);
  const pick = (kind: StartPageKind) => {
    setChoice((current) => ({ ...current, kind }));
    setErrors({});
  };
  const radio = useRadioGroupKeys(kinds, choice.kind, pick);

  const dashboards = useQuery({
    queryKey: dashboardsApi.DASHBOARDS_KEY(WORKSPACE_DASHBOARDS),
    queryFn: ({ signal }) => dashboardsApi.listDashboards(WORKSPACE_DASHBOARDS, signal),
    enabled: !client && choice.kind === "DASHBOARD",
  });
  const positions = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
    enabled: choice.kind === "POSITION",
  });
  const openPositions = (positions.data ?? []).filter(
    (project) => !isArchived(project) || project.id === saved.projectId,
  );

  useEffect(() => {
    if (location.hash === "#start-page") sectionRef.current?.scrollIntoView({ block: "start" });
  }, [location.hash]);

  const unchanged = sameChoice(choice, saved);

  const handleSave = async () => {
    if (choice.kind === "DASHBOARD" && !choice.dashboardId) {
      setErrors({ dashboard: "Choose a dashboard" });
      return;
    }
    if (choice.kind === "POSITION" && !choice.projectId) {
      setErrors({ project: "Choose a position" });
      return;
    }
    const body: SaveStartPage =
      choice.kind === "DASHBOARD"
        ? { kind: "DASHBOARD", dashboardId: choice.dashboardId }
        : choice.kind === "POSITION"
          ? { kind: "POSITION", projectId: choice.projectId, section: choice.section }
          : { kind: choice.kind };
    setSaving(true);
    try {
      await startPageApi.saveStartPage(body);
      await reload();
      toast(choice.kind === "HOME" ? "Uncava opens on Home" : "Uncava will open here next time");
    } catch (error) {
      if (codeOf(error) === "START_PAGE_UNAVAILABLE" && (choice.kind === "DASHBOARD" || choice.kind === "POSITION")) {
        setErrors(choice.kind === "DASHBOARD" ? { dashboard: messageFor(error) } : { project: messageFor(error) });
      } else {
        toast.error(messageFor(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <section
      id="start-page"
      ref={sectionRef}
      aria-labelledby="start-page-title"
      className="mt-4 scroll-mt-4 rounded-u-card border border-u-border bg-u-raised p-5"
    >
      <h2 id="start-page-title" className="text-body font-semibold">
        Start page
      </h2>
      <p className="mb-4 mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">
        Where Uncava opens in {user?.workspace?.name ?? "this workspace"} when you sign in or open it without a link. A
        link you follow still opens where it points.
      </p>

      {startPage && !startPage.available && (
        <div role="status" className="mb-3.5 rounded-u-chip border border-u-offlimits bg-u-offlimits-tint px-3 py-2.5 text-note text-u-text">
          Your start page is no longer available to you, so Uncava opens Home. Choose another below.
        </div>
      )}

      <div
        role="radiogroup"
        aria-label="Start page"
        ref={radio.ref}
        onKeyDown={radio.onKeyDown}
        className="flex flex-col gap-1.5 rounded-u-chip border border-u-border bg-u-surface p-1.5"
      >
        {options.map((option) => {
          const on = choice.kind === option.kind;
          return (
            <div
              key={option.kind}
              className={cn("rounded-u-control border", on ? "border-u-accent bg-u-accent-tint" : "border-u-border")}
            >
              <button
                type="button"
                role="radio"
                aria-checked={on}
                tabIndex={on ? 0 : -1}
                onClick={() => pick(option.kind)}
                className="flex w-full flex-wrap items-center gap-2.5 px-3 py-2 text-left"
              >
                <span
                  aria-hidden
                  className={cn(
                    "grid size-3.5 shrink-0 place-items-center rounded-full border-[1.5px]",
                    on ? "border-u-accent" : "border-u-border-strong",
                  )}
                >
                  {on && <span className="size-1.5 rounded-full bg-u-accent" />}
                </span>
                <span className="text-body font-medium">{option.label}</span>
                <span className="ml-auto text-meta text-u-text3">{option.hint}</span>
              </button>

              {on && option.kind === "DASHBOARD" && (
                <div className="px-3 pb-1 ps-9">
                  <Field label="Dashboard" error={errors.dashboard}>
                    <Select
                      aria-label="Dashboard"
                      value={choice.dashboardId}
                      invalid={Boolean(errors.dashboard)}
                      onChange={(event) => {
                        setChoice((current) => ({ ...current, dashboardId: event.target.value }));
                        setErrors({});
                      }}
                    >
                      <option value="">{dashboards.isError ? "Your dashboards could not be loaded" : "Choose a dashboard"}</option>
                      {(dashboards.data ?? []).map((dashboard) => (
                        <option key={dashboard.id} value={dashboard.id}>
                          {dashboard.name}
                        </option>
                      ))}
                    </Select>
                  </Field>
                </div>
              )}

              {on && option.kind === "POSITION" && (
                <div className="grid gap-x-2.5 px-3 pb-1 ps-9 md:grid-cols-2">
                  <Field label="Position" error={errors.project}>
                    <Select
                      aria-label="Position"
                      value={choice.projectId}
                      invalid={Boolean(errors.project)}
                      onChange={(event) => {
                        setChoice((current) => ({ ...current, projectId: event.target.value }));
                        setErrors({});
                      }}
                    >
                      <option value="">{positions.isError ? "Your positions could not be loaded" : "Choose a position"}</option>
                      {openPositions.map((project) => (
                        <option key={project.id} value={project.id}>
                          {project.positionTitle}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Open on">
                    <Select
                      aria-label="Open the position on"
                      value={choice.section}
                      onChange={(event) =>
                        setChoice((current) => ({ ...current, section: event.target.value as PositionSection }))
                      }
                    >
                      {sections.map((option) => (
                        <option key={option.section} value={option.section}>
                          {option.label}
                        </option>
                      ))}
                    </Select>
                  </Field>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="mt-4 flex justify-end">
        <Button onClick={() => void handleSave()} disabled={unchanged} loading={saving}>
          Save start page
        </Button>
      </div>
    </section>
  );
}
