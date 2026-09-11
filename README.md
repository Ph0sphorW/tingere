# 🧪 Tingere 插件使用手册

**本文档急需重写。**

## 📖 简介

**Tingere** 是一个为 Paper 1.21.11 服务器设计的自定义配方插件，它突破了原版配方的限制，允许您创建**支持完整物品组件**的合成配方。无论是带有自定义模型数据、附魔、属性的工具，还是保留盒内物品的潜影盒，都能轻松实现。

### 核心特性

- ✅ **支持三种配方类型**：有序（shaped）、无序（shapeless）、转化（transmute）
- ✅ **原料支持精确匹配**（带组件的物品）和**材料匹配**（仅类型）
- ✅ **完整的物品组件配置**：`item-model`、`display-name`、`lore`、`enchantments`、`maxdamage`、`unbreakable`、`custom-model-data` 等
- ✅ **特殊配方机制**：绕过转化配方“输入输出不能相同”的限制，同时保留输入物品的组件
- ✅ **名称词缀**：可为物品名称添加前缀/后缀，不影响原名称
- ✅ **调试命令**：快速获取配方结果或原料，验证配置
- ✅ **热重载**：修改配方后无需重启服务器
- ✅ **纯数据驱动**：配方全部来自普通 YAML 文件，无需数据包、无需重编译

---

## 📦 安装

1. 将编译好的 `tingere-<版本>.jar` 放入服务器的 `plugins` 文件夹。
2. 重启服务器或执行 `/reload confirm`（Paper 1.21+ 建议重启）。
3. 插件会在 `plugins/tingere/recipes/` 目录下自动创建配方文件夹，将配方 YAML 放进去即可。

---

## 📁 目录结构

```
plugins/tingere/
└── recipes/                  # 存放所有配方 YAML 文件（自动创建）
    ├── example_shaped.yml
    ├── example_shapeless.yml
    ├── example_transmute.yml
    ├── 分组文件.yml           # 一个文件也可以写多个配方
    └── 子目录/                # 支持嵌套，会递归扫描
        └── nested.yml
```

配方文件可以任意命名、任意分层，支持 `.yml` 或 `.yaml` 后缀。一个文件可以包含**单个配方**（顶层有 `type` 字段）或**多个配方**（每个顶级键为一个配方 ID），后者便于按用途归类，例如：

```yaml
# 一个文件内的多个配方，顶级键仅作分组标识
sword_upgrade:
  type: transmute
  key: sword_upgrade
  input:
    material: DIAMOND_SWORD
  material:
    material: NETHERITE_INGOT
  result:
    material: NETHERITE_SWORD

apple_smelting:
  type: shapeless
  key: apple_to_gold
  ingredients:
    - material: APPLE
    - material: GOLD_INGOT
  result:
    material: GOLDEN_APPLE
```

> 💡 加载按文件路径字母序进行。单个文件解析失败只会记录警告并跳过，不影响其他文件。


---

## 📝 配方编写基础

每个配方必须包含以下基本字段：

- `type`：配方类型，可选 `shaped`、`shapeless`、`transmute`
- `key`：配方唯一标识符（只能包含小写字母、数字、下划线、连字符和点）
- `result`：结果物品的定义
- `ingredients`（shaped/shapsless）或 `input`/`material`（transmute）：原料定义

### 1. 有序配方（shaped）

```yaml
type: shaped
key: example_sword
pattern:
  - " A "
  - " B "
  - " C "
ingredients:
  A:
    material: BLAZE_ROD
    components:
      custom-model-data: 500
  B:
    material: DIAMOND
  C:
    material: STICK
result:
  material: DIAMOND_SWORD
  amount: 1
  components:
    display-name: "<gold>烈焰剑"
```

- `pattern`：3 行字符串，每个字符代表一个槽位，空格表示空位。每行长度必须相等（通常为 3）。
- `ingredients`：定义每个字符对应的原料。原料可带 `components`（见后文）。

### 2. 无序配方（shapeless）

```yaml
type: shapeless
key: example_apple
ingredients:
  - material: APPLE
    components:
      custom-model-data: 100
  - material: GOLD_INGOT
result:
  material: GOLDEN_APPLE
  amount: 1
```

- `ingredients` 是一个列表，顺序不重要，但必须包含所有原料。

### 3. 转化配方（transmute）

转化配方专门用于需要保留输入物品组件的场景，如潜影盒染色、工具升级等。

```yaml
type: transmute
key: dye_shulker
input:
  material: WHITE_SHULKER_BOX
material:
  material: RED_DYE
result:
  material: RED_SHULKER_BOX
  amount: 1              # 可选，默认 1
  components:            # 可选，在合成期附加到结果上
    display-name: "<red>染色的潜影盒"
```

