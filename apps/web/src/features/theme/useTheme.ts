import { useCallback, useEffect, useState } from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "lm-theme";

/**
 * Light or dark, remembered.
 *
 * <p>The dark palette has existed since the first commit — `tokens.css` overrides every token under
 * `.dark`, lifted from the mockups. Nothing ever added the class, so all of it was unreachable.
 *
 * <p>The class goes on `<body>`, not on `<html>`, because that is where the mockups put it and the
 * tokens are written to match (`@custom-variant dark (&:where(.dark, .dark *))`).
 *
 * <p>An unset preference follows the operating system rather than assuming light: someone whose machine
 * is in dark mode at midnight did not ask to be flashbanged.
 */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const [theme, setTheme] = useState<Theme>(preferred);

  useEffect(() => {
    document.body.classList.toggle("dark", theme === "dark");
    localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  const toggle = useCallback(() => {
    setTheme((current) => (current === "dark" ? "light" : "dark"));
  }, []);

  return { theme, toggle };
}

/**
 * Puts the remembered theme on the body before React renders.
 *
 * <p>Called from `main.tsx` rather than from a hook, because the login and signup screens carry no
 * toggle — the mockups keep it in the app shell — and a preference that only applied on the screens
 * that can change it would leave someone in dark mode signing in through a white flash.
 */
export function applyStoredTheme(): void {
  document.body.classList.toggle("dark", preferred() === "dark");
}

function preferred(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "dark" || stored === "light") {
    return stored;
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}
