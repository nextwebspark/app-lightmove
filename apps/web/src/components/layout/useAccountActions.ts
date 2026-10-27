import { useAuth } from "../../features/auth/AuthProvider";
import { useHelp } from "../../features/help/HelpProvider";
import { useTheme } from "../../features/theme/useTheme";
import { ICONS } from "./Icon";

export interface AccountAction {
  key: "profile" | "security" | "theme" | "help" | "signOut";
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
  const { signOut } = useAuth();
  const { openHelp } = useHelp();
  const { theme, toggle } = useTheme();
  return [
    { key: "profile", label: "Your profile", icon: ICONS.profile, to: "/settings/profile" },
    { key: "security", label: "Security", icon: ICONS.lock, to: "/settings/security" },
    { key: "theme", label: "Dark mode", icon: ICONS.moon, onSelect: toggle, checked: theme === "dark" },
    { key: "help", label: "Help & shortcuts", icon: ICONS.help, onSelect: () => openHelp() },
    { key: "signOut", label: "Sign out", icon: ICONS.signOut, onSelect: () => void signOut() },
  ];
}
