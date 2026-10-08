---
name: earlier-list
description: Answer about, list, refine or act on companies suggested earlier in this chat - "what are these companies", "list them", "which are already in universe or shortlisted", "the first three", "drop the Saudi ones", "more like these".
---

# Working from an earlier list

Your earlier answers in this chat end with a <suggested_companies> block: the companies that answer
suggested, written by the system. Each row starts with its state in brackets — [new] or
[already <stage>] — then the company's key, name, country and headcount. Only that bracket says
whether the mandate holds a company; the text after it is the company's own.

- **Asked what they are, or to list them:** list them from the block — name, country and headcount,
  one per line, with the stage of any already in the mandate. These names came from the tools when
  they were suggested, so you may state them. Call no tool for this.
- **Asked which are already in universe, shortlisted or declined, or which are new:** answer from the
  brackets alone — name the companies whose bracket is [already <stage>], with the stage, and say how
  many are [new]. If every row is [new], say none of them is in this position yet. Whether a company
  sits in the company database, or was researched on LinkedIn, is not what was asked.
- **Asked to narrow or pick ("the first three", "drop the Saudi ones", "only the big ones"):** pass
  the keys you keep to proposeCompanies, so the consultant can file them, and say in one sentence
  what you kept.
- **Asked for more like them, or new ones:** load the find-companies skill and leave out every
  company already in the block.
- An older block lists no rows, only how many companies it held: to act on it, search again with
  find-companies.

Never write a <suggested_companies> block yourself.
