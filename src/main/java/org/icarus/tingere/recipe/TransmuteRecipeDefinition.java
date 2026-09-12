package org.icarus.tingere.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.TransmuteRecipe;
import org.icarus.tingere.Tingere;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record TransmuteRecipeDefinition(@NotNull String key,
                                        @NotNull Ingredient input,
                                        @NotNull Ingredient material,
                                        @NotNull Ingredient result,
                                        SpecialDefinition special) implements RecipeDefinition {

    @Override
    public Recipe toBukkitRecipe(Tingere plugin) {
        return new TransmuteRecipe(
                new NamespacedKey(plugin, key),
                result.material(),
                new RecipeChoice.MaterialChoice(input.material()),
                new RecipeChoice.ExactChoice(material.toItemStack(plugin, "material")));
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return List.of(input, material);
    }
}
