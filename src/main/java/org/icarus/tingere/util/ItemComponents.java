package org.icarus.tingere.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Keyed;
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

import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
public final class ItemComponents {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String[] LEGACY_ATTRIBUTE_PREFIXES = {"generic.",
            "player.",
            "zombie.",
            "horse."};

    private ItemComponents() {
    }

    public static ItemStack apply(Tingere plugin, ItemStack item, ConfigurationSection comps, String keyPrefix) {
        if (item == null || comps == null) {
            return item;
        }

        applyCustomModelData(item, comps);
        applyItemModel(plugin, item, comps);
        applyGlint(item, comps);
        applyName(item, comps);
        applyLore(item, comps);
        applyEnchantments(plugin, item, comps);
        applyAttributes(plugin, item, comps, keyPrefix);
        applyMaxDamage(item, comps);
        applyUnbreakable(item, comps);
        applyEquippable(item, comps);

        return item;
    }

    private static void applyCustomModelData(ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("custom-model-data")) {
            return;
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData()
                        .addFloat(comps.getInt("custom-model-data"))
                        .build());
    }

    private static void applyItemModel(Tingere plugin, ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("item-model")) {
            return;
        }
        NamespacedKey key = parseKey(plugin, comps.getString("item-model"));
        if (key != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, key);
        }
    }

    private static void applyGlint(ItemStack item, ConfigurationSection comps) {
        if (!comps.isBoolean("glint")) {
            return;
        }
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, comps.getBoolean("glint"));
    }

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
        String translatable = item.getType().getItemTranslationKey();
        return Component.translatable(translatable == null ? "" : translatable);
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

    private static void applyEnchantments(Tingere plugin, ItemStack item, ConfigurationSection comps) {
        if (!comps.contains("enchantments")) {
            return;
        }

        Logger logger = plugin.getLogger();
        Map<Enchantment, Integer> merged = new HashMap<>(item.getEnchantments());
        for (String entry : comps.getStringList("enchantments")) {
            int separator = entry.lastIndexOf(':');
            if (separator <= 0) {
                logger.warning("Ignored malformed enchantment entry: " + entry);
                continue;
            }
            NamespacedKey key = parseKey(null, entry.substring(0, separator).trim());
            Enchantment enchantment = key == null ? null : registry(RegistryKey.ENCHANTMENT).get(key);
            if (enchantment == null) {
                logger.warning("Ignored unknown enchantment: " + entry);
                continue;
            }
            try {
                merged.put(enchantment, Integer.parseInt(entry.substring(separator + 1).trim()));
            } catch (NumberFormatException ignored) {
                logger.warning("Ignored illegal-leveled enchantment: " + entry);
            }
        }

        if (!merged.isEmpty()) {
            item.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(merged));
        }
    }

    private static void applyAttributes(Tingere plugin, ItemStack item, ConfigurationSection comps, String keyPrefix) {
        if (!comps.isList("attributes")) {
            return;
        }

        Logger logger = plugin.getLogger();
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        boolean any = false;
        int index = 0;

        for (Map<?, ?> entry : comps.getMapList("attributes")) {
            index++;
            Object raw = entry.get("type");
            Attribute attribute = resolveAttribute(raw);
            if (attribute == null) {
                logger.warning("Ignored unknown attribute type: " + raw);
                continue;
            }
            Object amount = entry.get("amount");
            if (!(amount instanceof Number number)) {
                logger.warning("Invalid amount value " + amount + "for attribute " + raw);
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
        return key == null ? null : registry(RegistryKey.ATTRIBUTE).get(key);
    }

    private static <T extends Keyed> Registry<T> registry(RegistryKey<T> key) {
        return RegistryAccess.registryAccess().getRegistry(key);
    }

    private static AttributeModifier.Operation resolveOperation(Object raw) {
        if (raw != null) {
            try {
                return AttributeModifier.Operation.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
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

    private static void applyEquippable(ItemStack item, ConfigurationSection comps) {
        if (comps.getBoolean("equippable-on-head", false)) {
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).build());
        }
    }

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
