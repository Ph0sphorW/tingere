package org.icarus.tingere.command;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class TingereCommandExecutor implements CommandExecutor, TabCompleter {

    private final Tingere plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendRichMessage("<red>用法: /tingere reload | /tingere get result <key> [数量] | /tingere get ingredient <key> [序号] | /tingere get recipes <玩家> <模式>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tingere.admin")) {
                sender.sendRichMessage("<red>你没有权限执行此命令");
                return true;
            }
            long start = System.currentTimeMillis();
            plugin.reloadRecipes();
            long time = System.currentTimeMillis() - start;
            sender.sendRichMessage("<green>配方重载完成，耗时 " + time + "ms");
            return true;

        } else if (args[0].equalsIgnoreCase("get")) {
            if (args.length < 2) {
                sender.sendRichMessage("<red>用法: /tingere get result <key> [数量] | /tingere get ingredient <key> [序号] | /tingere get recipes <玩家> <模式>");
                return true;
            }

            String subCommand = args[1].toLowerCase();
            switch (subCommand) {
                case "result" -> {
                    return handleGetResult(sender, args);
                }
                case "ingredient" -> {
                    return handleGetIngredient(sender, args);
                }
                case "recipes" -> {
                    return handleGetRecipes(sender, args);
                }
                default -> {
                    sender.sendRichMessage("<red>未知子命令，可用: result, ingredient, recipes");
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean handleGetRecipes(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            sender.sendRichMessage("<red>你没有权限执行此命令");
            return true;
        }
        if (args.length < 4) {
            sender.sendRichMessage("<red>用法: /tingere get recipes <玩家> <模式>");
            sender.sendRichMessage("<gray>模式: * 解锁所有配方, 或输入字符串解锁ID包含该字符串的配方");
            return true;
        }

        String playerName = args[2];
        String pattern = args[3];

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendRichMessage("<red>玩家 " + playerName + " 不在线或不存在");
            return true;
        }

        Set<NamespacedKey> allKeys = plugin.getRecipeLoader().getRegisteredKeys();
        if (allKeys.isEmpty()) {
            sender.sendRichMessage("<red>当前没有已加载的自定义配方");
            return true;
        }
        
        Set<NamespacedKey> filtered = new HashSet<>();
        if (pattern.equals("*")) {
            filtered.addAll(allKeys);
        } else {
            String lowerPattern = pattern.toLowerCase();
            for (NamespacedKey key : allKeys) {
                if (key.toString().toLowerCase().contains(lowerPattern)) {
                    filtered.add(key);
                }
            }
        }

        if (filtered.isEmpty()) {
            sender.sendRichMessage("<red>没有找到匹配的配方");
            return true;
        }

        Set<NamespacedKey> alreadyDiscovered = target.getDiscoveredRecipes();
        Set<NamespacedKey> toUnlock = new HashSet<>(filtered);
        toUnlock.removeAll(alreadyDiscovered);

        if (toUnlock.isEmpty()) {
            sender.sendRichMessage("<green>玩家 " + target.getName() + " 已经解锁了所有匹配的配方");
            return true;
        }

        target.discoverRecipes(toUnlock);

        sender.sendRichMessage("<green>已为玩家 " + target.getName() + " 解锁 " + toUnlock.size() + " 个新配方");
        if (!sender.equals(target)) {
            target.sendRichMessage("<green>管理员已为你解锁 " + toUnlock.size() + " 个新自定义配方");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("reload", "get");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            return List.of("result", "ingredient", "recipes");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("recipes")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("recipes")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            return suggestions;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("result")) {
            return new ArrayList<>(plugin.getRecipeLoader().loadAllRecipes());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("ingredient")) {
            return new ArrayList<>(plugin.getRecipeLoader().getAllRecipeKeys());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("ingredient")) {
            String key = args[2];
            List<ItemStack> ingredients = plugin.getRecipeLoader().getIngredients(key);
            if (ingredients != null) {
                return IntStream.rangeClosed(1, ingredients.size())
                        .mapToObj(String::valueOf)
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
    private boolean handleGetResult(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            sender.sendRichMessage("<red>你没有权限执行此命令");
            return true;
        }
        if (args.length < 3) {
            sender.sendRichMessage("<red>用法: /tingere get result <key> [数量]");
            return true;
        }

        String key = args[2];
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendRichMessage("<red>数量必须在 1 到 64 之间");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendRichMessage("<red>数量必须是整数");
                return true;
            }
        }

        ItemStack result = plugin.getRecipeLoader().getResultItem(key);
        if (result == null) {
            sender.sendRichMessage("<red>未找到配方: " + key);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>该命令只能由玩家执行");
            return true;
        }

        ItemStack giveItem = result.clone();
        giveItem.setAmount(amount);
        player.getInventory().addItem(giveItem).values().forEach(item ->
                player.getWorld().dropItem(player.getLocation(), item));
        player.sendRichMessage("<green>已获取配方 §e" + key + " <green>的结果物品 x" + amount);
        return true;
    }

    private boolean handleGetIngredient(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            sender.sendRichMessage("<red>你没有权限执行此命令");
            return true;
        }
        if (args.length < 3) {
            sender.sendRichMessage("<red>用法: /tingere get ingredient <key> [序号]");
            return true;
        }

        String key = args[2];
        int index = 1;
        if (args.length >= 4) {
            try {
                index = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendRichMessage("<red>序号必须是整数");
                return true;
            }
        }

        List<ItemStack> ingredients = plugin.getRecipeLoader().getIngredients(key);
        if (ingredients == null || ingredients.isEmpty()) {
            sender.sendRichMessage("<red>未找到配方或该配方没有原料: " + key);
            return true;
        }

        if (index < 1 || index > ingredients.size()) {
            sender.sendRichMessage("<red>序号无效，有效范围 1 ~ " + ingredients.size());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>该命令只能由玩家执行");
            return true;
        }

        ItemStack ingredient = ingredients.get(index - 1).clone();
        ingredient.setAmount(1);
        player.getInventory().addItem(ingredient).values().forEach(item ->
                player.getWorld().dropItem(player.getLocation(), item));
        player.sendRichMessage("<green>已获取配方 §e" + key + " <green>的第 " + index + " 个原料");
        return true;
    }

}
