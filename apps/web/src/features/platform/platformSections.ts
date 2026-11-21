import { ICONS } from "../../components/layout/Icon";
import type { PlatformAction, User } from "../auth/api/types";
import { TEMPLATE_LIBRARY_PATH } from "../templates/lib/labels";

export const PLATFORM_HOME = "/platform";

/**
 * The console's sections. One table drives the rail, the breadcrumb, the routes' guards and where `/platform`
 * opens, as `settingsSections` does for Settings. A section is added here with the page it draws.
 */
export interface PlatformSection {
  to: string;
  label: string;
  icon: string;
  /** The platform action its routes ask for. Presentation only: every platform endpoint re-reads the grant. */
  action: PlatformAction;
  /** Its list is a grid that takes the whole main area, as the Settings template grid does. */
  grid?: boolean;
}

export const PLATFORM_SECTIONS: readonly PlatformSection[] = [
  { to: TEMPLATE_LIBRARY_PATH, label: "Templates", icon: ICONS.position, action: "TEMPLATE_LIBRARY_MANAGE", grid: true },
];

/** Whether the person holds any platform action: what shows the Platform item and opens `/platform` at all. */
export function isPlatformStaff(user: User | null): boolean {
  return (user?.platformActions ?? []).length > 0;
}

export function visiblePlatformSections(user: User | null): PlatformSection[] {
  const held = user?.platformActions ?? [];
  return PLATFORM_SECTIONS.filter((section) => held.includes(section.action));
}

export function platformSectionOf(pathname: string): PlatformSection | undefined {
  return PLATFORM_SECTIONS.find((section) => pathname === section.to || pathname.startsWith(`${section.to}/`));
}
