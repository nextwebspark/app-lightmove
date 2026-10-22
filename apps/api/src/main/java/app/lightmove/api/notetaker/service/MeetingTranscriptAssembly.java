package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.CaptionedLine;
import app.lightmove.api.notetaker.model.HeardLine;
import app.lightmove.api.notetaker.model.TranscribedPiece;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stitches transcribed pieces into one transcript. Pieces overlap by a second, so a line is kept by the piece it
 * began in and dropped from the one before, which also heard its start. A transcriber's voice labels mean nothing
 * outside their piece, so each piece's labels are named from the captions said at the same time: the caption
 * speaker sharing the most words with a label's lines takes it, and a label no caption matches stays as it is.
 */
final class MeetingTranscriptAssembly {

    /** Captions trail speech, and a turn's caption window stretches a little either side of it. */
    private static final Duration CAPTION_SLACK = Duration.ofSeconds(5);
    private static final Pattern WORD_BREAK = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int SHORTEST_WORD = 3;

    private MeetingTranscriptAssembly() {
    }

    static List<TranscriptLine> assemble(List<TranscribedPiece> pieces, List<CaptionedLine> captions) {
        List<TranscribedPiece> ordered = pieces.stream()
                .sorted(Comparator.comparing(TranscribedPiece::startedAt))
                .toList();
        List<TranscriptLine> transcript = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            TranscribedPiece piece = ordered.get(index);
            Instant nextStart = index + 1 < ordered.size() ? ordered.get(index + 1).startedAt() : null;
            transcript.addAll(linesOf(piece, nextStart, captions));
        }
        return transcript;
    }

    private static List<TranscriptLine> linesOf(TranscribedPiece piece, Instant nextStart,
                                                List<CaptionedLine> captions) {
        List<HeardLine> kept = new ArrayList<>();
        for (HeardLine line : piece.lines()) {
            Instant at = piece.startedAt().plusMillis(line.offsetMillis());
            if (nextStart == null || at.isBefore(nextStart)) {
                kept.add(line);
            }
        }
        Map<String, String> names = namesOf(piece.startedAt(), kept, captions);
        return kept.stream()
                .map(line -> new TranscriptLine(piece.startedAt().plusMillis(line.offsetMillis()),
                        line.carriesName() ? line.speakerLabel()
                                : names.getOrDefault(line.speakerLabel(), line.speakerLabel()), line.text(),
                        line.carriesName() || names.containsKey(line.speakerLabel())))
                .toList();
    }

    private static Map<String, String> namesOf(Instant pieceStart, List<HeardLine> lines,
                                               List<CaptionedLine> captions) {
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        for (HeardLine line : lines) {
            if (line.carriesName()) {
                continue;
            }
            Instant at = pieceStart.plusMillis(line.offsetMillis());
            Set<String> heardWords = wordsOf(line.text());
            for (CaptionedLine caption : captions) {
                if (at.isBefore(caption.startedAt().minus(CAPTION_SLACK))
                        || at.isAfter(caption.updatedAt().plus(CAPTION_SLACK))) {
                    continue;
                }
                Set<String> shared = new HashSet<>(wordsOf(caption.text()));
                shared.retainAll(heardWords);
                if (!shared.isEmpty()) {
                    votes.computeIfAbsent(line.speakerLabel(), label -> new HashMap<>())
                            .merge(caption.speaker(), shared.size(), Integer::sum);
                }
            }
        }
        Map<String, String> names = new HashMap<>();
        votes.forEach((label, byName) -> byName.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .ifPresent(best -> names.put(label, best.getKey())));
        return names;
    }

    private static Set<String> wordsOf(String text) {
        Set<String> words = new HashSet<>();
        for (String word : WORD_BREAK.split(text.toLowerCase(Locale.ROOT))) {
            if (word.length() >= SHORTEST_WORD) {
                words.add(word);
            }
        }
        return words;
    }
}
