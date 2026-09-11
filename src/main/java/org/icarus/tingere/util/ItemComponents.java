package org.icarus.tingere.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把配方里的 {@code components} 配置节应用到物品上。
 * <p>
 * 加载期（{@code RecipeLoader.parseItemStack}）与合成期（{@code CraftListener}）共用这一份实现，
 * 以保证同一个配置键在两处语义完全一致——此前两处各有一套代码，支持的键集不同，
 * 写在不同的配方类型上会静默失效。
 * <p>
 * 全部通过 Paper 的 DataComponent API 写入，不做 ItemMeta 往返（后者会用整份组件补丁覆盖，
 * 从而丢失已经 set 过的组件）。
 */
public final class ItemComponents {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** 1.21.2 起属性名去掉了这些前缀，读取时向后兼容 */
    private static final String[] LEGACY_ATTRIBUTE_PREFIXES = {"generic.", "player.", "zombie.", "horse."};

    private ItemComponents() {
    }

    /**
     * 就地把配置节里的组件应用到物品上。
     *
     * @param item      目标物品（会被修改）
     * @param comps     components 配置节，可为 null
     * @param keyPrefix 属性修饰符 id 的前缀。同一个物品上多次调用本方法时，
     *                  传入不同的前缀可避免修饰符 key 冲突互相覆盖。
     * @return 传入的 item，便于链式书写
     */
    public static ItemStack apply(Tingere plugin, ItemStack item, ConfigurationSection comps, String keyPrefix) {
        if (item == null || comps == null) {
            return item;
        }

        applyCustomModelData(item, comps);
        applyItemModel(plugin, item, comps);
        applyGlint(item, comps);
        applyName(item, comps);
        applyLore(item, comps);
        applyEnchantments(item, comps);
        applyAttributes(plugin, item, comps, keyPrefix);
        applyMaxDamage(item, comps);
        applyUnbreakable(item, comps);
        applyEquippable(item, comps);

        return item;
    }

