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
public class RecipeCommand implements CommandExecutor, TabCompleter {

    private final Tingere plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c用法: /tingere reload | /tingere get result <key> [数量] | /tingere get ingredient <key> [序号] | /tingere get recipes <玩家> <模式>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tingere.admin")) {
                sender.sendMessage("§c你没有权限执行此命令");
                return true;
            }
            long start = System.currentTimeMillis();
            plugin.reloadRecipes();
            long time = System.currentTimeMillis() - start;
            sender.sendMessage("§a配方重载完成，耗时 " + time + "ms");
            return true;

        } else if (args[0].equalsIgnoreCase("get")) {
            if (args.length < 2) {
                sender.sendMessage("§c用法: /tingere get result <key> [数量] | /tingere get ingredient <key> [序号] | /tingere get recipes <玩家> <模式>");
                return true;
            }

            String subCommand = args[1].toLowerCase();
            if (subCommand.equals("result")) {
                return handleGetResult(sender, args);
            } else if (subCommand.equals("ingredient")) {
                return handleGetIngredient(sender, args);
            } else if (subCommand.equals("recipes")) {
                return handleGetRecipes(sender, args);
            } else {
                sender.sendMessage("§c未知子命令，可用: result, ingredient, recipes");
                return true;
            }
        }
        return false;
    }

    // 新增处理解锁配方的私有方法
    private boolean handleGetRecipes(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§c用法: /tingere get recipes <玩家> <模式>");
            sender.sendMessage("§7模式: * 解锁所有配方, 或输入字符串解锁ID包含该字符串的配方");
            return true;
        }

        String playerName = args[2];
        String pattern = args[3];

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§c玩家 " + playerName + " 不在线或不存在");
            return true;
        }

        Set<NamespacedKey> allKeys = plugin.getRecipeLoader().getRegisteredKeys();
        if (allKeys.isEmpty()) {
            sender.sendMessage("§c当前没有已加载的自定义配方");
            return true;
        }

        // 根据模式过滤
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
            sender.sendMessage("§c没有找到匹配的配方");
            return true;
        }

        // 获取玩家已解锁的配方，只解锁未解锁的
        Set<NamespacedKey> alreadyDiscovered = target.getDiscoveredRecipes();
        Set<NamespacedKey> toUnlock = new HashSet<>(filtered);
        toUnlock.removeAll(alreadyDiscovered);

        if (toUnlock.isEmpty()) {
            sender.sendMessage("§a玩家 " + target.getName() + " 已经解锁了所有匹配的配方");
            return true;
        }

        target.discoverRecipes(toUnlock);

        sender.sendMessage("§a已为玩家 " + target.getName() + " 解锁 " + toUnlock.size() + " 个新配方");
        if (!sender.equals(target)) {
            target.sendMessage("§a管理员已为你解锁 " + toUnlock.size() + " 个新自定义配方");
        }
        return true;
    }

    // 同时需要修改 Tab 补全，增加 recipes 子命令的补全
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
            // 补全在线玩家名
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("recipes")) {
            // 补全 "*" 和可能的示例模式（如配方 key 片段）
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            // 可添加一些常见片段提示，但不是必须
            return suggestions;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("result")) {
            // 原有的 result 补全
            return new ArrayList<>(plugin.getRecipeLoader().getAllRecipeKeys());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("ingredient")) {
            // 原有的 ingredient 补全
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
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§c用法: /tingere get result <key> [数量]");
            return true;
        }

        String key = args[2];
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage("§c数量必须在 1 到 64 之间");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c数量必须是整数");
                return true;
            }
        }

        ItemStack result = plugin.getRecipeLoader().getResultItem(key);
        if (result == null) {
            sender.sendMessage("§c未找到配方: " + key);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        ItemStack giveItem = result.clone();
        giveItem.setAmount(amount);
        player.getInventory().addItem(giveItem).values().forEach(item ->
                player.getWorld().dropItem(player.getLocation(), item));
        player.sendMessage("§a已获取配方 §e" + key + " §a的结果物品 x" + amount);
        return true;
    }

    private boolean handleGetIngredient(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tingere.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§c用法: /tingere get ingredient <key> [序号]");
            return true;
        }

        String key = args[2];
        int index = 1; // 默认序号为1
        if (args.length >= 4) {
            try {
                index = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c序号必须是整数");
                return true;
            }
        }

        List<ItemStack> ingredients = plugin.getRecipeLoader().getIngredients(key);
        if (ingredients == null || ingredients.isEmpty()) {
            sender.sendMessage("§c未找到配方或该配方没有原料: " + key);
            return true;
        }

        if (index < 1 || index > ingredients.size()) {
            sender.sendMessage("§c序号无效，有效范围 1 ~ " + ingredients.size());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        ItemStack ingredient = ingredients.get(index - 1).clone();
        ingredient.setAmount(1); // 原料只给1个
        player.getInventory().addItem(ingredient).values().forEach(item ->
                player.getWorld().dropItem(player.getLocation(), item));
        player.sendMessage("§a已获取配方 §e" + key + " §a的第 " + index + " 个原料");
        return true;
    }

}
