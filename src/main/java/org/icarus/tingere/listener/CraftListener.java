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
import org.icarus.tingere.config.ResultOverride;
import org.icarus.tingere.config.SpecialRecipeInfo;
import org.icarus.tingere.util.ItemComponents;

/**
 * 在合成瞬间修正结果物品。
 * <p>
 * 该类并非多余：Bukkit 的 {@code TransmuteRecipe} 构造器只接受 {@code Material}，
 * 既无法携带结果组件、数量也恒为 1，所以「转化配方带组件 / 数量」与「{@code special}
 * 同 id 转化」这两件事无法在注册阶段完成，只能在 {@link PrepareItemCraftEvent} 里补。
 * <p>
 * 只对 {@code specialRecipes} / {@code resultOverrides} 里登记过的配方生效；两张表都以完整的
 * NamespacedKey（含插件命名空间）为键，并在每次 reload 时清空，因此不会误接管其他插件或原版配方。
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

        // 特殊配方优先，并合并两处组件配置（result.components 先，special.components 后覆盖）
        SpecialRecipeInfo specialInfo = loader.getSpecialRecipeInfo(fullKey);
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
        ItemComponents.apply(plugin, modified, override.components(), "result");
        event.getInventory().setResult(modified);
    }

    private void handleSpecialCraft(PrepareItemCraftEvent event, SpecialRecipeInfo info,
                                    ResultOverride override, Recipe recipe) {
        ItemStack sourceInput = findSourceItem(event.getInventory().getMatrix(), recipe, info);
        if (sourceInput == null) {
            // 合成过程中的常态（材料尚未放齐），不作为错误刷屏
            return;
        }

        ItemStack finalItem;
        if (info.isCopyInput()) {
            finalItem = sourceInput.clone();
            finalItem.setType(info.getTargetMaterial());
        } else {
            finalItem = new ItemStack(info.getTargetMaterial());
        }
        finalItem.setAmount(info.getAmount());

        // result.components 先应用，special.components 再应用，后者覆盖同名键
        ItemComponents.apply(plugin, finalItem, override == null ? null : override.components(), "result");
        ItemComponents.apply(plugin, finalItem, info.getComponents(), "special");

        event.getInventory().setResult(finalItem);
    }

    /**
     * 确定「被转化的主物品」。
     * 未显式指定 {@code source-character} / {@code source-slot} 时，回退到第一个非空槽位。
     */
    private ItemStack findSourceItem(ItemStack[] matrix, Recipe recipe, SpecialRecipeInfo info) {
        if (info.getSourceCharacter() != null) {
            return findByShape(matrix, recipe, info.getSourceCharacter());
        }

        if (info.getSourceSlot() != null) {
            int slot = info.getSourceSlot();
            return slot < matrix.length && !isEmpty(matrix[slot]) ? matrix[slot].clone() : null;
        }

        // 转化配方：优先取与 input 声明匹配的那一个
        if (recipe instanceof TransmuteRecipe transmute) {
            RecipeChoice input = transmute.getInput();
            for (ItemStack item : matrix) {
                if (!isEmpty(item) && new RecipeChoice.MaterialChoice(item.getType()).equals(input)) {
                    return item.clone();
                }
            }
        }

        for (ItemStack item : matrix) {
            if (!isEmpty(item)) {
                return item.clone();
            }
        }
        return null;
    }

    private ItemStack findByShape(ItemStack[] matrix, Recipe recipe, char symbol) {
        if (!(recipe instanceof ShapedRecipe shaped)) {
            plugin.getLogger().warning("source-character 只能用于有序配方，当前配方类型: " + recipe.getClass().getSimpleName());
            return null;
        }

        // 工作台固定 3x3，pattern 每行左侧对齐，不足的位置视为空格
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

        plugin.getLogger().warning("未在 pattern 中找到字符 '" + symbol + "'");
        return null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