- `input`：主材料，其组件会被保留到输出。
- `material`：辅助材料，会被消耗。
- `result`：指定 `material`（物品类型）。
- ⚠️ 转化配方的 `result.components` 与 `result.amount` **无法在注册阶段生效** —— Bukkit 的 `TransmuteRecipe` 构造器只接受 `Material`，数量也固定为 1。插件会在合成瞬间（`PrepareItemCraftEvent`）补上，因此这两项对转化配方是有效的，但依赖事件监听。

> 三类配方的 `result.components` 行为一致；其中的差异（转化配方需要合成期补偿）已由插件自动处理，写配置时无需区分。

---

## ✨ 高级特性

### 1. 物品组件配置

在 `result` 或任何原料的 `components` 下，可以配置以下属性。**同一个 `components` 键写在任何位置（结果、原料、`special`）语义完全一致**，均通过 Paper 的 DataComponent API 写入：

| 组件                  | 格式               | 说明                                                        |
| --------------------- | ------------------ | ----------------------------------------------------------- |
| `display-name`        | MiniMessage 字符串 | 物品自定义名称（优先级最高）                                |
| `prefix`              | MiniMessage 字符串 | 在**原有名称**前添加前缀（与 `display-name` 共存时被忽略）  |
| `suffix`              | MiniMessage 字符串 | 在**原有名称**后添加后缀                                     |
| `lore`                | 字符串列表         | 物品描述文字                                                |
| `item-model`          | `"命名空间:路径"`  | 物品模型（1.21+ 模型系统）；不含 `:` 时挂到插件命名空间下   |
| `custom-model-data`   | 整数               | 旧版自定义模型数据（写入为 `floats[0]`，与原版迁移一致）    |
| `enchantments`        | 字符串列表         | 附魔，格式 `"附魔ID:等级"`；支持 `"minecraft:sharpness:3"`  |
| `attributes`          | 映射列表           | 属性修饰符，见下方说明                                      |
| `maxdamage`           | 整数               | 自定义最大耐久值                                            |
| `unbreakable`         | `true`/`false`     | 是否无法破坏                                                |
| `glint`               | `true`/`false`     | 强制附魔光效开关（`false` 可让附魔物品不发光）              |
| `equippable-on-head`  | `true`/`false`     | 允许该物品戴在头上（**默认关闭**）                          |

> ⚠️ `enchantments` 与物品已有的附魔是**合并**关系，而非替换。因此对「复制输入组件」的特殊配方，原有附魔不会被清掉。

`attributes` 的每一项：

```yaml
attributes:
  - type: attack_damage     # 也接受旧名 generic.attack_damage
    amount: 3.0
    operation: ADD_NUMBER   # 省略则 ADD_NUMBER，可选 ADD_SCALAR / MULTIPLY_SCALAR_1
    slot: mainhand          # 省略则 any，可选 mainhand/offhand/hand/feet/legs/chest/head/armor/body 等
```

- `type` 使用 1.21.2+ 的属性名（去掉 `generic.` 前缀的形式），旧名会自动兼容。
- 名称无法识别时会以 WARNING 记录在控制台并跳过该项；`amount` 非数字同理。



### 2. 原料匹配模式

默认情况下，原料使用**精确匹配**（`exact`），即必须与配置的 `ItemStack` 完全一致（包括所有组件）。如果需要仅匹配物品类型（忽略组件），可以设置 `match-mode: material`。

```yaml
ingredients:
  A:
    material: DIAMOND_SWORD
    match-mode: material   # 任何钻石剑都能匹配，无论附魔、名称等
```

支持的有序/无序配方中，每个原料可独立设置匹配模式。

### 3. 特殊配方（绕过 ID 限制）

原版转化配方不允许输出物品与输入物品 ID 相同（如钻石剑→钻石剑）。通过 `special` 节点，我们可以实现"同 ID 转化"，同时保留输入组件的所有数据。

```yaml
type: transmute
key: upgrade_same_sword
input:
  material: DIAMOND_SWORD
material:
  material: NETHERITE_INGOT
result:
  material: BARRIER                 # 中间物品，实际不会被使用
  amount: 1                         # 真实结果数量（special 生效时以这里为准）
  components:                       # 会先应用，可被 special.components 同名覆盖
    lore:
      - "<gray>来自 result.components"
special:
  target-material: DIAMOND_SWORD    # 最终物品材质（可与输入相同）
  copy-input: true                  # 是否复制输入组件
  source-character: "A"             # 指定源物品的 pattern 字符（可选）
  # 或 source-slot: 0               # 指定源物品的工作台槽位（0-8）
  components:                       # 后应用，同名键覆盖 result.components
    item-model: "rwskins:upgraded_sword"
    prefix: "<gold>[强化] "
    attributes:
      - type: "attack_damage"
        amount: 12.0
```

- `special` 节点会覆盖原配方逻辑，无论配方类型如何。
- 最终物品材质为 `target-material`（**必填且必须有效**，否则加载时会 WARNING 并忽略整个 `special` 段，退化为普通转化配方）。
- 组件应用顺序为 `result.components` → `special.components`，后者覆盖同名键，因此两处可以分工（例如基础描述放 result、强化属性放 special）。
- `result.amount` 决定最终数量；不写则为 1。
- `source-character` 或 `source-slot` 用于精确指定从哪个槽位获取输入物品（避免取错）。

