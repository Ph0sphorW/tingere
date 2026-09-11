package org.icarus.tingere.model;

import org.bukkit.inventory.Recipe;

/**
 * 自定义配方模型接口
 */
public interface CustomRecipe {
    /**
     * 转化为 Bukkit/Paper 的 Recipe 对象
     * @return Recipe
     * @throws IllegalArgumentException 如果转换失败
     */
    Recipe toBukkitRecipe() throws IllegalArgumentException;

    /**
     * 获取配方的 NamespacedKey（唯一标识）
     */
    String getKey();
}
