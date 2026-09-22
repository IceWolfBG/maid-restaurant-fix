# 女仆餐厅：修复 (Maid Restaurant: Fix)

[女仆餐厅 (Maid Restaurant)](https://github.com/MasterMarisa/MaidRestaurant) 的修复与兼容附属模组，
在不改动女仆餐厅本体的前提下，修复若干崩溃与体验问题，并为点餐菜单补充实用功能。

本仓库为**双版本同仓维护**，两个加载器的工程位于独立子目录，功能保持一致。

## 目录结构

| 目录 | 平台 | Minecraft | Java |
|---|---|---|---|
| [`forge-1.20.1/`](forge-1.20.1) | Minecraft Forge | 1.20.1 | JDK 17 |
| [`neoforge-1.21.1/`](neoforge-1.21.1) | NeoForge 21.1.x | 1.21.1 | JDK 21 |

编译好的 jar 归档在 [`releases/`](releases) 下按版本分目录存放。

## 修复与功能

1. **汤锅空容器崩溃修复（水煎包等）**
   女仆烹饪水煎包这类成品不需要容器（carrier 为空）的汤锅配方时，原逻辑访问
   `carrier().getItems()[0]` 会数组越界 / 空指针导致崩溃。模组在烹饪任务头尾补充检查，
   对无容器配方直接取出产品，并复刻原版「先拿锅盖、下一 tick 再取餐」的节奏。

2. **烹饪异常保护**
   `cookTick()` 抛出的任何异常都会被拦截记录，不再向上击穿女仆行为系统，
   避免女仆 AI 状态崩溃、请求卡死在「已完成菜品」。

3. **烹饪状态判定兜底**
   当获取锅中材料的方法异常时，改为直接从汤锅方块实体读取已投入的材料与汤底，
   保证女仆能被正确判定为「烹饪中」而不是错误地进入找材料状态。

4. **同产物多配方替代搜索**
   同一道食物存在多个配方时（例如煎蛋既可用鸡蛋也可用海龟蛋），当前配方材料不足会
   自动尝试最多 3 个产出相同的替代配方，避免只因一种替代材料缺失就无法烹饪。

5. **点餐菜单去重**
   同一物品的不同数量条目（如熟兔肉 *1、*2、*3……）合并为 *1、*4、*9；
   没有首选数量时保留最小与最大数量，减少菜单冗余。

6. **点餐菜单拼音搜索（可选）**
   安装 [Just Enough Characters (JEC)](https://www.curseforge.com/minecraft/mc-mods/just-enough-characters) 后，
   可在女仆餐厅点餐菜单用全拼或声母组合搜索中文菜名；未安装时自动降级为原版文字匹配，行为不变。

## 依赖

- 必需：[女仆餐厅 Maid Restaurant](https://github.com/MasterMarisa/MaidRestaurant)、车万女仆 (Touhou Little Maid)
- 可选：森罗物语：厨房 (Kaleidoscope Cookery)、Just Enough Characters

## 从源码构建

各版本工程通过本地 `libs/` 目录引用女仆餐厅、车万女仆、森罗厨房的 jar（第三方 jar 不随仓库分发）。
构建前请把**对应 MC 版本**的依赖 jar 放入该工程的 `libs/` 目录，然后：

```bash
cd forge-1.20.1      # 或 neoforge-1.21.1
./gradlew build      # Windows 用 gradlew.bat
```

产物在对应工程的 `build/libs/` 下。Forge 1.20.1 需 JDK 17，NeoForge 1.21.1 需 JDK 21。

## 开源协议

与女仆餐厅一致，采用 **GNU GPL v3**，详见 [LICENSE](LICENSE)。
