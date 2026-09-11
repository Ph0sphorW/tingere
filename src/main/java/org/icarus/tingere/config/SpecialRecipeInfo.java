package org.icarus.tingere.config;

import lombok.Data;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 配方加载器：扫描 recipes 目录下的所有 yml 文件并注册配方
 */
@Data
public class SpecialRecipeInfo {
    private Material targetMaterial;
    private boolean copyInput;
    private ConfigurationSection components;
    private Character sourceCharacter; // 用于有序配方（pattern 中的字符）
    private Integer sourceSlot;        // 用于任意配方（工作台槽位索引 0-8）
    private int amount = 1;            // 结果数量（原版 TransmuteRecipe 恒为 1，故在此单独保存）
}