### 4. 名称词缀

在 `components` 中使用 `prefix` 和 `suffix` 可以为物品原有名称添加前后缀，而不覆盖原名称（包括自定义名称或默认名称）。

```yaml
components:
  prefix: "<red>[稀有] "          # 添加红色前缀
  suffix: " <gray>+1"              # 添加灰色后缀
```

如果同时指定了 `display-name`，则 `display-name` 优先，词缀无效。

---

## 🛠️ 命令与权限

所有命令需要权限 `tingere.admin`（OP 默认拥有）。命令别名：`/tg`、`/trecipes`。

| 命令                                        | 描述                                          |
| ------------------------------------------- | --------------------------------------------- |
| `/tingere reload`                           | 重载所有配方文件（热重载）                    |
| `/tingere get result <配方key> [数量]`      | 获取指定配方的结果物品（用于验证）            |
| `/tingere get ingredient <配方key> [序号]`  | 获取指定配方的某个原料（序号从 1 开始）       |
| `/tingere get recipes <玩家> <模式>`        | 为玩家解锁配方，模式 `*` 表示全部，或填子串   |

Tab 补全会自动列出可用的配方 key、原料序号、在线玩家名。

---

## 🧪 示例配方大全

### 1. 带自定义模型的下界合金剑（有序）
```yaml
type: shaped
key: netherite_sword_custom
pattern:
  - " N "
  - " S "
  - "   "
ingredients:
  N:
    material: NETHERITE_INGOT
  S:
    material: STICK
result:
  material: NETHERITE_SWORD
  components:
    item-model: "mypack:netherite_sword_crystal"
    display-name: "<aqua>水晶下界合金剑"
    enchantments:
      - sharpness:6
    unbreakable: true
```

### 2. 保留附魔的转化配方（特殊配方）
```yaml
type: transmute
key: upgrade_sword
input:
  material: DIAMOND_SWORD
material:
  material: NETHERITE_INGOT
result:
  material: BARRIER
special:
  target-material: NETHERITE_SWORD
  copy-input: true
  source-character: "A"
  components:
    prefix: "<gold>[升级] "
    attributes:
      - type: "generic.attack_damage"
        amount: 3.0
        operation: ADD_NUMBER
        slot: mainhand
```
效果：任何钻石剑（带附魔）与下界合金锭合成，得到下界合金剑，保留原附魔，增加 3 点攻击伤害，名称前加金色前缀。

### 3. 仅类型匹配的无序配方
```yaml
type: shapeless
key: any_diamond_sword_to_stick
ingredients:
  - material: DIAMOND_SWORD
    match-mode: material   # 任何钻石剑均可
result:
  material: STICK
  amount: 2
```
任意钻石剑（无论附魔、名称）合成 2 个木棍。

---

## ❓ 常见问题与排错

### Q: 配方加载成功但无法合成？
A: 可能原因：
- 原料匹配过于严格（使用了精确匹配但玩家物品无对应组件）。检查原料的 `match-mode` 或简化组件。
- 有序配方的 `pattern` 长度不一致或字符未定义。
- 转化配方中 `input` 和 `result` 材质相同且未使用特殊配方（原版限制）。

### Q: 特殊配方无效，预览仍是中间物品？
A: 请检查：
- 控制台是否有“已存储特殊配方: xxx -> 材质”的日志，若无则配置未正确解析。
- `target-material` 是否拼写正确（必须是大写枚举名，如 `DIAMOND_SWORD`）。
- `source-character` 是否对应正确字符，且工作台对应槽位有物品。
- 监听器是否注册（重启服务器确认）。

### Q: 附魔/组件丢失？
A: 检查：
- `copy-input: true` 是否设置。
- 在 `components` 中是否误写了空的 `enchantments` 列表（即使空列表也会清空附魔，当前版本空列表不会触发设置，但建议不写）。
- `findSourceItem` 是否选错了槽位（查看控制台日志）。

### Q: 名称前缀/后缀不生效？
A:
- 如果同时指定了 `display-name`，则前缀/后缀被覆盖。
- 确认使用了正确的 MiniMessage 格式（如 `<red>`、`<bold>`）。

### Q: 如何调试？
A: 使用命令 `/tingere get result <key>` 获取结果物品，`/tingere get ingredient <key> 1` 获取原料，检查 NBT 是否符合预期。

---

## 🔐 权限

| 权限节点            | 描述                 | 默认 |
| ------------------- | -------------------- | ---- |
| `tingere.admin` | 允许使用所有插件命令 | OP   |

---

## 💡 最后

Tingere 旨在让 Minecraft 服务器管理员能够充分发挥物品组件的潜力，创造独特的合成体验。如果您有任何问题或建议，欢迎随时提出！

Happy Crafting! ⚒️