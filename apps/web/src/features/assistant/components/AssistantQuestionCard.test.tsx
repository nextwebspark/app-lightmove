import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { AssistantQuestion } from "../api/types";
import { AssistantQuestionCard } from "./AssistantQuestionCard";

const QUESTIONS: AssistantQuestion[] = [
  {
    question: "Which markets should the companies operate in?",
    header: "Region",
    multiSelect: false,
    options: [
      { label: "GCC only (Recommended)", description: "The six Gulf states" },
      { label: "MENA", description: "Adds Egypt, Jordan and Morocco" },
    ],
  },
  {
    question: "Which ownership types?",
    header: "Ownership",
    multiSelect: true,
    options: [
      { label: "Listed", description: "On a public exchange" },
      { label: "Family-owned", description: "Private family groups" },
    ],
  },
];

function mount(answerable = true) {
  const onAnswer = vi.fn();
  render(<AssistantQuestionCard questions={QUESTIONS} answerable={answerable} onAnswer={onAnswer} />);
  return { onAnswer };
}

describe("the assistant's question card", () => {
  it("sends nothing until every question has an answer", async () => {
    const { onAnswer } = mount();

    await userEvent.click(screen.getByRole("radio", { name: /GCC only/ }));

    expect(screen.getByRole("button", { name: "Send answers" })).toBeDisabled();
    expect(onAnswer).not.toHaveBeenCalled();
  });

  it("sends one choice per single-select question and every choice of a multi-select one", async () => {
    const { onAnswer } = mount();

    await userEvent.click(screen.getByRole("radio", { name: /GCC only/ }));
    await userEvent.click(screen.getByRole("radio", { name: /MENA/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Listed/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Family-owned/ }));
    await userEvent.click(screen.getByRole("button", { name: "Send answers" }));

    expect(onAnswer).toHaveBeenCalledWith("Region: MENA · Ownership: Listed, Family-owned");
  });

  it("leaves the recommendation mark out of the answer it sends", async () => {
    const { onAnswer } = mount();

    await userEvent.click(screen.getByRole("radio", { name: /GCC only/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Listed/ }));
    await userEvent.click(screen.getByRole("button", { name: "Send answers" }));

    expect(onAnswer).toHaveBeenCalledWith("Region: GCC only · Ownership: Listed");
  });

  it("takes typed text as the answer, in place of a single choice and beside multiple ones", async () => {
    const { onAnswer } = mount();

    await userEvent.click(screen.getByRole("radio", { name: /GCC only/ }));
    await userEvent.type(screen.getByLabelText(/Another answer to: Which markets/), "Saudi and Qatar");
    await userEvent.click(screen.getByRole("checkbox", { name: /Listed/ }));
    await userEvent.type(screen.getByLabelText(/Another answer to: Which ownership/), "Sovereign");
    await userEvent.click(screen.getByRole("button", { name: "Send answers" }));

    expect(onAnswer).toHaveBeenCalledWith("Region: Saudi and Qatar · Ownership: Listed, Sovereign");
  });

  it("moves through a single-select question with the arrow keys, as a radio group does", async () => {
    mount();
    const gcc = screen.getByRole("radio", { name: /GCC only/ });
    const mena = screen.getByRole("radio", { name: /MENA/ });

    expect(screen.getByRole("radiogroup", { name: "Which markets should the companies operate in?" })).toBeInTheDocument();
    expect(gcc).toHaveAttribute("tabindex", "0");
    expect(mena).toHaveAttribute("tabindex", "-1");

    gcc.focus();
    await userEvent.keyboard("{ArrowDown}");

    expect(mena).toHaveFocus();
    expect(mena).toHaveAttribute("aria-checked", "true");
    expect(gcc).toHaveAttribute("aria-checked", "false");
  });

  it("keeps a repeated question's answers apart", async () => {
    const onAnswer = vi.fn();
    render(
      <AssistantQuestionCard questions={[QUESTIONS[0], QUESTIONS[0]]} answerable onAnswer={onAnswer} />,
    );

    const [firstMena, secondMena] = screen.getAllByRole("radio", { name: /MENA/ });
    await userEvent.click(firstMena);
    await userEvent.click(screen.getAllByRole("radio", { name: /GCC only/ })[1]);
    await userEvent.click(screen.getByRole("button", { name: "Send answers" }));

    expect(secondMena).toHaveAttribute("aria-checked", "false");
    expect(onAnswer).toHaveBeenCalledWith("Region: MENA · Region: GCC only");
  });

  it("only reads once a later turn has answered it", () => {
    mount(false);

    expect(screen.getByRole("radio", { name: /GCC only/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Send answers" })).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Other…")).not.toBeInTheDocument();
    expect(screen.getByText("Answered below")).toBeInTheDocument();
  });
});
