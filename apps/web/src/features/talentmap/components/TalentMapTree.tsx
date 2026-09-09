import { useEffect, useMemo, useRef, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { countOf } from "../lib/talentMapFeatures";
import {
  UNLOCATED_KEY,
  type TalentMapTree as Tree,
  type TreeCompany,
  type TreeCountry,
  type TreeExecutive,
} from "../lib/talentMapTree";

/** One line of the panel as the keyboard walks it: a group header or a row it can open. */
type Line =
  | { kind: "group"; key: string; label: string; count: string; expandable: boolean; muted?: boolean }
  | { kind: "node"; node: TreeCompany | TreeExecutive; groupKey: string; depth: number };

/**
 * The mapping panel's tree: country → company → executives, a `role="tree"` whose rows mirror the
 * globe's pins. Hover and selection are the parent's, so a row and its pin light up together
 * whichever was touched; a row's Open (or Enter, or a double click) is the drawer, and a single click
 * is the pin.
 */
export function TalentMapTree({
  tree,
  projectId,
  expanded,
  onToggle,
  selectedId,
  hoveredId,
  onSelect,
  onHover,
  onOpen,
}: {
  tree: Tree;
  projectId: string;
  /** Group keys (a country's, a company's, the unlocated group's) currently open. */
  expanded: ReadonlySet<string>;
  onToggle: (key: string) => void;
  selectedId: string | null;
  hoveredId: string | null;
  onSelect: (id: string | null) => void;
  onHover: (id: string | null) => void;
  onOpen: (node: TreeCompany | TreeExecutive) => void;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const lines = useMemo(() => linesOf(tree, expanded), [tree, expanded]);

  // A pin click selects a row the reader may have scrolled past; bring it back into view.
  useEffect(() => {
    if (!selectedId || !rootRef.current) return;
    const row = rootRef.current.querySelector<HTMLElement>(`[data-row-id="${selectedId}"]`);
    // Optional call: jsdom, where the tests run, has no scrollIntoView at all.
    row?.scrollIntoView?.({ block: "nearest" });
  }, [selectedId, lines]);

  const focusLine = (index: number) => {
    const target = rootRef.current?.querySelectorAll<HTMLElement>("[data-line]")[index];
    target?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>, line: Line, index: number) => {
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        focusLine(Math.min(index + 1, lines.length - 1));
        return;
      case "ArrowUp":
        event.preventDefault();
        focusLine(Math.max(index - 1, 0));
        return;
      case "ArrowRight":
        event.preventDefault();
        if (line.kind === "group" && line.expandable && !expanded.has(line.key)) onToggle(line.key);
        if (line.kind === "node" && line.node.kind === "company" && line.node.executives.length
          && !expanded.has(line.node.id)) onToggle(line.node.id);
        return;
      case "ArrowLeft":
        event.preventDefault();
        if (line.kind === "group" && expanded.has(line.key)) onToggle(line.key);
        if (line.kind === "node" && line.node.kind === "company" && expanded.has(line.node.id)) {
          onToggle(line.node.id);
        }
        return;
      case "Enter":
        event.preventDefault();
        if (line.kind === "node") onOpen(line.node);
        else if (line.expandable) onToggle(line.key);
        return;
      case " ":
        event.preventDefault();
        if (line.kind === "node") onSelect(line.node.id);
        return;
      case "Escape":
        onSelect(null);
        return;
      default:
    }
  };

  return (
    <div ref={rootRef} role="tree" aria-label="Mapping" className="flex flex-col py-1">
      {lines.map((line, index) =>
        line.kind === "group" ? (
          <GroupRow
            key={line.key}
            line={line}
            open={expanded.has(line.key)}
            onToggle={() => onToggle(line.key)}
            onKeyDown={(event) => handleKeyDown(event, line, index)}
          />
        ) : (
          <NodeRow
            key={line.node.id}
            line={line}
            projectId={projectId}
            open={line.node.kind === "company" && expanded.has(line.node.id)}
            selected={selectedId === line.node.id}
            hovered={hoveredId === line.node.id}
            onToggle={() => onToggle(line.node.id)}
            onSelect={() => onSelect(line.node.id)}
            onHover={onHover}
            onOpen={() => onOpen(line.node)}
            onKeyDown={(event) => handleKeyDown(event, line, index)}
          />
        ),
      )}
    </div>
  );
}

function GroupRow({
  line,
  open,
  onToggle,
  onKeyDown,
}: {
  line: Extract<Line, { kind: "group" }>;
  open: boolean;
  onToggle: () => void;
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void;
}) {
  return (
    <div
      role="treeitem"
      aria-expanded={line.expandable ? open : undefined}
      aria-label={`${line.label}, ${line.count}`}
      tabIndex={0}
      data-line=""
      onClick={line.expandable ? onToggle : undefined}
      onKeyDown={onKeyDown}
      className={cn(
        "mt-1 flex cursor-pointer select-none items-center gap-1.5 px-3 py-1.5 outline-none focus-visible:bg-panel2",
        line.muted && "cursor-default",
      )}
    >
      {line.expandable ? (
        <Icon
          d={ICONS.chevronRight}
          size={13}
          className={cn("flex-none text-text3 transition-transform", open && "rotate-90")}
        />
      ) : (
        <span className="w-[13px] flex-none" />
      )}
      <span className={cn("truncate text-[13px] font-semibold", line.muted ? "text-text3" : "text-text")}>
        {line.label}
      </span>
      <span className="ms-auto flex-none font-mono text-[11px] text-text3">{line.count}</span>
    </div>
  );
}

function NodeRow({
  line,
  projectId,
  open,
  selected,
  hovered,
  onToggle,
  onSelect,
  onHover,
  onOpen,
  onKeyDown,
}: {
  line: Extract<Line, { kind: "node" }>;
  projectId: string;
  open: boolean;
  selected: boolean;
  hovered: boolean;
  onToggle: () => void;
  onSelect: () => void;
  onHover: (id: string | null) => void;
  onOpen: () => void;
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void;
}) {
  const { node } = line;
  const isCompany = node.kind === "company";
  const name = isCompany ? node.company.companyName : node.candidate.fullName;
  const located = node.location !== null || (!isCompany && node.seatedAt !== null);
  const expandable = isCompany && node.executives.length > 0;

  return (
    <div
      role="treeitem"
      aria-selected={selected}
      aria-expanded={expandable ? open : undefined}
      aria-label={name}
      tabIndex={0}
      data-line=""
      data-row-id={node.id}
      onClick={onSelect}
      onDoubleClick={onOpen}
      onMouseEnter={() => onHover(node.id)}
      onMouseLeave={() => onHover(null)}
      onKeyDown={onKeyDown}
      style={{ paddingInlineStart: 12 + line.depth * 18 }}
      className={cn(
        "group flex cursor-pointer select-none items-center gap-2 border-s-2 py-1.5 pe-2 outline-none transition-colors",
        selected
          ? "border-amber bg-amber-dim"
          : hovered
            ? "border-transparent bg-panel2"
            : "border-transparent hover:bg-panel2 focus-visible:bg-panel2",
      )}
    >
      {expandable ? (
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            onToggle();
          }}
          aria-label={open ? `Collapse ${name}` : `Expand ${name}`}
          className="-ms-1 flex-none rounded p-0.5 text-text3 hover:text-text"
        >
          <Icon d={ICONS.chevronRight} size={12} className={cn("transition-transform", open && "rotate-90")} />
        </button>
      ) : (
        <span className="w-[13px] flex-none" />
      )}

      {isCompany ? (
        <CompanyLogo name={node.company.companyName} logo={node.company.logoUrl} size={22} />
      ) : (
        <CandidateAvatar projectId={projectId} candidate={node.candidate} size="sm" />
      )}

      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className={cn(
              "size-2 flex-none rounded-full",
              !located ? "bg-line" : isCompany ? "bg-text" : "bg-sky",
            )}
          />
          <span className="truncate text-[13px] font-medium text-text">{name}</span>
        </span>
        <span className="block truncate ps-3.5 text-[11.5px] text-text3">
          {isCompany
            ? [
                countOf(node.executives.length, "exec"),
                node.location?.placeLabel ?? node.company.companyCity ?? (located ? null : "No location"),
              ]
                .filter(Boolean)
                .join(" · ")
            : node.candidate.title ?? (located ? "" : "No location")}
        </span>
      </span>

      <button
        type="button"
        onClick={(event) => {
          event.stopPropagation();
          onOpen();
        }}
        aria-label={`Open ${name}`}
        title="Open"
        className="flex-none rounded p-1 text-text3 opacity-0 transition hover:bg-panel hover:text-text group-hover:opacity-100 focus-visible:opacity-100"
      >
        <Icon d={ICONS.arrowRight} size={13} />
      </button>
    </div>
  );
}

