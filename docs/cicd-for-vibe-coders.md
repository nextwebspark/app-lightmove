# CI/CD for Vibe Coders — and Why Security Is the Part You Can't Skip

You described an app to an AI. It wrote it. It runs. That is a real achievement, and a few
years ago it would have taken a team.

Then comes the awkward question: **how do you know it is safe to put in front of people?**

You can't read every line — nobody can any more, not even the developers who typed them.
So instead of reading, we check. Automatically. Every single time. That is all CI/CD is,
and this is exactly how we do it on our product.

---

## The one idea behind all of it

A pipeline is a set of **gates**. Every change has to walk through them in order. If a gate
says no, the change stops there — not in front of a customer.

Nobody remembers to run checks by hand at 11pm. A robot does. That is the whole trick.

> **[SCREENSHOT PLACEHOLDER — GitHub Actions tab showing the list of workflow runs, green ticks
> down the left]**

---

## Gate 1: Does it still work?

The moment we propose a change, three jobs start on their own: the backend, the web app, and
our browser extension. Each one runs its tests and builds itself from scratch.

The backend tests spin up a **real database**, not a pretend one. A test that passes against a
fake database only proves the code works somewhere we don't actually ship.

Plain version: *before anyone looks at the change, the machine has already checked nothing
obvious is broken.*

---

## Gate 2: The code you didn't write

Here is the part most people miss.

Your app is maybe 5% your idea and 95% **other people's code** — the free libraries your AI
pulled in to handle logins, dates, file uploads. You didn't choose them. You've never read them.
They are still your responsibility once your app is live.

And they have known holes. Publicly listed ones, with ID numbers, that anyone can look up.

So on every change we run a scanner across our full list of libraries and match it against the
public vulnerability databases. It tells us: *this library, this version, this known problem.*

> **[SCREENSHOT PLACEHOLDER — the "Dependencies" job summary showing the scan result table]**

---

## Gate 3: The box it ships in

Our app doesn't ship as loose files. It ships as a **container** — a sealed box holding the app
plus a small operating system to run it on.

That operating system has its own libraries, and its own holes, and they are invisible to the
scan in Gate 2. So we build the exact box we're about to ship and scan the whole thing.

This is the only check that sees what genuinely runs in production. It is also the slowest one,
and worth every second.

> **[SCREENSHOT PLACEHOLDER — the "Container image" job with the Trivy scan output]**

---

## Gate 4: Deciding what actually stops a release

This is where teams usually go wrong in one of two directions.

Block on everything, and you get twenty alerts a week, most of them irrelevant. People start
clicking past them. Block on nothing, and the alerts are decoration.

So we drew a line. Vulnerabilities are scored 0–10 for severity by an industry standard. **Ours
blocks at 9.0 and above — the critical ones.** Everything below that is still recorded, still
visible, still triaged. It just doesn't stop a change at 6pm on a Friday.

Two rules keep that line honest:

1. **If something must not block, we write down why.** There's a file for it. It needs a reason —
   "no fix has been published yet", "that part isn't reachable from our code" — and an **expiry
   date**, so "waiting on upstream" can't quietly become permanent.
2. **We never lower the 9.0 to make a red build go away.** Moving the line to dodge one finding
   silently un-checks every future one.

> **[SCREENSHOT PLACEHOLDER — the Security → Code scanning page listing open findings]**

---

## Gate 5: The scanner that runs when nothing changed

Most new vulnerabilities are found in code that has been sitting there untouched for months.
Your app didn't change; the world's knowledge about it did.

So the security scan also runs **every Monday morning**, on its own, whether or not anyone
touched anything. And GitHub raises the fixes for us: when a library we use gets a security
patch, a change proposing the upgrade appears by itself, and walks through the same gates as
everything else.

We deliberately turned *routine* version bumps off and left *security* bumps on. Noise down,
safety up.

---

## Then: merging is not shipping

This surprises people. When a change is approved and merged, **nothing deploys.** Merging only
means "this is ready".

Shipping is a separate, deliberate button. When someone presses it, the pipeline:

1. **refuses to continue** unless all three test gates are green;
2. **stamps a version number** — v1.4.0 — so we can always say exactly what is live;
3. builds the image and updates the database *before* the new version goes live, so a bad
   database change fails the deploy and the old version keeps serving;
4. deploys;
5. runs a **smoke test** against the live site.

That last step is small and it is our favourite. It doesn't just check the site loads. It checks
the security properties we actually care about: that our internal metrics page is **not** public,
and that the API still says "no" to a stranger with no login. If either answer is wrong, the
deploy is a failure — even though the app is technically up.

Rolling back is the same button with an older version number.

> **[SCREENSHOT PLACEHOLDER — the Deploy workflow run, smoke test step expanded]**

---

## One more thing: no passwords in the pipeline

Two habits worth stealing, whatever you build with:

- **No secret is ever written in the code.** Keys and passwords are stored in a vault and handed
  to the app at the moment it starts. Anything typed into a file gets copied, shared and
  eventually pasted into a chat window.
- **The pipeline holds no permanent key to our cloud account.** It proves who it is at deploy
  time and gets a short-lived pass. There is no long-lived credential sitting around to leak.

---

## If you take one thing from this

Vibe coding changed *who* can build software. It changed nothing about what the internet does to
software that is left unchecked.

You don't need our full setup. Start with two things, this week:

1. **Turn on automatic dependency scanning.** On GitHub it is a settings toggle and a few lines
   of config. It will find something.
2. **Make your tests run automatically on every change**, not when you remember.

Everything else — the severity line, the expiry dates on exceptions, the smoke test, the
versioned releases — is just those two habits, grown up.

The point isn't to be perfect. It's that the checking shouldn't depend on you being in the mood
to check.

---

*Building [LightMove], a multi-tenant SaaS for executive search. Happy to share the workflow
files if they'd be useful to you — ask in the comments.*
