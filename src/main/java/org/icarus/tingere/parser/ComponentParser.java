package org.icarus.tingere.parser;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
public final class ComponentParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String[] LEGACY_ATTRIBUTE_PREFIXES = {"generic.", "player.", "zombie.", "horse."};
    private ComponentParser() {
    }

    public static ItemStack apply(Tingere plugin, ItemStack item, JsonNode comps, String keyPrefix) {
        if (item == null || comps == null || !comps.isObject()) {
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

    private static void applyCustomModelData(ItemStack item, JsonNode comps) {
        if (!has(comps, "custom-model-data")) {
            return;
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(intValue(comps, "custom-model-data")).build());
    }

    private static void applyItemModel(Tingere plugin, ItemStack item, JsonNode comps) {
        NamespacedKey key = parseKey(plugin, text(comps, "item-model"));
        if (key != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, key);
        }
    }

    private static void applyGlint(ItemStack item, JsonNode comps) {
        if (has(comps, "glint")) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, boolValue(comps, "glint"));
        }
    }

    private static void applyName(ItemStack item, JsonNode comps) {
        String displayName = text(comps, "display-name");
        if (displayName != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(displayName));
            return;
        }

        String prefix = text(comps, "prefix");
        String suffix = text(comps, "suffix");
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

    private static void applyLore(ItemStack item, JsonNode comps) {
        if (!has(comps, "lore")) {
            return;
        }
        List<Component> lore = new ArrayList<>();
        for (String line : strings(comps, "lore")) {
            lore.add(MINI_MESSAGE.deserialize(line));
        }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
    }

    private static void applyEnchantments(Tingere plugin, ItemStack item, JsonNode comps) {
        if (!has(comps, "enchantments")) {
            return;
        }

        Logger logger = plugin.getLogger();
        Map<Enchantment, Integer> merged = new HashMap<>(item.getEnchantments());
        for (String entry : strings(comps, "enchantments")) {
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

    private static void applyAttributes(Tingere plugin, ItemStack item, JsonNode comps, String keyPrefix) {
        JsonNode attributes = comps.get("attributes");
        if (attributes == null || !attributes.isArray()) {
            return;
        }

        Logger logger = plugin.getLogger();
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        boolean any = false;
        int index = 0;

        for (JsonNode entry : attributes) {
            index++;
            String rawType = text(entry, "type");
            Attribute attribute = resolveAttribute(rawType);
            if (attribute == null) {
                logger.warning("Ignored unknown attribute type: " + rawType);
                continue;
            }
            JsonNode amountNode = entry.get("amount");
            if (amountNode == null || !amountNode.isNumber()) {
                logger.warning("Invalid amount value " + amountNode + " for attribute " + rawType);
                continue;
            }

            NamespacedKey modifierKey = new NamespacedKey(plugin, keyPrefix + "_attr_" + index);
            AttributeModifier modifier = new AttributeModifier(
                    modifierKey, amountNode.doubleValue(), resolveOperation(text(entry, "operation")));
            builder.addModifier(attribute, modifier, resolveSlotGroup(text(entry, "slot")));
            any = true;
        }

        if (any) {
            item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        }
    }

    private static Attribute resolveAttribute(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Attribute attribute = lookupAttribute(normalized);
        if (attribute != null) {
            return attribute;
        }
        for (String legacy : LEGACY_ATTRIBUTE_PREFIXES) {
            if (normalized.startsWith(legacy)) {
                return lookupAttribute(normalized.substring(legacy.length()));
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

    private static AttributeModifier.Operation resolveOperation(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return AttributeModifier.Operation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return AttributeModifier.Operation.ADD_NUMBER;
    }

    private static EquipmentSlotGroup resolveSlotGroup(String raw) {
        if (raw != null && !raw.isBlank()) {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw.trim());
            if (group != null) {
                return group;
            }
        }
        return EquipmentSlotGroup.ANY;
    }

    private static void applyMaxDamage(ItemStack item, JsonNode comps) {
        // 依旧历史遗留问题
        String field = has(comps, "max-damage") ? "max-damage" : "maxdamage";
        if (has(comps, field)) {
            item.setData(DataComponentTypes.MAX_DAMAGE, intValue(comps, field));
        }
    }

    private static void applyUnbreakable(ItemStack item, JsonNode comps) {
        if (boolValue(comps, "unbreakable")) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        }
    }

    private static void applyEquippable(ItemStack item, JsonNode comps) {
        if (boolValue(comps, "equippable-on-head")) {
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).build());
        }
    }

    private static NamespacedKey parseKey(Tingere plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.contains(":")
                ? NamespacedKey.fromString(raw)
                : (plugin == null ? NamespacedKey.minecraft(raw) : new NamespacedKey(plugin, raw));
    }

    // fallbacks

    private static boolean has(JsonNode node, String field) {
        return node.hasNonNull(field);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asInt(0);
    }

    private static boolean boolValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode entry : value) {
            result.add(entry.asText());
        }
        return result;
    }
}
