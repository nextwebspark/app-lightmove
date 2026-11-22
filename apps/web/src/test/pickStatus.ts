import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/** Opens the status picker whose label matches, within `scope` where a screen draws two, and picks that status. */
export async function pickStatus(picker: RegExp, statusLabel: string, scope: HTMLElement = document.body) {
  await userEvent.click(within(scope).getByRole("button", { name: picker }));
  const list = await screen.findByRole("listbox");
  await userEvent.click(await within(list).findByRole("option", { name: statusLabel }));
}
