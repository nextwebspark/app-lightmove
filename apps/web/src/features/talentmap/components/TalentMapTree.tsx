import { memo, useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { countOf } from "../lib/talentMapFeatures";
import {
  UNLOCATED_KEY,
  type MappingTree,
  type TreeCompany,
  type TreeCountry,
  type TreeExecutive,
} from "../lib/talentMapTree";

/** One line of the panel as the keyboard walks it: a group header or a row it can open. */
type Line =
  | { kind: "group"; key: string; label: string; count: string; expandable: boolean; muted?: boolean }
  | { kind: "node"; node: TreeCompany | TreeExecutive; depth: number };

/**
 * What a row calls back into, held stable for the life of the panel so a row can be memoized: the
 * parent rebuilds its own handlers on every hover, and a changed handler would re-render all of
 * them. Behind each is the current prop, read at the moment the reader clicks.
 */
interface RowHandlers {
  toggle: (key: string) => void;
  select: (id: string) => void;
  hover: (id: string | null) => void;
  open: (node: TreeCompany | TreeExecutive) => void;
  keyDown: (event: KeyboardEvent<HTMLDivElement>, index: number) => void;
  focused: (index: number) => void;
  register: (index: number, row: HTMLElement | null) => void;
}

/**
 * The mapping panel's tree: country → company → executives, a `role="tree"` whose rows mirror the
 * globe's pins. Hover and selection are the parent's, so a row and its pin light up together
 * whichever was touched; a row's Open (or Enter, or a double click) is the drawer, and a single click
 * is the pin.
 *
 * <p>One tab stop, not one per row: the ARIA tree pattern, and the only workable one at this
 * screen's caps — 2000 companies would otherwise be 2000 stops between the toolbar and the globe.
 * Tab enters the tree where the reader left it and the arrows move within it.
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
  tree: MappingTree;
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
  const rowsRef = useRef(new Map<number, HTMLElement>());
  const lines = useMemo(() => linesOf(tree, expanded), [tree, expanded]);
  const [activeIndex, setActiveIndex] = useState(0);
  const active = lines.length ? Math.min(activeIndex, lines.length - 1) : 0;

  // The props as they stand, for the stable handlers below to read at click time.
  const latest = useRef({ lines, expanded, onToggle, onSelect, onHover, onOpen });
  useEffect(() => {
    latest.current = { lines, expanded, onToggle, onSelect, onHover, onOpen };
  });

  // A pin click selects a row the reader may have scrolled past; bring it back into view.
  useEffect(() => {
    if (!selectedId || !rootRef.current) return;
    const row = rootRef.current.querySelector<HTMLElement>(`[data-row-id="${selectedId}"]`);
    // Optional call: jsdom, where the tests run, has no scrollIntoView at all.
    row?.scrollIntoView?.({ block: "nearest" });
  }, [selectedId, lines]);

  const focusLine = useCallback((index: number) => {
    setActiveIndex(index);
    rowsRef.current.get(index)?.focus();
  }, []);

  const handlers = useMemo<RowHandlers>(() => {
    const keyDown = (event: KeyboardEvent<HTMLDivElement>, index: number) => {
      const held = latest.current;
      const line = held.lines[index];
      if (!line) return;
      switch (event.key) {
        case "ArrowDown":
          event.preventDefault();
          focusLine(Math.min(index + 1, held.lines.length - 1));
          return;
        case "ArrowUp":
          event.preventDefault();
          focusLine(Math.max(index - 1, 0));
          return;
        case "ArrowRight":
          event.preventDefault();
          if (line.kind === "group" && line.expandable && !held.expanded.has(line.key)) {
            held.onToggle(line.key);
          }
          if (line.kind === "node" && line.node.kind === "company" && line.node.executives.length
            && !held.expanded.has(line.node.id)) held.onToggle(line.node.id);
          return;
        case "ArrowLeft":
          event.preventDefault();
          if (line.kind === "group" && held.expanded.has(line.key)) held.onToggle(line.key);
          if (line.kind === "node" && line.node.kind === "company" && held.expanded.has(line.node.id)) {
            held.onToggle(line.node.id);
          }
          return;
        case "Enter":
          event.preventDefault();
          if (line.kind === "node") held.onOpen(line.node);
          else if (line.expandable) held.onToggle(line.key);
          return;
        case " ":
          event.preventDefault();
          if (line.kind === "node") held.onSelect(line.node.id);
          return;
        case "Escape":
          held.onSelect(null);
          return;
        default:
      }
    };
    return {
      toggle: (key) => latest.current.onToggle(key),
      select: (id) => latest.current.onSelect(id),
      hover: (id) => latest.current.onHover(id),
      open: (node) => latest.current.onOpen(node),
      keyDown,
      focused: setActiveIndex,
      register: (index, row) => {
        if (row) rowsRef.current.set(index, row);
        else rowsRef.current.delete(index);
      },
    };
  }, [focusLine]);

  return (
    <div ref={rootRef} role="tree" aria-label="Mapping" className="flex flex-col py-1">
      {lines.map((line, index) =>
        line.kind === "group" ? (
          <GroupRow
            key={line.key}
            line={line}
            index={index}
            open={expanded.has(line.key)}
            tabbable={index === active}
            handlers={handlers}
          />
        ) : (
          <NodeRow
            key={line.node.id}
            line={line}
            index={index}
            projectId={projectId}
            open={line.node.kind === "company" && expanded.has(line.node.id)}
            selected={selectedId === line.node.id}
            hovered={hoveredId === line.node.id}
            tabbable={index === active}
            handlers={handlers}
          />
        ),
      )}
    </div>
  );
}

const GroupRow = memo(function GroupRow({
  line,
  index,
  open,
  tabbable,
  handlers,
}: {
  line: Extract<Line, { kind: "group" }>;
  index: number;
  open: boolean;
  tabbable: boolean;
  handlers: RowHandlers;
}) {
  return (
    <div
      ref={(row) => handlers.register(index, row)}
      role="treeitem"
      aria-expanded={line.expandable ? open : undefined}
      aria-label={`${line.label}, ${line.count}`}
      tabIndex={tabbable ? 0 : -1}
      onFocus={() => handlers.focused(index)}
      onClick={line.expandable ? () => handlers.toggle(line.key) : undefined}
      onKeyDown={(event) => handlers.keyDown(event, index)}
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
});

const NodeRow = memo(function NodeRow({
  line,
  index,
  projectId,
  open,
  selected,
  hovered,
  tabbable,
  handlers,
}: {
  line: Extract<Line, { kind: "node" }>;
  index: number;
  projectId: string;
  open: boolean;
  selected: boolean;
  hovered: boolean;
  tabbable: boolean;
  handlers: RowHandlers;
}) {
  const { node } = line;
  const isCompany = node.kind === "company";
  const name = isCompany ? node.company.companyName : node.candidate.fullName;
  const located = node.location !== null || (!isCompany && node.seatedAt !== null);
  const expandable = isCompany && node.executives.length > 0;

  return (
    <div
      ref={(row) => handlers.register(index, row)}
      role="treeitem"
      aria-selected={selected}
      aria-expanded={expandable ? open : undefined}
      aria-label={name}
      tabIndex={tabbable ? 0 : -1}
      data-row-id={node.id}
      onFocus={() => handlers.focused(index)}
      onClick={() => handlers.select(node.id)}
      onDoubleClick={() => handlers.open(node)}
      onMouseEnter={() => handlers.hover(node.id)}
      onMouseLeave={() => handlers.hover(null)}
      onKeyDown={(event) => handlers.keyDown(event, index)}
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
          tabIndex={-1}
          onClick={(event) => {
            event.stopPropagation();
            handlers.toggle(node.id);
          }}
          aria-label={open ? `Collapse ${name}` : `Expand ${name}`}
          className="-ms-1 flex-none cursor-pointer rounded p-0.5 text-text3 hover:text-text"
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
        tabIndex={-1}
        onClick={(event) => {
          event.stopPropagation();
          handlers.open(node);
        }}
        aria-label={`Open ${name}`}
        title="Open"
        className="flex-none cursor-pointer rounded p-1 text-text3 opacity-0 transition hover:bg-panel hover:text-text group-hover:opacity-100 focus-visible:opacity-100"
      >
        <Icon d={ICONS.arrowRight} size={13} />
      </button>
    </div>
  );
});

/** The panel as a flat list of what is visible, in reading order — what the keyboard walks. */
function linesOf(tree: MappingTree, expanded: ReadonlySet<string>): Line[] {
  const lines: Line[] = [];
  const pushCompany = (company: TreeCompany) => {
    lines.push({ kind: "node", node: company, depth: 1 });
    if (expanded.has(company.id)) {
      for (const executive of company.executives) {
        lines.push({ kind: "node", node: executive, depth: 2 });
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
    country.companies.forEach(pushCompany);
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
        lines.push({ kind: "node", node: executive, depth: 1 });
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
      tree.unlocated.companies.forEach(pushCompany);
      for (const executive of tree.unlocated.executives) {
        lines.push({ kind: "node", node: executive, depth: 1 });
      }
    }
  }
  return lines;
}
