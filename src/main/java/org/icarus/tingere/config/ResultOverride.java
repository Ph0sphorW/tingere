package org.icarus.tingere.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 转化配方在合成期需要覆盖的结果信息。
 * <p>
 * 之所以不能在注册阶段完成，是因为 Bukkit 的 {@code TransmuteRecipe} 构造器只接受
 * {@code Material}：既无法携带结果组件，数量也恒为 1。
 *
 * @param components result.components 配置节，可为 null
 * @param amount     结果数量
 */
public record ResultOverride(ConfigurationSection components, int amount) {
}
