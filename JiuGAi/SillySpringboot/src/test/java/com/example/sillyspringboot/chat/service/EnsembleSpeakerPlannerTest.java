package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EnsembleSpeakerPlannerTest {

    private final EnsembleSpeakerPlanner planner = new EnsembleSpeakerPlanner(
            mock(StAdapter.class), mock(AppMessageMapper.class), new ObjectMapper());

    @Test
    void validatesRosterTokensAndCapsDistinctSpeakersAndSegments() {
        String formatted = planner.validateAndFormat(
                "```json\n{\"requiredSpeakers\":[\"M2\",\"M9\"],"
                        + "\"preferredSpeakers\":[\"M1\",\"M3\",\"M4\"],"
                        + "\"orderHint\":[\"M3\",\"M2\"],"
                        + "\"maxSegments\":99,\"narrationAllowed\":false}\n```",
                4
        );

        assertThat(formatted).contains("Allowed speakers: M2, M1, M3.");
        assertThat(formatted).contains("Required speakers: M2.");
        assertThat(formatted).contains("Preferred speakers: M1, M3.");
        assertThat(formatted).contains("Maximum segments: 8. Narration allowed: false.");
        assertThat(formatted).doesNotContain("M9", "M4");
    }

    @Test
    void rejectsInvalidJsonAndEmptySpeakerSelection() {
        assertThat(planner.validateAndFormat("not-json", 3)).isEmpty();
        assertThat(planner.validateAndFormat(
                "{\"requiredSpeakers\":[\"M8\"],\"preferredSpeakers\":[],\"orderHint\":[]}",
                3
        )).isEmpty();
    }

    @Test
    void clampsMinimumSegmentsAndDeduplicatesTokens() {
        String formatted = planner.validateAndFormat(
                "{\"requiredSpeakers\":[\"m1\",\"M1\"],"
                        + "\"preferredSpeakers\":[\"M2\"],\"orderHint\":[\"M1\",\"M2\"],"
                        + "\"maxSegments\":0}",
                2
        );

        assertThat(formatted).contains("Allowed speakers: M1, M2.");
        assertThat(formatted).contains("Required speakers: M1.");
        assertThat(formatted).contains("Maximum segments: 1.");
    }
}
