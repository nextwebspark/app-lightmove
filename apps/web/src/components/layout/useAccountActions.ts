import { useAuth } from "../../features/auth/AuthProvider";
import { useHelp } from "../../features/help/HelpProvider";
import { isPlatformStaff, PLATFORM_HOME } from "../../features/platform/platformSections";
import { useTheme } from "../../features/theme/useTheme";
import { ICONS } from "./Icon";

export interface AccountAction {
  key: "profile" | "security" | "theme" | "help" | "platform" | "signOut";
  label: string;
  icon: string;
  /** A screen to go to; otherwise `onSelect` does the work. */
  to?: string;
  onSelect?: () => void;
  /** Set on a toggle: whether it is on. */
  checked?: boolean;
}

/** What the person can do about their own account — one list, drawn by the avatar's menu and by a phone's More. */
export function useAccountActions(): AccountAction[] {
  const { user, signOut } = useAuth();
  const { openHelp } = useHelp();
  const { theme, toggle } = useTheme();
  return [
    { key: "profile", label: "Your profile", icon: ICONS.profile, to: "/settings/profile" },
    { key: "security", label: "Security", icon: ICONS.lock, to: "/settings/security" },
    { key: "theme", label: "Dark mode", icon: ICONS.moon, onSelect: toggle, checked: theme === "dark" },
    { key: "help", label: "Help & shortcuts", icon: ICONS.help, onSelect: () => openHelp() },
    // Uncava staff only: the console belongs to no workspace, so its way in is the person's menu, not the workspace's.
    ...(isPlatformStaff(user)
      ? [{ key: "platform", label: "Platform", icon: ICONS.shield, to: PLATFORM_HOME } satisfies AccountAction]
      : []),
    { key: "signOut", label: "Sign out", icon: ICONS.signOut, onSelect: () => void signOut() },
  ];
}
