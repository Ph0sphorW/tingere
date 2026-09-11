package org.icarus.tingere.config;

import lombok.Data;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

@Data
public class SpecialRecipeInfo {
    private Material targetMaterial;
    private boolean copyInput;
    private ConfigurationSection components;
    private Character sourceCharacter; // For ordered recipes
    private Integer sourceSlot;        // Any recipes
    private int amount = 1;            // Results
}
