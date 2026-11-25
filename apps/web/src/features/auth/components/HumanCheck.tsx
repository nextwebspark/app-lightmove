import { useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { cn } from "../../../lib/cn";
import { authProvidersQuery, useAuthProviders } from "../useAuthProviders";

/** The form a token is bound to; the server refuses one minted for another form. */
export type HumanCheckAction = "login" | "signup" | "password-reset" | "invite-signup" | "sso";

interface TurnstileRenderOptions {
  sitekey: string;
  action: HumanCheckAction;
  appearance: "always" | "execute" | "interaction-only";
  theme: "auto" | "light" | "dark";
  callback: (token: string) => void;
  "expired-callback": () => void;
  "error-callback": () => void;
  "before-interactive-callback": () => void;
  "after-interactive-callback": () => void;
}

interface TurnstileApi {
  render: (container: HTMLElement, options: TurnstileRenderOptions) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

const SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

/** How long a submit waits for a token still being minted before it goes without one. */
const TOKEN_WAIT_MS = 15_000;

let turnstileLoading: Promise<TurnstileApi> | null = null;

function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile);
  turnstileLoading ??= new Promise<TurnstileApi>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error("Turnstile missing")));
    script.onerror = () => {
      turnstileLoading = null;
      script.remove();
      reject(new Error("Turnstile did not load"));
    };
    document.head.appendChild(script);
  });
  return turnstileLoading;
}

export interface HumanCheck {
  /** Where the form draws the check. Invisible unless Cloudflare asks for a click; nothing at all when the check is off. */
  widget: ReactNode;
  /**
   * The token for one submit, sent as `X-Human-Check`; undefined when the check is off or no token came in time.
   * Tokens are single-use, so taking one starts the next.
   */
  takeToken: () => Promise<string | undefined>;
}

/** Cloudflare Turnstile for one sign-in form, loaded only where the server hands out a site key. */
export function useHumanCheck(action: HumanCheckAction): HumanCheck {
  const siteKey = useAuthProviders().data?.humanCheckSiteKey ?? null;
  const queryClient = useQueryClient();
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<string | null>(null);
  const token = useRef<string | null>(null);
  const waiters = useRef<((value: string | undefined) => void)[]>([]);
  const [interactive, setInteractive] = useState(false);

  const answerWaiters = useCallback((value: string | undefined) => {
    const pending = waiters.current;
    waiters.current = [];
    pending.forEach((resolve) => resolve(value));
  }, []);

  useEffect(() => {
    const element = container.current;
    if (!siteKey || !element) return;
    let cancelled = false;

    loadTurnstile()
      .then((turnstile) => {
        if (cancelled) return;
        widgetId.current = turnstile.render(element, {
          sitekey: siteKey,
          action,
          appearance: "interaction-only",
          theme: document.body.classList.contains("dark") ? "dark" : "light",
          callback: (value) => {
            token.current = value;
            answerWaiters(value);
          },
          "expired-callback": () => {
            token.current = null;
          },
          "error-callback": () => {
            token.current = null;
          },
          "before-interactive-callback": () => setInteractive(true),
          "after-interactive-callback": () => setInteractive(false),
        });
      })
      .catch(() => answerWaiters(undefined));

    return () => {
      cancelled = true;
      if (widgetId.current) window.turnstile?.remove(widgetId.current);
      widgetId.current = null;
      token.current = null;
    };
  }, [siteKey, action, answerWaiters]);

  const takeToken = useCallback(async () => {
    // A submit can beat the providers read; without waiting for it the form would go with no token and be refused.
    const knownSiteKey =
      siteKey ??
      (await queryClient.ensureQueryData(authProvidersQuery).catch(() => null))?.humanCheckSiteKey ??
      null;
    if (!knownSiteKey) return undefined;
    const value =
      token.current ??
      (await new Promise<string | undefined>((resolve) => {
        const waiter = (answer: string | undefined) => {
          clearTimeout(timer);
          resolve(answer);
        };
        const timer = setTimeout(() => {
          waiters.current = waiters.current.filter((pending) => pending !== waiter);
          resolve(undefined);
        }, TOKEN_WAIT_MS);
        waiters.current.push(waiter);
      }));
    token.current = null;
    if (widgetId.current) window.turnstile?.reset(widgetId.current);
    return value ?? undefined;
  }, [siteKey, queryClient]);

  const widget = siteKey ? (
    <div ref={container} data-testid="human-check" className={cn("flex justify-center", interactive && "mb-4")} />
  ) : null;

  return { widget, takeToken };
}
