package com.example.sillyspringboot.chat.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps ensemble speaker protocol markers out of client-visible stream deltas. */
public final class AssistantProtocolStreamFilter {
    private static final String SPEAKER_PREFIX = "<|speaker:";
    private static final String NARRATOR_MARKER = "<|narrator|>";
    private static final Pattern COMPLETE_MARKER = Pattern.compile(
            "^<\\|(?:speaker:[^|>\\r\\n]+|narrator)\\|>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTIAL_SPEAKER_MARKER = Pattern.compile(
            "^<\\|speaker:[^|>\\r\\n]*(?:\\|)?$", Pattern.CASE_INSENSITIVE);

    private final boolean enabled;
    private final StringBuilder pending = new StringBuilder();

    private AssistantProtocolStreamFilter(boolean enabled) {
        this.enabled = enabled;
    }

    public static AssistantProtocolStreamFilter passthrough() {
        return new AssistantProtocolStreamFilter(false);
    }

    public static AssistantProtocolStreamFilter ensemble() {
        return new AssistantProtocolStreamFilter(true);
    }

    public String accept(String delta) {
        if (!enabled || delta == null || delta.isEmpty()) {
            return delta;
        }
        pending.append(delta);
        StringBuilder visible = new StringBuilder();
        while (!pending.isEmpty()) {
            int markerStart = pending.indexOf("<");
            if (markerStart < 0) {
                visible.append(pending);
                pending.setLength(0);
                break;
            }
            if (markerStart > 0) {
                visible.append(pending, 0, markerStart);
                pending.delete(0, markerStart);
            }

            Matcher complete = COMPLETE_MARKER.matcher(pending);
            if (complete.find()) {
                pending.delete(0, complete.end());
                continue;
            }
            if (couldBecomeProtocolMarker(pending.toString())) {
                break;
            }
            visible.append(pending.charAt(0));
            pending.deleteCharAt(0);
        }
        return visible.toString();
    }

    private static boolean couldBecomeProtocolMarker(String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT);
        return NARRATOR_MARKER.startsWith(normalized)
                || SPEAKER_PREFIX.startsWith(normalized)
                || PARTIAL_SPEAKER_MARKER.matcher(candidate).matches();
    }
}
