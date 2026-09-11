package org.icarus.tingere.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;

@RequiredArgsConstructor
public class ShapelessRecipeModel implements CustomRecipeInterface {

    @Getter
    private final String key;
    private final List<ItemStack> ingredients;
    private final ItemStack result;

    @Override
    public Recipe toBukkitRecipe() {
        NamespacedKey namespacedKey = new NamespacedKey(Tingere.getInstance(), key);
        ShapelessRecipe recipe = new ShapelessRecipe(namespacedKey, result);
        for (ItemStack ingredient : ingredients) {
            recipe.addIngredient(new RecipeChoice.ExactChoice(ingredient));
        }
        return recipe;
    }
}
