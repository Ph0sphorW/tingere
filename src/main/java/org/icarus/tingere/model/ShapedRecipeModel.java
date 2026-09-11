package org.icarus.tingere.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ShapedRecipeModel implements CustomRecipeInterface {

    public record IngredientInfo(ItemStack itemStack, boolean matchesExactly) {
    }

    @Getter
    private final String key;
    private final List<String> pattern;
    private final Map<Character, IngredientInfo> ingredients;
    private final ItemStack result;

    @Override
    public Recipe toBukkitRecipe() {
        NamespacedKey namespacedKey = new NamespacedKey(Tingere.getInstance(), key);
        ShapedRecipe recipe = new ShapedRecipe(namespacedKey, result);
        recipe.shape(pattern.toArray(new String[0]));

        for (Map.Entry<Character, IngredientInfo> entry : ingredients.entrySet()) {
            char symbol = entry.getKey();
            IngredientInfo info = entry.getValue();
            RecipeChoice choice;
            if (info.matchesExactly) {
                choice = new RecipeChoice.ExactChoice(info.itemStack);
            } else {
                choice = new RecipeChoice.MaterialChoice(info.itemStack.getType());
            }
            recipe.setIngredient(symbol, choice);
        }

        return recipe;
    }
}
