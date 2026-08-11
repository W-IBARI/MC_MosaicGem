# MC_MosaicGem

Minecraft 服务器插件项目，目标服务端为 **Folia 26.2**，游戏版本 **Java Edition 26.2**。

## 开发环境

- JDK 25（Temurin 25.0.4）
- Gradle 9.6.1（项目自带 Wrapper）
- 插件 API：`dev.folia:folia-api:26.2.build.3-beta`

## 构建插件

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/MosaicGem-1.0.0-SNAPSHOT.jar`，放入服务端的 `plugins` 目录即可。
