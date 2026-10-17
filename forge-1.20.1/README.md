# 女仆餐厅：修复 (Maid Restaurant: Fix)

女仆餐厅（Maid Restaurant）的修复附属模组，解决原版女仆餐厅的若干问题。

## 修复内容

### 1. 水煎包崩溃修复
修复女仆使用汤锅烹饪水煎包等不需要容器的食物时导致游戏崩溃的问题。

崩溃原因：女仆尝试用容器拿取不需要容器的食物（如水煎包），导致空指针异常。

修复方式：通过 Mixin 重定向 StockpotCookTask 中的物品获取逻辑，对不需要容器的食物直接获取，不使用容器。

### 2. 多配方支持
女仆烹饪时不再只使用单一配方，而是检索所有可用配方，选择材料最匹配的配方进行烹饪。

### 3. 菜单去重
女仆餐厅菜单中重复的点单项（如熟兔肉*1、熟兔肉*2、熟兔肉*3...）进行合并，只保留 *1、*4、*9 三种数量，减少菜单冗余。

### 4. 点餐菜单拼音搜索（可选）
安装 [Just Enough Characters (JEC)](https://www.curseforge.com/minecraft/mc-mods/just-enough-characters) 后，女仆餐厅点餐菜单的搜索框支持汉语拼音搜索（如输入 "zr" 或 "zhurou" 可匹配"猪肉/熟猪排"等）。本模组**软依赖** JEC，不打包字库；未安装 JEC 时自动回退为原版的原文（中文/英文）匹配，不影响使用。

## 依赖

- Minecraft 1.20.1
- Forge 47.2.0+
- 车万女仆 (Touhou Little Maid) 1.5.3+
- 女仆餐厅 (Maid Restaurant) 0.2.9+
- （可选）森罗物语：厨房 (Kaleidoscope Cookery)
- （可选）Just Enough Characters (JEC)：提供点餐菜单拼音搜索

## 许可

GPL-3.0
