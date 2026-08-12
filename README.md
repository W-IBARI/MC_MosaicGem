# MC_MosaicGem

Minecraft 服务器宝石镶嵌插件，支持**装备打孔、宝石镶嵌、宝石拆卸**，属性支持 **SX-Attribute（lore 合并）** 与 **原版属性（AttributeModifier）**，适配 **Folia 26.2 / Java Edition 26.2**。

## 功能特性

- **装备打孔**：使用打孔器为装备添加孔位，成功率与双维度孔数上限可配置
- **宝石镶嵌**：宝石携带随机数值，`sx_attribute` 宝石合并进装备属性面板由 SX-Attribute 读取生效；`vanilla_attribute` 宝石直接附加到装备的原版属性修饰符
- **宝石拆卸**：拆卸后宝石按原随机数值返还，装备属性面板自动还原
- **属性面板合并**：物品原有 `攻击力：13.90` + 宝石 +20 → `攻击力：33.90（+20）`，宝石独有的属性自动新增行
- **三种交互方式**：铁砧合成、工作台/随身合成、拖拽工具到目标物品，均可独立开关
- **完整反馈**：所有被拦截或失败的操作都会提示玩家，文案按语言拆分到独立的 `messages/` 目录语言文件中配置
- **管理指令**：重载配置、发放物品、调试查看、列表、自检
- **格式保留**：镶嵌/打孔不会破坏物品原有 lore 的颜色、斜体、加粗等格式

## 环境要求

