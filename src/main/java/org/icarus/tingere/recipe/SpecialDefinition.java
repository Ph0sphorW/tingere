package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public record SpecialDefinition(@NotNull Material targetMaterial,
                                Boolean copyInput,
                                @NotNull String sourceCharacter,
                                @NotNull Integer sourceSlot,
                                JsonNode components) {

    public SpecialDefinition {
        if (targetMaterial.isAir()) {
            throw new IllegalArgumentException("'special.target-material' must not be air");
        }
        if (sourceCharacter.length() != 1) {
            throw new IllegalArgumentException(
                    "'special.source-character' must be exactly one character, got '" + sourceCharacter + "'");
        }
        if (sourceSlot < 0 || sourceSlot > 8) {
            throw new IllegalArgumentException("'special.source-slot' must be between 0 and 8, got " + sourceSlot);
        }
    }

    public boolean copyInputOrDefault() {
        return copyInput == null || copyInput;
    }

    public Character sourceCharacterOrNull() {
        return sourceCharacter.charAt(0);
    }
}