/** The panel as a flat list of what is visible, in reading order — what the keyboard walks. */
function linesOf(tree: Tree, expanded: ReadonlySet<string>): Line[] {
  const lines: Line[] = [];
  const pushCompany = (company: TreeCompany, groupKey: string) => {
    lines.push({ kind: "node", node: company, groupKey, depth: 1 });
    if (expanded.has(company.id)) {
      for (const executive of company.executives) {
        lines.push({ kind: "node", node: executive, groupKey, depth: 2 });
      }
    }
  };
  const pushCountry = (country: TreeCountry) => {
    lines.push({
      kind: "group",
      key: country.key,
      label: country.name,
      count: countOf(country.companyCount, "company", "companies"),
      expandable: true,
    });
    if (!expanded.has(country.key)) return;
    for (const company of country.companies) pushCompany(company, country.key);
    if (country.unmapped.length) {
      lines.push({
        kind: "group",
        key: `${country.key}:unmapped`,
        label: "No company in this mandate",
        count: countOf(country.unmapped.length, "exec"),
        expandable: false,
        muted: true,
      });
      for (const executive of country.unmapped) {
        lines.push({ kind: "node", node: executive, groupKey: country.key, depth: 1 });
      }
    }
  };
  tree.countries.forEach(pushCountry);

  const unlocatedCount = tree.unlocated.companies.length + tree.unlocated.executives.length;
  if (unlocatedCount) {
    lines.push({
      kind: "group",
      key: UNLOCATED_KEY,
      label: "No location",
      count: `${unlocatedCount}`,
      expandable: true,
      muted: true,
    });
    if (expanded.has(UNLOCATED_KEY)) {
      for (const company of tree.unlocated.companies) pushCompany(company, UNLOCATED_KEY);
      for (const executive of tree.unlocated.executives) {
        lines.push({ kind: "node", node: executive, groupKey: UNLOCATED_KEY, depth: 1 });
      }
    }
  }
  return lines;
}