- 服务端：Folia 26.2（Java Edition 26.2）
- Java：JDK 25+
- 可选依赖：[SX-Attribute-Folia](https://github.com/Saukiya/SX-Attribute)（宝石属性生效需要，同时需要其前置 [SX-Item](https://github.com/Saukiya/SX-Item)）

> MosaicGem 将 SX-Attribute 声明为软依赖（softdepend）：未安装时插件本身可正常运行，但宝石属性不会生效。

## 安装

1. 构建插件（见下文「构建」），或使用已发布的 jar
2. 将 `MosaicGem-*.jar` 放入服务端 `plugins` 目录
3. 如需属性生效，同时放入 `SX-Item` 与 `SX-Attribute` 的 jar
4. 启动服务端，插件会自动生成 `config.yml`、`messages/zh_cn.yml`、`messages/en_us.yml`、`items/gems.yml`、`items/punchers.yml`、`items/removers.yml`
5. 按需修改配置后执行 `/mosaicgem reload`

> 配置热重载时若发现某个 yml 缺失，会自动从插件内置默认版本补齐，不会覆盖已有文件。

## 玩法机制

### 打孔

- 使用打孔器与目标装备交互（铁砧 / 工作台 / 拖拽）
- 打孔成功：装备孔数 +1，打孔器消耗 1 个
- 打孔失败：打孔器消耗 1 个，装备及其已镶嵌宝石不受影响
- `rate` 为百分比（0-100）
- 孔数上限为双维度，任一不满足都会被拦截并提示：
  - **全局上限**：装备所有来源的孔数之和不得超过 `settings.max-holes`
  - **来源上限**：同一类打孔器给一件装备贡献的孔数不得超过该打孔器的 `holesnum`

### 镶嵌

- 目标装备必须已有孔位，且已镶嵌数量未达到孔数上限
- 宝石生成时按 `random` 配置随机取值并固定到该宝石实例；批量发放时每颗宝石随机值独立
- 同一种宝石可按 `repetitions` 限制重复镶嵌次数（不填为无上限）
- `buffType` 支持 `sx_attribute`（写入 lore 由 SX-Attribute 读取）与 `vanilla_attribute`（附加原版属性修饰符），其他类型会拦截镶嵌并提示
- `sx_attribute` 属性面板合并规则：
  - 物品原有属性行与所有宝石同类数值求和，显示为 `总值（+加成）`，如 `攻击力：33.90（+20）`
  - 宝石独有的属性自动追加新属性行
  - 加成数值的小数位：单颗宝石生效时去掉多余小数零（`+20`）；多颗宝石生效时取小数位最多的宝石的位数（`+40.00`）
  - 加成文字通过 `§X` 标记与数值隔离，SX-Attribute 只读取纯总值，不会把 `（+20）` 算进属性
- `vanilla_attribute` 原版属性合并规则：
  - 同属性的多颗宝石会合并为一个 AttributeModifier，tooltip 只显示一行总值（如两颗合计 +11 → `装备时：攻击力 +11`）
  - 物品原有的同属性 ADD_NUMBER 修饰符会一并合并进总值，显示在“原值上增加”
  - 被合并的原生修饰符会存入物品数据，宝石取下后自动还原

### 拆卸

- 拆卸器与已镶嵌宝石的装备交互，成功后移除最后一个镶嵌的宝石
- 宝石按原始随机数值原样返还（优先进背包，背包满则掉落脚边）
- 装备属性面板自动还原：`sx_attribute` 原有属性行恢复原始数值、宝石新增行整行移除；`vanilla_attribute` 修饰符按剩余宝石重建，被合并的原生修饰符自动还原
- 失败时拆卸器消耗 1 个，装备与剩余宝石不受影响

### 工具消耗与堆叠

- 一次行为只消耗 1 个工具，剩余工具继续留在光标/原槽位，可连续操作
- 光标持有工具时点击同类工具（合并/换位）交给原版处理，不拦截
- 创造模式与生存模式操作逻辑一致（目标修改、工具消耗、剩余保留）

### 交互方式

| 方式 | 操作 |
| --- | --- |
| 铁砧 | 左侧放装备、右侧放工具（或反过来），结果槽预览，点击结果完成操作 |
| 合成 | 在 2x2 随身合成或工作台中放入「1 个工具 + 1 个目标装备」，点击结果 |
| 拖拽 | 鼠标左键拾取工具吸附到光标，再点击背包中的目标装备 |

三种方式均可在 `config.yml` 的 `settings.interactions` 中独立开关。

## 指令与权限

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/mosaicgem reload` | 重载全部配置文件（缺失的 yml 自动补齐） | `mosaicgem.reload`（默认 OP） |
| `/mosaicgem give <id> [数量] [玩家]` | 给予宝石/打孔器/拆卸器（自动匹配类型，不区分） | `mosaicgem.give`（默认 OP） |
| `/mosaicgem debug [玩家]` | 查看物品的孔数、宝石、属性行等调试信息 | `mosaicgem.debug`（默认所有玩家） |
| `/mosaicgem list <gem\|puncher\|remover>` | 列出已配置的物品 | `mosaicgem.list`（默认所有玩家） |
| `/mosaicgem selftest` | 无玩家环境自检：配置解析、物品生成、数据读写、属性合并 | `mosaicgem.debug` |

指令别名：`/mg`、`/mgem`。

## 配置文件

配置文件位于 `plugins/MosaicGem/`，首次启动自动生成，修改后执行 `/mosaicgem reload` 热重载。

### config.yml

```yaml
settings:
  language: zh_cn          # 消息语言：zh_cn（简体中文）/ en_us（English）
  max-holes: 6            # 全局孔数硬上限（所有来源孔数之和）
  interactions:
    anvil: true           # 铁砧合成
    crafting: true        # 工作台/随身合成
    drag: true            # 拖拽工具到目标物品
```

#### socket-lore：装备上的镶嵌信息

打孔/镶嵌/拆卸成功后自动更新，展示孔位与宝石：

```yaml
socket-lore:
  enabled: true
  lines:
    - ''
    - '&r&f[ &6镶嵌信息 &f]'
    - '&r&7孔位: &f{holes}&7/&f{max_holes}'
  gem-lines:
    - '&r&7宝石{index}: &f{gem}'
    - '&r&7  {value_lines}'
  empty-line: '&r&7暂无宝石'
```

占位符：

| 占位符 | 说明 |
| --- | --- |
| `{holes}` | 已经镶嵌的孔洞数（孔内宝石数） |
| `{max_holes}` | 物品可用的孔洞数（已打孔数） |
| `{gem_count}` | 已镶嵌宝石数量 |
| `{index}` | 宝石序号（从 1 开始） |
| `{gem}` | 宝石显示名 |
| `{id}` | 宝石内部名 |
| `{values}` | 宝石数值描述（单行合并，如 `攻击力：20.00、防御力：20.00`） |
| `{value_lines}` | 宝石数值描述（每个属性单独一行） |

#### attribute-lore：属性面板合并

```yaml
attribute-lore:
  enabled: true
  names: []                          # 额外需要识别的属性名（一般不需要填）
  new-line: '&r&f{name}：&e{value}'  # 宝石独有属性的新增行模版
  bonus-format: '&r（+{bonus}）'     # 加成标注格式
```

占位符：`{name}` 属性名、`{value}` 属性总值、`{bonus}` 宝石加成值。

> 默认模板均带 `&r`（正体）。如需自定义颜色，可直接在模板中加入颜色代码（`&` 或 `§x` 十六进制）。

### 多语言消息文件

所有玩家消息按语言拆分：`config.yml` 的 `settings.language` 决定加载 `messages/` 目录下的哪个文件（如 `zh_cn` 对应 `messages/zh_cn.yml`），`{xxx}` 为占位符。内置语言：

- `messages/zh_cn.yml`：简体中文
- `messages/en_us.yml`：English（英文）

语言值不区分大小写，`-` 与 `_` 等价（如 `en-US` 与 `en_us` 均可）。若指定语言的文件不存在，会自动回退到中文文件；旧版 `messages.yml` 在中文语言下仍会被优先读取，用于保留已自定义的文案。

语言文件里还有一个 `attribute-names` 段，用于把原版属性 id（如 `minecraft:attack_damage`）映射成玩家可见的显示名（如 `攻击力`），`vanilla_attribute` 宝石镶嵌后的镶嵌信息 lore 会使用这里的名字。

| 消息键 | 场景 |
| --- | --- |
| `punch-max-global` | 打孔：全局孔数已满 |
| `punch-max-source` | 打孔：打孔器来源上限已满 |
| `punch-fail` | 打孔：成功率失败（工具消耗） |
| `socket-no-hole` | 镶嵌：装备没有孔位 |
| `socket-full` | 镶嵌：孔位已满 |
| `socket-repeat-limit` | 镶嵌：达到重复镶嵌次数上限 |
| `socket-bufftype-unsupported` | 镶嵌：buffType 不受支持 |
| `remove-empty` | 拆卸：没有已镶嵌宝石 |
| `remove-fail` | 拆卸：成功率失败（工具消耗） |
| `target-invalid` | 目标类型/材质不符合限制 |
| `interaction-disabled` | 该交互方式被禁用 |
| `tool-config-missing` | 工具在配置中不存在 |
| `invalid-combination` | 工具+工具或无效组合 |
| `inventory-full` | 返还宝石时背包已满 |

另有成功/指令/帮助/调试/自检类消息：`punch-success`、`socket-success`、`remove-success`、`reload-success`、`give-*`、`player-not-found`、`no-permission`、`help-*`、`debug-*`、`selftest-*` 等，完整键值见文件内注释。

### items/gems.yml

```yaml
# 内部名（指令发放时使用）
SA测试宝石:
  material: PAPER              # 物品材质
  isEnchant: true              # 是否带附魔光效
  name: "&cSA测试宝石"          # 物品名称（支持 & 颜色代码）
  lore:                        # 物品 lore
    - '&a▪ 伤害增加：${random_value}'
  custom-model-data: 0         # 自定义模型序号
  targetType:                  # 可镶嵌装备类型，不填默认全生效
    - SWORD
  targetMaterial:              # 可镶嵌装备 id，不填默认全生效（与 targetType 为且关系）
    - IRON_SWORD
  repetitions: 5               # 同种宝石可重复镶嵌次数，不填默认无上限
  random:                      # 宝石生成时随机取值，可在 lore/attribute 中引用
    random_value: '10.00~20.00'
  buffType: 'sx_attribute'     # SX 属性：写入 lore，由 SX-Attribute 读取
  attribute:                   # 格式：属性名：数值
    - '攻击力：${random_value}'
    - '防御力：${random_value}'

原版测试宝石:
  material: PAPER
  isEnchant: true
  name: "&b原版测试宝石"
  lore:
    - '&a▪ 攻击伤害：${random_value}'
  custom-model-data: 0
  targetType:
    - SWORD
  targetMaterial:
    - IRON_SWORD
  repetitions: 5
  random:
    random_value: '5.00~10.00'
  buffType: 'vanilla_attribute'  # 原版属性：直接附加到物品属性修饰符，不修改 lore
  attribute:                     # 格式：原版属性id：数值
    - 'minecraft:attack_damage: ${random_value}'
    - 'minecraft:attack_speed: 2'
```

- `random` 支持多个随机数，格式 `最小值~最大值`，小数位数按配置自动保留；生成后数值固定到该宝石实例
- `sx_attribute` 的属性行写进装备 lore 并参与属性面板合并；`vanilla_attribute` 的属性行直接附加为原版属性修饰符，**不会**修改/覆盖装备 lore
- 原版属性 id 的显示名在对应语言文件的 `attribute-names` 段中配置，未配置时显示原始 id
- `targetMaterial` 与 `targetType` 同时配置时需**同时满足**才可操作
- 支持的装备类型：`SWORD`、`SPEAR`、`AXE`、`HELMET`、`CHESTPLATE`、`LEGGINGS`、`BOOTS`、`ELYTRA`

### items/punchers.yml

```yaml
测试打孔器:
  material: PAPER
  isEnchant: true
  name: "&c测试打孔器"
  lore:
    - '我是lore描述'
  custom-model-data: 0
  targetType:
    - SWORD
  targetMaterial:
    - IRON_SWORD
  rate: 10                     # 成功率（0-100，百分比）
  holesnum: 2                  # 该类打孔器给同一物品的孔数上限
```

### items/removers.yml

```yaml
测试拆卸器:
  material: PAPER
  isEnchant: true
  name: "&c测试拆卸器"
  lore:
    - '我是lore描述'
  custom-model-data: 0
  targetType:
    - SWORD
  targetMaterial:
    - IRON_SWORD
  rate: 10                     # 成功率（0-100，百分比）
```

## 数据存储与属性计算

- 工具物品（宝石/打孔器/拆卸器）通过物品的 custom data 组件（PersistentDataContainer）标记类型与内部名
- 宝石实例生成时固定随机数值并写入组件数据
- 装备记录：总孔数、各来源打孔器的孔数、已镶嵌宝石列表（实例 UUID、随机数值）、属性合并前的原始属性行
- 原版属性：被合并进宝石修饰符的物品原生修饰符会存入物品 PDC，宝石取下后用于还原
- 属性合并行与镶嵌信息行使用 `§X` 标记：
  - 合并行中 `§X` 之前的数值由 SX-Attribute 读取，之后的 `（+加成）` 不影响计算
  - 镶嵌信息行以 `§X` 开头，SX-Attribute 整行忽略，仅作展示
- 物品原有 lore 中的 `§r`、`§x` 十六进制色、斜体/加粗等按字面文本原样写回，不会丢失

## 构建

```powershell
.\gradlew.bat build
```

构建产物：`build/libs/MosaicGem-1.0.0-SNAPSHOT.jar`

开发环境：JDK 25、Gradle 9.6.1（项目自带 Wrapper）、`dev.folia:folia-api:26.2.build.3-beta`。

## 常见问题

**Q：属性不生效？**

确认 `SX-Item` 与 `SX-Attribute` 已安装并启用，宝石的 `buffType` 为 `sx_attribute`，且装备属性面板中存在合并后的属性行；`vanilla_attribute` 宝石则检查物品属性面板中是否存在对应原版属性。

**Q：修改配置后需要重启吗？**

不需要，执行 `/mosaicgem reload` 即可，全部 yml 会重载，缺失文件会自动补齐。

**Q：如何排查配置问题？**

执行 `/mosaicgem selftest` 自检配置解析、物品生成、数据读写与属性合并；执行 `/mosaicgem debug` 查看手持物品的组件数据。

## 开源许可

本插件基于 [GNU GPL v3](https://github.com/W-IBARI/SX-Attribute-Folia-fixed/blob/main/LICENSE) 开源许可发布（完整条款见仓库内 [LICENSE](LICENSE) 文件）。

允许任何人在遵守 GPL v3 条款的前提下使用、修改、分发本插件；修改后对外分发时需保留本许可声明，并以相同许可开源。
