/**
 * The strategy's left nav. Only Sector Scope is built this session; the other scope filters and the
 * lists are shown disabled so the shape of the finished screen is legible without pretending the
 * sections work. The active item carries a live count of its selected chips.
 */

interface NavItem {
  key: string;
  label: string;
  icon: string;
}

interface NavGroup {
  group: string;
  items: NavItem[];
}

const GROUPS: NavGroup[] = [
  {
    group: "Scope filters",
    items: [
      { key: "sector", label: "Sector Scope", icon: "M12 2a10 10 0 1 0 .01 0M12 8a4 4 0 1 0 .01 0" },
      {
        key: "size",
        label: "Company Size",
        icon: "M7 7h.01M7 3h5a2 2 0 0 1 1.4.6l7 7a2 2 0 0 1 0 2.8l-5 5a2 2 0 0 1-2.8 0l-7-7A2 2 0 0 1 5 10V5a2 2 0 0 1 2-2Z",
      },
      { key: "ownership", label: "Ownership Type", icon: "M3 21h18M5 21V7l7-4 7 4v14" },
      { key: "location", label: "Location", icon: "M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 1 1 18 0Z" },
    ],
  },
  {
    group: "Lists",
    items: [
      { key: "seed", label: "Target List Seeding", icon: "M5 12h14M13 6l6 6-6 6" },
      { key: "offlimits", label: "Off-limits", icon: "M4.9 4.9l14.2 14.2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z" },
    ],
  },
];

export function StrategyNav({ activeKey, selectedCount }: { activeKey: string; selectedCount: number }) {
  return (
    <nav className="sticky top-0 rounded-[10px] border border-line-soft bg-panel2 p-2">
      {GROUPS.map((group) => (
        <div key={group.group}>
          <div className="px-[10px] pt-3 pb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
            {group.group}
          </div>
          {group.items.map((item) => {
            const active = item.key === activeKey;
            return (
              <button
                key={item.key}
                type="button"
                disabled={!active}
                aria-current={active ? "page" : undefined}
                className={`flex w-full items-center gap-[9px] rounded-lg px-[10px] py-[9px] text-left font-sans text-[13px] font-medium transition ${
                  active
                    ? "bg-panel text-text"
                    : "cursor-not-allowed text-text2 opacity-50"
                }`}
              >
                <svg
                  width="15"
                  height="15"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className={`flex-none ${active ? "text-amber" : ""}`}
                  aria-hidden="true"
                >
                  <path d={item.icon} />
                </svg>
                <span className="flex-1">{item.label}</span>
                {active && (
                  <span className="flex-none rounded-full border border-amber bg-panel px-[7px] py-px font-mono text-[10.5px] font-semibold text-amber">
                    {selectedCount}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
