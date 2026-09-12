package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;

public record SpecialDefinition(@JsonProperty(required = true) Material targetMaterial,
                                Boolean copyInput,
                                String sourceCharacter,
                                Integer sourceSlot,
                                JsonNode components) {

    public SpecialDefinition {
        if (targetMaterial == null) {
            throw new IllegalArgumentException("missing required field 'target-material'");
        }
        if (targetMaterial.isAir()) {
            throw new IllegalArgumentException("'special.target-material' must not be air");
        }
        if (sourceCharacter != null && sourceCharacter.length() != 1) {
            throw new IllegalArgumentException(
                    "'special.source-character' must be exactly one character, got '" + sourceCharacter + "'");
        }
        if (sourceSlot != null && (sourceSlot < 0 || sourceSlot > 8)) {
            throw new IllegalArgumentException("'special.source-slot' must be between 0 and 8, got " + sourceSlot);
        }
    }

    public boolean copyInputOrDefault() {
        return copyInput == null || copyInput;
    }

    public Character sourceCharacterOrNull() {
        return sourceCharacter == null ? null : sourceCharacter.charAt(0);
    }
}
