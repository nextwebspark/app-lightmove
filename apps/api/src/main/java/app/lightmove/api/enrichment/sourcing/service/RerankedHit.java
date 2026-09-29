package app.lightmove.api.enrichment.sourcing.service;

/** One of the model's picks, by its number in the list it was shown, with a score and a reason. */
record RerankedHit(int index, int score, String reason) {}
