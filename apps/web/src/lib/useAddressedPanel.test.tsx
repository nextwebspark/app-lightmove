import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { useAddressedPanel } from "./useAddressedPanel";

function renderPanel(start = "/clients?page=2") {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={["/positions", start]} initialIndex={1}>
      {children}
    </MemoryRouter>
  );
  return renderHook(
    () => ({
      panel: useAddressedPanel("client", ["tab"]),
      location: useLocation(),
      navigate: useNavigate(),
      setSearchParams: useSearchParams()[1],
    }),
    { wrapper },
  );
}

describe("useAddressedPanel", () => {
  it("opens as a step in history, so Back closes it and lands on the list as left", () => {
    const { result } = renderPanel();
    act(() => result.current.panel.open("acme"));
    expect(result.current.panel.value).toBe("acme");

    act(() => result.current.navigate(-1));
    expect(result.current.location.pathname).toBe("/clients");
    expect(result.current.location.search).toBe("?page=2");
  });

  it("moves along in place, so one Back leaves however many were looked at", () => {
    const { result } = renderPanel();
    act(() => result.current.panel.open("acme"));
    act(() => result.current.panel.step("globex"));
    act(() => result.current.panel.open("initech"));
    expect(result.current.panel.value).toBe("initech");

    act(() => result.current.navigate(-1));
    expect(result.current.location.search).toBe("?page=2");
  });

  it("closes by popping its step, taking the params that live with it", () => {
    const { result } = renderPanel();
    act(() => result.current.panel.open("acme"));
    act(() => result.current.setSearchParams((current) => {
      current.set("tab", "notes");
      return current;
    }, { replace: true, state: result.current.location.state }));
    act(() => result.current.panel.close());
    expect(result.current.location.search).toBe("?page=2");

    act(() => result.current.navigate(-1));
    expect(result.current.location.pathname).toBe("/positions");
  });

  it("clears in place when the list changed under it, rather than undo that change", () => {
    const { result } = renderPanel();
    act(() => result.current.panel.open("acme"));
    act(() => result.current.setSearchParams((current) => {
      current.set("page", "3");
      return current;
    }, { replace: true }));
    act(() => result.current.panel.close());
    expect(result.current.panel.value).toBeNull();
    expect(result.current.location.search).toBe("?page=3");
  });
});
