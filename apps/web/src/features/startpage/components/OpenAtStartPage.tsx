import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";
import { startPagePath } from "../lib/startPage";

/**
 * Home's route, which opens the member's start page instead when Uncava itself was just opened at its bare root: the
 * tab's first entry ("default" key), with nothing asked of it. Home reached from inside the app stays Home.
 */
export function OpenAtStartPage({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  const openedHere = location.key === "default" && location.search === "" && location.hash === "";
  const target = openedHere ? startPagePath(user?.startPage) : "/";
  if (target !== "/") return <Navigate to={target} replace />;
  return <>{children}</>;
}
