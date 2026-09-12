package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;

import java.util.List;

public sealed interface RecipeDefinition
        permits ShapedRecipeDefinition,
        ShapelessRecipeDefinition,
        TransmuteRecipeDefinition {

    String key();
    Ingredient result();
    /** 特殊配方用 */
    SpecialDefinition special();
    Recipe toBukkitRecipe(Tingere plugin);
    /** 扁平化的配方 */
    List<Ingredient> flattenedIngredients();
    default JsonNode resultComponents() {
        return result().components();
    }
}
