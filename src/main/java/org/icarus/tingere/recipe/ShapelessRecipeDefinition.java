package org.icarus.tingere.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.icarus.tingere.Tingere;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ShapelessRecipeDefinition(@NotNull String key,
                                        @NotNull List<Ingredient> ingredients,
                                        @NotNull Ingredient result,
                                        SpecialDefinition special) implements RecipeDefinition {

    public ShapelessRecipeDefinition {
        if (key.isBlank()) {
            throw new IllegalArgumentException("missing required field 'key'");
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("'ingredients' must not be empty");
        }
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin) {
        ShapelessRecipe recipe = new ShapelessRecipe(
                new NamespacedKey(plugin, key), result.toItemStack(plugin, "result"));

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            recipe.addIngredient(ingredient.matchesExactly()
                    ? new RecipeChoice.ExactChoice(ingredient.toItemStack(plugin, "ingredient_" + i))
                    : new RecipeChoice.MaterialChoice(ingredient.material()));
        }
        return recipe;
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return ingredients;
    }
}
