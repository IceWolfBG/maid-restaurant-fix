package com.example.maidrestaurant.fix;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 女仆餐厅：修复（Maid Restaurant: Fix）
 *
 * 修复内容：
 * 1. 汤锅烹饪异常保护：防止配方不存在或汤底缺失导致的崩溃
 * 2. 女仆AI状态保护：防止烹饪异常导致女仆"原地消失"
 * 3. 请求清理保护：防止异常导致请求未正确清理，任务卡在已完成菜品
 */
@Mod(MaidRestaurantFix.MOD_ID)
public class MaidRestaurantFix {
    public static final String MOD_ID = "maid_restaurant_fix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public MaidRestaurantFix() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Maid Restaurant: Fix loaded");
    }
}
