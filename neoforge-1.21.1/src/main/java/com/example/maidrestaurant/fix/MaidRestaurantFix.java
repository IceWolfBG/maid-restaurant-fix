package com.example.maidrestaurant.fix;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 女仆餐厅：修复（Maid Restaurant: Fix）— NeoForge 1.21.1
 *
 * 修复内容：
 * 1. 汤锅烹饪异常保护：配方不存在 / 空 carrier（水煎包）时的崩溃修复与手动取产品
 * 2. cookTick 异常保护：防止烹饪异常导致女仆 AI 状态崩溃、请求卡死
 * 3. getCurrentInput 崩溃兜底：从汤锅实体取材料与汤底
 * 4. 同产物多配方替代搜索（如煎蛋可用鸡蛋 / 海龟蛋）
 * 5. 点餐菜单去重 + 可选 JEC 拼音搜索
 */
@Mod(MaidRestaurantFix.MOD_ID)
public class MaidRestaurantFix {
    public static final String MOD_ID = "maid_restaurant_fix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public MaidRestaurantFix(IEventBus modEventBus) {
        LOGGER.info("Maid Restaurant: Fix loaded (NeoForge 1.21.1)");
    }
}
