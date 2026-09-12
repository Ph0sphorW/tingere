package org.icarus.tingere.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.parser.ComponentParser;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.ResultOverride;
import org.icarus.tingere.recipe.SpecialDefinition;

import java.util.logging.Logger;

/**
 * 在修改这个类之前，你需要知道：
 * <p>
 * 由于沟槽的 Bukkit 限制，不注入 NMS 是绝对无法做到主动定义 transmute 的结果物品的附加标签以及个数的
 * 只能是一个雷霆 Material
 * 因此选择更加不需要脑子的想法接管事件
 * 你不会真的有受虐癖到想去反射 NMS 吧？
 */
@RequiredArgsConstructor
public class CraftListener implements Listener {

    private final Tingere plugin;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }

        RecipeLoader loader = plugin.getRecipeLoader();
        String fullKey = keyed.getKey().toString();
        ResultOverride override = loader.getResultOverride(fullKey);

        // Special first
        SpecialDefinition specialInfo = loader.getSpecialRecipeInfo(fullKey);
        if (specialInfo != null) {
            handleSpecialCraft(event, specialInfo, override, recipe);
            return;
        }

        if (override == null) {
            return;
        }

        ItemStack originalResult = event.getInventory().getResult();
        if (isEmpty(originalResult)) {
            return;
        }

        ItemStack modified = originalResult.clone();
        if (override.amount() > 0) {
            modified.setAmount(override.amount());
        }
        ComponentParser.apply(plugin, modified, override.components(), "result");
        event.getInventory().setResult(modified);
    }

    private void handleSpecialCraft(PrepareItemCraftEvent event,
                                    SpecialDefinition info,
                                    ResultOverride override,
                                    Recipe recipe) {
        ItemStack sourceInput = findSourceItem(event.getInventory().getMatrix(), recipe, info);
        if (sourceInput == null) {
            return;
        }

        ItemStack finalItem;
        if (info.copyInputOrDefault()) {
            finalItem = sourceInput.withType(info.targetMaterial());
        } else {
            finalItem = new ItemStack(info.targetMaterial());
        }
        finalItem.setAmount(override == null ? Ingredient.DEFAULT_AMOUNT : override.amount());

        ComponentParser.apply(plugin, finalItem, override == null ? null : override.components(), "result");
        ComponentParser.apply(plugin, finalItem, info.components(), "special");

        event.getInventory().setResult(finalItem);
    }

    private ItemStack findSourceItem(ItemStack[] matrix, Recipe recipe, SpecialDefinition info) {
        Character sourceCharacter = info.sourceCharacterOrNull();
        return findByShape(matrix, recipe, sourceCharacter);
    }

    private ItemStack findByShape(ItemStack[] matrix, Recipe recipe, char symbol) {
        Logger logger = plugin.getLogger();
        if (!(recipe instanceof ShapedRecipe shaped)) {
            logger.warning("Key 'source-character' can be only used in ordered recipe type. Current type is: " + recipe.getClass().getSimpleName());
            return null;
        }

        // 不会有无尽工作台吧。
        String[] pattern = shaped.getShape();
        for (int row = 0; row < Math.min(pattern.length, 3); row++) {
            String line = pattern[row];
            for (int col = 0; col < Math.min(line.length(), 3); col++) {
                if (line.charAt(col) != symbol) {
                    continue;
                }
                int slot = row * 3 + col;
                return slot < matrix.length && !isEmpty(matrix[slot]) ? matrix[slot].clone() : null;
            }
        }

        logger.warning("Couldn't find target charactor '" + symbol + "' in recipe");
        return null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
