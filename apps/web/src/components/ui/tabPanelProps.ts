/** What the panel a `TabList` controls carries, so the two name each other. */
export function tabPanelProps(idPrefix: string, value: string) {
  return {
    role: "tabpanel",
    id: `${idPrefix}-panel-${value}`,
    "aria-labelledby": `${idPrefix}-tab-${value}`,
  } as const;
}
