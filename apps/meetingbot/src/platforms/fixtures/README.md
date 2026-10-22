Pages the Meet and Teams screen readers are tested against (`meet-*`, `teams-*`). Hand-trimmed to what the readers look at — the labels and sentences a
person sees — and not saved from a live call yet. When the daily canary (#956) or a real call shows a service has changed,
save the page (`document.documentElement.outerHTML` from DevTools, names and links removed) over the matching file and
make the test pass again.
