package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentCodeRunnerToolTest {

    @Test
    void rejectsRootContainerUserBeforeDockerInvocation() {
        AgentCodeRunnerTool tool = new AgentCodeRunnerTool(new ObjectMapper(), "python:3.12", "0:0", false);
        assertThrows(IllegalStateException.class, () -> tool.run(Map.of(
                "inputJson", "{\"code\":\"print(1)\"}"
        )));
    }
}
