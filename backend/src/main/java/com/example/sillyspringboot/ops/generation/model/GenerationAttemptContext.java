package com.example.sillyspringboot.ops.generation.model;

public class GenerationAttemptContext {

    private Long generationTaskId;
    private Long characterId;

    public Long getGenerationTaskId() { return generationTaskId; }
    public void setGenerationTaskId(Long generationTaskId) { this.generationTaskId = generationTaskId; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
}