    /** {@code custom-model-data: 1001} → 写入为 floats[0]（与 1.20.5+ 的原版迁移一致） */
    private static void applyCustomModelData(ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("custom-model-data")) {
            return;
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(comps.getInt("custom-model-data")).build());
    }

    /** {@code item-model: "namespace:path"} / {@code item-model: path} */
    private static void applyItemModel(Tingere plugin, ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("item-model")) {
            return;
        }
        NamespacedKey key = parseKey(plugin, comps.getString("item-model"));
        if (key != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, key);
        }
    }

    /** {@code glint: true/false} → 强制附魔光效开关（false 可让附魔物品不发光） */
    private static void applyGlint(ItemStack item, ConfigurationSection comps) {
        if (!comps.isBoolean("glint")) {
            return;
        }
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, comps.getBoolean("glint"));
    }

    /**
     * {@code display-name}，或 {@code prefix} / {@code suffix} 词缀。
     * display-name 优先级最高，存在时词缀不生效。
     */
    private static void applyName(ItemStack item, ConfigurationSection comps) {
        if (comps.contains("display-name")) {
            String name = comps.getString("display-name");
            if (name != null) {
                item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(name));
            }
            return;
        }

        String prefix = comps.getString("prefix");
        String suffix = comps.getString("suffix");
        if (prefix == null && suffix == null) {
            return;
        }

        // 基于物品当前名称拼接，因此不会覆盖原有的自定义名称
        Component name = currentName(item);
        if (prefix != null) {
            name = MINI_MESSAGE.deserialize(prefix).append(name);
        }
        if (suffix != null) {
            name = name.append(MINI_MESSAGE.deserialize(suffix));
        }
        item.setData(DataComponentTypes.CUSTOM_NAME, name);
    }

    private static Component currentName(ItemStack item) {
        Component customName = item.getData(DataComponentTypes.CUSTOM_NAME);
        if (customName != null) {
            return customName;
        }
        // 可翻译组件，会跟随客户端语言
        return Component.translatable(item.getType().getItemTranslationKey());
    }

    private static void applyLore(ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("lore")) {
            return;
        }
        List<Component> lore = new ArrayList<>();
        for (String line : comps.getStringList("lore")) {
            lore.add(MINI_MESSAGE.deserialize(line));
        }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
    }

    /**
     * {@code enchantments: ["sharpness:5", "minecraft:looting:3"]}
     * <p>
     * 与物品已有的附魔<b>合并</b>而非替换，因此对「复制输入组件」的特殊配方不会丢掉原有附魔；
     * 对全新物品来说等价于直接设置。
     */
    private static void applyEnchantments(ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("enchantments")) {
            return;
        }

        Map<Enchantment, Integer> merged = new HashMap<>(item.getEnchantments());
        for (String entry : comps.getStringList("enchantments")) {
            // 从右往左切，兼容 "sharpness:5" 与 "minecraft:sharpness:5"
            int separator = entry.lastIndexOf(':');
            if (separator <= 0) {
                continue;
            }
            NamespacedKey key = parseKey(null, entry.substring(0, separator).trim());
            if (key == null) {
                continue;
            }
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment == null) {
                continue;
            }
            try {
                merged.put(enchantment, Integer.parseInt(entry.substring(separator + 1).trim()));
            } catch (NumberFormatException ignored) {
                // 等级非法则跳过该条
            }
        }

        if (!merged.isEmpty()) {
            item.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(merged));
        }
    }

    /**
     * <pre>
     * attributes:
     *   - type: attack_damage      # 也接受旧名 generic.attack_damage
     *     amount: 3.0
     *     operation: ADD_NUMBER    # 省略则 ADD_NUMBER
     *     slot: mainhand           # 省略则 any
     * </pre>
     */
    private static void applyAttributes(Tingere plugin, ItemStack item, ConfigurationSection comps, String keyPrefix) {
        if (!comps.isList("attributes")) {
            return;
        }

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        boolean any = false;
        int index = 0;

        for (Map<?, ?> entry : comps.getMapList("attributes")) {
            index++;
            Attribute attribute = resolveAttribute(entry.get("type"));
            if (attribute == null) {
                plugin.getLogger().warning("忽略未知属性: " + entry.get("type"));
                continue;
            }
            Object amount = entry.get("amount");
            if (!(amount instanceof Number number)) {
                plugin.getLogger().warning("属性 " + entry.get("type") + " 的 amount 无效: " + amount);
                continue;
            }

            NamespacedKey modifierKey = new NamespacedKey(plugin, keyPrefix + "_attr_" + index);
            AttributeModifier modifier = new AttributeModifier(
                    modifierKey, number.doubleValue(), resolveOperation(entry.get("operation")));
            builder.addModifier(attribute, modifier, resolveSlotGroup(entry.get("slot")));
            any = true;
        }

        if (any) {
            item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        }
    }

    private static Attribute resolveAttribute(Object raw) {
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        Attribute attribute = lookupAttribute(name);
        if (attribute != null) {
            return attribute;
        }
        for (String legacy : LEGACY_ATTRIBUTE_PREFIXES) {
            if (name.startsWith(legacy)) {
                return lookupAttribute(name.substring(legacy.length()));
            }
        }
        return null;
    }

    private static Attribute lookupAttribute(String name) {
        NamespacedKey key = parseKey(null, name);
        return key == null ? null : Registry.ATTRIBUTE.get(key);
    }

    private static AttributeModifier.Operation resolveOperation(Object raw) {
        if (raw != null) {
            try {
                return AttributeModifier.Operation.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 落到默认值
            }
        }
        return AttributeModifier.Operation.ADD_NUMBER;
    }

    private static EquipmentSlotGroup resolveSlotGroup(Object raw) {
        if (raw != null) {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(String.valueOf(raw).trim());
            if (group != null) {
                return group;
            }
        }
        return EquipmentSlotGroup.ANY;
    }

    private static void applyMaxDamage(ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("maxdamage")) {
            return;
        }
        item.setData(DataComponentTypes.MAX_DAMAGE, comps.getInt("maxdamage"));
    }

    private static void applyUnbreakable(ItemStack item, ConfigurationSection comps) {
        if (comps.getBoolean("unbreakable", false)) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        }
    }

    /** {@code equippable-on-head: true} 让任意物品可戴在头上（默认关闭） */
    private static void applyEquippable(ItemStack item, ConfigurationSection comps) {
        if (comps.getBoolean("equippable-on-head", false)) {
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).build());
        }
    }

    /**
     * 解析命名空间键。含 {@code :} 时按原样解析，否则挂到插件命名空间下。
     *
     * @param plugin 为 null 时不使用插件命名空间做兜底
     */
    private static NamespacedKey parseKey(Tingere plugin, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.contains(":")) {
            return NamespacedKey.fromString(raw);
        }
        return plugin == null ? NamespacedKey.minecraft(raw) : new NamespacedKey(plugin, raw);
    }
}
