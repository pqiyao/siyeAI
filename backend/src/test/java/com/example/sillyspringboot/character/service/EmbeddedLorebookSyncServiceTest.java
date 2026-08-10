package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmbeddedLorebookSyncServiceTest {

    @Test
    void importedEntriesPopulateRequiredFieldsAndKeepSecondaryKeysSeparate() {
        AppLorebookEntryMapper mapper = mock(AppLorebookEntryMapper.class);
        EmbeddedLorebookSyncService service = new EmbeddedLorebookSyncService(mapper, new ObjectMapper());

        int imported = service.replaceEmbeddedLorebook(7L, """
                {"entries":[{
                  "keys":["alpha","beta"],
                  "secondary_keys":["gamma"],
                  "content":"lore text",
                  "position":1
                }]}
                """);

        assertEquals(1, imported);
        ArgumentCaptor<AppLorebookEntry> captor = ArgumentCaptor.forClass(AppLorebookEntry.class);
        verify(mapper).insert(captor.capture());
        AppLorebookEntry row = captor.getValue();
        assertEquals("alpha,beta", row.getKeywordsCsv());
        assertEquals("gamma", row.getSecondaryKeywordsCsv());
        assertEquals("ANY", row.getMatchMode());
        assertEquals("AFTER_CHARACTER", row.getInjectionPosition());
    }

    @Test
    void malformedJsonDoesNotDeletePreviouslyImportedEntries() {
        AppLorebookEntryMapper mapper = mock(AppLorebookEntryMapper.class);
        EmbeddedLorebookSyncService service = new EmbeddedLorebookSyncService(mapper, new ObjectMapper());

        assertEquals(0, service.replaceEmbeddedLorebook(7L, "{broken"));

        verify(mapper, never()).deleteImportedByCharacterId(7L);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stAtDepthPositionMapsToBeforeHistoryFromTopLevelAndExtensions() {
        AppLorebookEntryMapper mapper = mock(AppLorebookEntryMapper.class);
        EmbeddedLorebookSyncService service = new EmbeddedLorebookSyncService(mapper, new ObjectMapper());

        assertEquals(2, service.replaceEmbeddedLorebook(7L, """
                {"entries":[
                  {"keys":["top"],"content":"top-level","position":4},
                  {"keys":["nested"],"content":"nested","extensions":{"position":4}}
                ]}
                """));

        ArgumentCaptor<AppLorebookEntry> captor = ArgumentCaptor.forClass(AppLorebookEntry.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(
                java.util.List.of("BEFORE_HISTORY", "BEFORE_HISTORY"),
                captor.getAllValues().stream().map(AppLorebookEntry::getInjectionPosition).toList()
        );
    }
}
