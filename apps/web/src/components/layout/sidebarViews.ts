import type { SidebarItem } from "./Sidebar";

/** Whether one of the section's views is the one on screen, so the view, not the section, is marked as current. */
export function hasActiveView(item: SidebarItem) {
  return item.children?.some((view) => view.active) ?? false;
}
