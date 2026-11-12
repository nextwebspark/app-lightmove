import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ListboxSelect, matching, type ListboxOption } from "./ListboxSelect";

const SHAPES: ListboxOption<string>[] = [
  { value: "DD_MMM_YYYY", label: "04 Dec 2026", detail: "DD MMM YYYY" },
  { value: "MMM_D_YYYY", label: "Dec 4, 2026", detail: "MMM D, YYYY" },
  { value: "YYYY_MM_DD", label: "2026-12-04", detail: "YYYY-MM-DD" },
];

function Picker({ searchable = false }: { searchable?: boolean }) {
  const [value, setValue] = useState("DD_MMM_YYYY");
  return (
    <>
      <ListboxSelect aria-label="Date format" value={value} options={SHAPES} onChange={setValue} searchable={searchable} />
      <output>{value}</output>
    </>
  );
}

describe("ListboxSelect", () => {
  it("reads as the choice held and marks it in the list", async () => {
    const user = userEvent.setup();
    render(<Picker />);

    expect(screen.getByRole("button", { name: "Date format" })).toHaveTextContent("04 Dec 2026DD MMM YYYY");
    await user.click(screen.getByRole("button", { name: "Date format" }));
    expect(screen.getByRole("option", { name: "04 Dec 2026 DD MMM YYYY" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("option", { name: "Dec 4, 2026 MMM D, YYYY" })).toHaveAttribute("aria-selected", "false");
  });

  it("moves with the arrow keys and picks with Enter, then closes", async () => {
    const user = userEvent.setup();
    render(<Picker />);

    await user.click(screen.getByRole("button", { name: "Date format" }));
    await user.keyboard("{ArrowDown}{ArrowDown}{Enter}");

    expect(screen.getByRole("status")).toHaveTextContent("YYYY_MM_DD");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("narrows to what every word typed matches, and picks the first match on Enter", async () => {
    const user = userEvent.setup();
    render(<Picker searchable />);

    await user.click(screen.getByRole("button", { name: "Date format" }));
    await user.type(screen.getByRole("combobox", { name: "Search date format" }), "dec 2026{Enter}");

    expect(screen.getByRole("status")).toHaveTextContent("DD_MMM_YYYY");
  });

  it("matches accents and case aside, across label, detail and keywords", () => {
    const groups = [
      {
        options: [
          { value: "America/Sao_Paulo", label: "São Paulo", keywords: "Brazil" },
          { value: "Asia/Dubai", label: "Dubai, Abu Dhabi", keywords: "United Arab Emirates UAE" },
        ],
      },
    ];
    const valuesFor = (search: string) => matching(groups, search).flatMap((group) => group.options.map((o) => o.value));

    expect(valuesFor("sao")).toEqual(["America/Sao_Paulo"]);
    expect(valuesFor("BRAZIL")).toEqual(["America/Sao_Paulo"]);
    expect(valuesFor("abu uae")).toEqual(["Asia/Dubai"]);
    expect(valuesFor("abu brazil")).toEqual([]);
    expect(valuesFor("  ")).toEqual(["America/Sao_Paulo", "Asia/Dubai"]);
  });
});
