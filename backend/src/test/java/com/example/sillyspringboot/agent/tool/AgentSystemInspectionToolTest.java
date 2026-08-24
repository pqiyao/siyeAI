package com.example.sillyspringboot.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSystemInspectionToolTest {

    @Test
    void parsesFlywayVersionWithAndWithoutVPrefix() {
        assertEquals(165, AgentSystemInspectionTool.parseMigrationVersion("V165"));
        assertEquals(165, AgentSystemInspectionTool.parseMigrationVersion(165));
    }

    @Test
    void returnsZeroWhenFlywayVersionHasNoNumericPart() {
        assertEquals(0, AgentSystemInspectionTool.parseMigrationVersion(null));
        assertEquals(0, AgentSystemInspectionTool.parseMigrationVersion("unknown"));
    }

    @Test
    void detectsStaleJarWhenClassesAreNewer() {
        assertFalse(AgentSystemInspectionTool.artifactFresh(true, true, 100L, 101L));
        assertTrue(AgentSystemInspectionTool.artifactFresh(true, true, 101L, 101L));
        assertFalse(AgentSystemInspectionTool.artifactFresh(true, false, 0L, 101L));
    }
}
