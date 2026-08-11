# MC_MosaicGem

Minecraft 服务器插件项目，目标服务端为 **Folia 26.2**，游戏版本 **Java Edition 26.2**。

## 功能

- 装备打孔：使用打孔器为装备添加孔位（成功率、数量上限可配置）
- 宝石镶嵌：将宝石注入装备，属性行写入装备 lore，由 SX-Attribute 自动读取生效
- 宝石拆卸：使用拆卸器移除已镶嵌宝石，并原样返还宝石（随机数值不丢失）
- 三种交互方式（可在配置中开关）：铁砧合成、工作台合成、拖拽工具到目标物品
- 指令：`/mosaicgem reload`、`/mosaicgem give`、`/mosaicgem debug`、`/mosaicgem list`

## 开发环境

- JDK 25（Temurin 25.0.4）
- Gradle 9.6.1（项目自带 Wrapper）
- 插件 API：`dev.folia:folia-api:26.2.build.3-beta`

## 配置文件

首次启动后插件会在数据目录生成以下文件：

- `config.yml`：全局设置（最大孔数、交互开关、消息）
- `gems.yml`：宝石定义（随机数、属性注入、可镶嵌目标）
- `punchers.yml`：打孔器定义（成功率、孔数上限、目标限制）
- `removers.yml`：拆卸器定义（成功率、目标限制）

修改后执行 `/mosaicgem reload` 即可热重载。

## 构建插件

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/MosaicGem-1.0.0-SNAPSHOT.jar`，放入服务端的 `plugins` 目录即可。

> 属性生效依赖 SX-Attribute（softdepend），未安装时插件本体功能不受影响，但宝石属性不会生效。
