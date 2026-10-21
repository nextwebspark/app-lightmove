/** What the Profile pickers offer. Data, not markup, so the form stays a form. */

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectGroup {
  label: string;
  options: SelectOption[];
}

/**
 * The zones a person may keep: a region's own or UTC, never a legacy alias like `EST` — the server's rule, since
 * their sending hours, scheduled emails and daily digest are all read in it.
 */
export const REGION_ZONE = /^(UTC|(Africa|America|Antarctica|Arctic|Asia|Atlantic|Australia|Europe|Indian|Pacific)\/.+)$/;

/** Every zone the browser knows by region, each read as its city and today's offset — "Dubai (GMT+4)". */
export function timezoneGroups(current: string, now: Date = new Date()): SelectGroup[] {
  const zones = Intl.supportedValuesOf("timeZone").filter((zone) => zone !== "UTC" && REGION_ZONE.test(zone));
  if (current !== "UTC" && !zones.includes(current)) zones.push(current);
  const byRegion = new Map<string, SelectOption[]>();
  for (const zone of zones) {
    const region = zone.split("/")[0];
    const options = byRegion.get(region) ?? [];
    options.push({ value: zone, label: `${cityOf(zone)} (${offsetOf(zone, now)})` });
    byRegion.set(region, options);
  }
  return [
    { label: "Universal", options: [{ value: "UTC", label: "UTC" }] },
    ...[...byRegion.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([label, options]) => ({ label, options: options.sort((left, right) => left.label.localeCompare(right.label)) })),
  ];
}

/** The zone this computer's clock is set to, where it is one a person may keep. */
export function browserTimeZone(): string | null {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return zone && REGION_ZONE.test(zone) ? zone : null;
}

export function cityOf(zone: string): string {
  return (zone.split("/").pop() ?? zone).replace(/_/g, " ");
}

function offsetOf(zone: string, now: Date): string {
  try {
    const parts = new Intl.DateTimeFormat("en-US", { timeZone: zone, timeZoneName: "shortOffset" }).formatToParts(now);
    return parts.find((part) => part.type === "timeZoneName")?.value ?? zone;
  } catch {
    return zone;
  }
}

/**
 * Stored, not yet applied — nothing in the product is translated. Kept because the choice is the
 * user's to record, and the server refuses any tag outside this set.
 */
export const LANGUAGE_OPTIONS: SelectOption[] = [
  { value: "en", label: "English" },
  { value: "ar", label: "العربية" },
  { value: "fr", label: "Français" },
];
