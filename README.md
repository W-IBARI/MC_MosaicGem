# MC_MosaicGem

Minecraft 服务器宝石镶嵌插件，支持**装备打孔、宝石镶嵌、宝石拆卸**，属性接入 **SX-Attribute**，适配 **Folia 26.2 / Java Edition 26.2**。

## 功能特性

- **装备打孔**：使用打孔器为装备添加孔位，成功率与数量上限可配置
- **宝石镶嵌**：宝石携带随机数值与属性行，镶嵌后注入装备 lore，由 SX-Attribute 读取生效
- **宝石拆卸**：拆卸后宝石原样返还，镶嵌时固定的随机数值不丢失
- **三种交互方式**：铁砧合成、工作台/随身合成、拖拽工具到目标物品，均可独立开关
- **完整反馈**：所有被拦截或失败的操作都会向玩家发送提示，文案全部可配置
- **管理指令**：重载配置、发放物品、调试查看、自检

## 环境要求

- 服务端：Folia 26.2（Java Edition 26.2）
- Java：JDK 25+
- 可选依赖：[SX-Attribute-Folia](https://github.com/Saukiya/SX-Attribute)（宝石属性生效需要，同时需要其前置 [SX-Item](https://github.com/Saukiya/SX-Item)）

> MosaicGem 将 SX-Attribute 声明为软依赖（softdepend）：未安装时插件本身可正常运行，但宝石注入的属性不会生效。

## 安装

1. 构建插件（见下文「构建」），或使用已发布的 jar
2. 将 `MosaicGem-*.jar` 放入服务端 `plugins` 目录
3. 如需属性生效，同时放入 `SX-Item` 与 `SX-Attribute` 的 jar
4. 启动服务端，插件会自动生成配置文件
5. 按需修改配置后执行 `/mosaicgem reload`

## 玩法机制

### 打孔

- 使用打孔器与目标装备进行交互（铁砧 / 工作台 / 拖拽）
- 打孔成功：装备孔数 +1，打孔器消耗
- 打孔失败：打孔器消耗，装备及其已镶嵌宝石不受影响
- 孔数上限为双维度，任一不满足都会被拦截：
  - **全局上限**：装备所有来源的孔数之和不得超过 `settings.max-holes`
  - **来源上限**：同一类打孔器给一件装备贡献的孔数不得超过该打孔器的 `holesnum`

### 镶嵌

- 目标装备必须已有孔位，且未镶嵌的宝石数量未达到孔数上限
- 宝石生成时按 `random` 配置随机取值并固定到该宝石实例，镶嵌时数值随之注入装备
- 属性行（`attribute`）会写入装备 lore，SX-Attribute 自动解析生效
- 同一种宝石可按 `repetitions` 限制重复镶嵌次数（不填为无上限）
- 目前仅支持 `buffType: sx_attribute`，其他类型会拦截镶嵌并提示

### 拆卸

- 拆卸器与已镶嵌宝石的装备交互，成功后移除最后一个镶嵌的宝石
- 宝石按原始随机数值原样返还（优先进背包，背包满则掉落脚边）
- 失败时拆卸器消耗，装备与剩余宝石不受影响

### 交互方式

| 方式 | 操作 |
| --- | --- |
| 铁砧 | 左侧放装备、右侧放工具（或反过来），结果槽预览，点击结果完成操作 |
| 合成 | 在 2x2 随身合成或工作台中放入「1 个工具 + 1 个目标装备」，点击结果 |
| 拖拽 | 鼠标光标持有工具，点击背包中的目标装备 |

三种方式均可在 `config.yml` 中独立开关。

## 指令与权限

| 指令 | 说明 | 权限 |
| --- | --- | --- |
| `/mosaicgem reload` | 重载全部配置文件 | `mosaicgem.reload`（默认 OP） |
| `/mosaicgem give <gem\|puncher\|remover> <id> [数量] [玩家]` | 给予宝石/打孔器/拆卸器 | `mosaicgem.give`（默认 OP） |
| `/mosaicgem debug [玩家]` | 查看物品的孔数、宝石、注入属性等调试信息 | `mosaicgem.debug`（默认所有玩家） |
| `/mosaicgem list <gem\|puncher\|remover>` | 列出已配置的物品 | `mosaicgem.list`（默认所有玩家） |
| `/mosaicgem selftest` | 无玩家环境自检：配置解析、物品生成、数据读写 | `mosaicgem.debug` |

指令别名：`/mg`、`/mgem`。

## 配置文件

配置文件位于 `plugins/MosaicGem/`，首次启动自动生成，修改后执行 `/mosaicgem reload` 热重载。

### config.yml

```yaml
settings:
  max-holes: 6            # 全局孔数硬上限（所有来源孔数之和）
  interactions:
    anvil: true           # 铁砧合成
    crafting: true        # 工作台/随身合成
    drag: true            # 拖拽工具到目标物品

messages:                 # 玩家消息，{xxx} 为占位符
  prefix: '&8[&6MosaicGem&8] '
  # ... 完整消息键见下方说明
```

所有拦截/失败场景均有默认消息，策划可直接修改文案：

| 消息键 | 场景 | 默认文案 |
| --- | --- | --- |
| `punch-max-global` | 打孔：全局孔数已满 | `&c打孔失败：该物品的孔数已达到全局上限（{max}）！` |
| `punch-max-source` | 打孔：打孔器来源上限已满 | `&c打孔失败：该打孔器类型已达到来源上限（{max}）！` |
| `punch-fail` | 打孔：成功率失败（工具消耗） | `&c打孔失败，打孔器已消耗！` |
| `socket-no-hole` | 镶嵌：装备没有孔位 | `&c镶嵌失败：该物品还没有孔位，请先打孔！` |
| `socket-full` | 镶嵌：孔位已满 | `&c镶嵌失败：该物品的孔位已满！` |
| `socket-repeat-limit` | 镶嵌：达到重复镶嵌次数上限 | `&c该宝石已达到可重复镶嵌次数上限！` |
| `socket-bufftype-unsupported` | 镶嵌：buffType 不受支持 | `&c镶嵌失败：该宝石的属性类型不受支持，无法镶嵌！` |
| `remove-empty` | 拆卸：没有已镶嵌宝石 | `&c拆卸失败：该物品没有已镶嵌的宝石！` |
| `remove-fail` | 拆卸：成功率失败（工具消耗） | `&c拆卸失败，拆卸器已消耗！` |
| `target-invalid` | 目标类型/材质不符合限制 | `&c该物品不能进行此操作！` |
| `interaction-disabled` | 该交互方式被禁用 | `&c该交互方式已被禁用！` |
| `tool-config-missing` | 工具在配置中不存在 | `&c该工具在配置中不存在，请联系管理员！` |
| `invalid-combination` | 工具+工具或无效组合 | `&c工具与目标物品的组合无效！` |
| `inventory-full` | 返还宝石时背包已满 | `&e背包已满，返还物品已掉落在地上。` |

另有成功/指令类消息：`punch-success`、`socket-success`、`remove-success`、`reload-success`、`give-*`、`player-not-found`、`no-permission` 等。

### gems.yml

```yaml
# 内部名（指令发放时使用）
测试宝石:
  material: PAPER              # 物品材质
  isEnchant: true              # 是否带附魔光效
  name: "&c测试宝石"            # 物品名称（支持 & 颜色代码）
  lore:                        # 物品 lore
    - '&a▪ 伤害增加：${random_value}'
  custom-model-data: 0         # 自定义模型序号
  targetType:                  # 可镶嵌装备类型，不填默认全生效
    - SWORD
  targetMaterial:              # 可镶嵌装备 id，不填默认全生效
    - IRON_SWORD
  repetitions: 5               # 同种宝石可重复镶嵌次数，不填默认无上限
  random:                      # 宝石生成时随机取值，可在 lore/attribute 中引用
    random_value: '10.00~20.00'
  buffType: 'sx_attribute'     # 属性生效方式，目前仅支持 sx_attribute
  attribute:                   # 注入装备 lore 的属性行（SX-Attribute 读取）
    - '攻击力：${random_value}'
    - '防御力：${random_value}'
```

`random` 支持多个随机数，格式为 `最小值~最大值`，小数位数按配置自动保留；生成后数值固定到该宝石实例，后续引用 `${随机数名}` 均使用同一数值。

支持的装备类型：`SWORD`、`SPEAR`、`AXE`、`HELMET`、`CHESTPLATE`、`LEGGINGS`、`BOOTS`、`ELYTRA`。

### punchers.yml

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

### removers.yml

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

## 数据存储

- 工具物品（宝石/打孔器/拆卸器）通过物品的 custom data 组件（PersistentDataContainer）标记类型与内部名
- 宝石实例生成时固定随机数值并写入组件数据
- 装备记录：总孔数、各来源打孔器的孔数、已镶嵌宝石列表（实例 UUID、随机数值、注入的属性行）
- 镶嵌采用注入式：属性行写入装备 lore，拆卸时按记录精确移除并原样返还宝石

## 构建

```powershell
.\gradlew.bat build
```

构建产物：`build/libs/MosaicGem-1.0.0-SNAPSHOT.jar`

开发环境：JDK 25、Gradle 9.6.1（项目自带 Wrapper）、`dev.folia:folia-api:26.2.build.3-beta`。

## 常见问题

**Q：属性不生效？**

确认 `SX-Item` 与 `SX-Attribute` 已安装并启用，宝石的 `buffType` 为 `sx_attribute`，且装备 lore 中包含注入的属性行。

**Q：修改配置后需要重启吗？**

不需要，执行 `/mosaicgem reload` 即可，宝石/打孔器/拆卸器与消息会一并重载。

**Q：如何排查配置问题？**

执行 `/mosaicgem selftest` 自检配置解析与数据读写；执行 `/mosaicgem debug` 查看手持物品的组件数据。
