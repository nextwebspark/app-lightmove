import { useTheme } from "./useTheme";

/**
 * Sun/moon switch.
 *
 * <p>Lives in the workspace header for now. The mockups put it in the sidebar, and the sidebar arrives
 * with the Project screen — putting it there now would mean building the shell that houses it, which is
 * a screen this session was not asked for.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { theme, toggle } = useTheme();
  const dark = theme === "dark";

  return (
    <button
      type="button"
      onClick={toggle}
      // The label names the *destination*, not the current state — a button that says "dark mode" while
      // in dark mode reads as a description to a screen reader, not an action.
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      title={dark ? "Switch to light mode" : "Switch to dark mode"}
      className={`grid size-7 place-items-center rounded-lg border border-line bg-panel2 text-text3 outline-none transition hover:text-text focus-visible:ring-2 focus-visible:ring-sky ${className ?? ""}`}
    >
      {dark ? (
        <svg className="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        </svg>
      ) : (
        <svg className="size-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
        </svg>
      )}
    </button>
  );
}
