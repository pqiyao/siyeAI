package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentBrowserOpenToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsBoundedUniqueLinkIndexes() throws Exception {
        List<Integer> result = AgentBrowserOpenTool.validateFollowIndexes(
                mapper.readTree("[0,2]"), 3);
        assertEquals(List.of(0, 2), result);
    }

    @Test
    void rejectsDuplicateOrOutOfRangeLinkIndexes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> AgentBrowserOpenTool.validateFollowIndexes(mapper.readTree("[1,1]"), 3));
        assertThrows(IllegalArgumentException.class,
                () -> AgentBrowserOpenTool.validateFollowIndexes(mapper.readTree("[3]"), 3));
    }
}
