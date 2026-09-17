import { describe, expect, it, vi } from "vitest";
import { useSubmitShortcut } from "./useSubmitShortcut";

function keyEvent(key: string, modifiers: { metaKey?: boolean; ctrlKey?: boolean } = {}) {
  return {
    key,
    metaKey: false,
    ctrlKey: false,
    ...modifiers,
    preventDefault: vi.fn(),
  } as unknown as Parameters<ReturnType<typeof useSubmitShortcut>>[0];
}

describe("useSubmitShortcut", () => {
  it("submits on Ctrl+Enter and prevents the default", () => {
    const onSubmit = vi.fn();
    const handleKeyDown = useSubmitShortcut(onSubmit);
    const event = keyEvent("Enter", { ctrlKey: true });

    handleKeyDown(event);

    expect(onSubmit).toHaveBeenCalledOnce();
    expect(event.preventDefault).toHaveBeenCalledOnce();
  });

  it("submits on Cmd+Enter", () => {
    const onSubmit = vi.fn();
    const handleKeyDown = useSubmitShortcut(onSubmit);

    handleKeyDown(keyEvent("Enter", { metaKey: true }));

    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("leaves plain Enter alone, so a multi-line textarea still gets its newline", () => {
    const onSubmit = vi.fn();
    const handleKeyDown = useSubmitShortcut(onSubmit);
    const event = keyEvent("Enter");

    handleKeyDown(event);

    expect(onSubmit).not.toHaveBeenCalled();
    expect(event.preventDefault).not.toHaveBeenCalled();
  });

  it("ignores every other key, modified or not", () => {
    const onSubmit = vi.fn();
    const handleKeyDown = useSubmitShortcut(onSubmit);

    handleKeyDown(keyEvent("a", { ctrlKey: true }));

    expect(onSubmit).not.toHaveBeenCalled();
  });
});
