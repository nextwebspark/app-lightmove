/** Three of the nine groups are not a nationality, so "a Western expat national" is a thing nobody is. */
const EXPAT_GROUPS = new Set(["Western expat", "South Asian", "Arab expat, non-GCC"]);

const noun = (group: string) => (EXPAT_GROUPS.has(group) ? "executive" : "national");

/** "Saudi nationals", "Western expat executives". */
export function membersOf(group: string): string {
  return `${group} ${noun(group)}s`;
}

/** "a Saudi national", "an Emirati national", "an Arab expat, non-GCC executive". */
export function memberOf(group: string): string {
  return `${/^[aeiou]/i.test(group) ? "an" : "a"} ${group} ${noun(group)}`;
}
