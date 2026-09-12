package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.parser.ComponentParser;

public record Ingredient(@JsonProperty(required = true) Material material,
                         Integer amount,
                         String matchMode,
                         JsonNode components) {

    public static final int DEFAULT_AMOUNT = 1;

    public Ingredient {
        if (material == null) {
            throw new IllegalArgumentException("missing required field 'material'");
        }
        if (material.isAir()) {
            throw new IllegalArgumentException("'material' must not be air");
        }
    }

    public int amountOrDefault() {
        return amount == null ? DEFAULT_AMOUNT : amount;
    }

    public boolean matchesExactly() {
        return !"material".equalsIgnoreCase(matchMode);
    }

    public void requireSingle(String field, String recipeType) {
        if (amount != null && amount != 1) {
            throw new IllegalArgumentException(
                    "'" + field + ".amount' must be 1 for " + recipeType + " recipes, got " + amount);
        }
    }

    public ItemStack toItemStack(Tingere plugin, String keyPrefix) {
        return ComponentParser.apply(plugin, new ItemStack(material, amountOrDefault()), components, keyPrefix);
    }

    public RecipeChoice toRecipeChoice(Tingere plugin, String keyPrefix) {
        return matchesExactly()
                ? new RecipeChoice.ExactChoice(toItemStack(plugin, keyPrefix))
                : new RecipeChoice.MaterialChoice(material);
    }
}
