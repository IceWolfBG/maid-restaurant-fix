package com.example.maidrestaurant.fix;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆餐厅：修复（Maid Restaurant: Fix）
 *
 * 修复内容：
 * 1. 汤锅烹饪异常保护：防止配方不存在或汤底缺失导致的崩溃
 * 2. 女仆AI状态保护：防止烹饪异常导致女仆"原地消失"
 * 3. 请求清理保护：防止异常导致请求未正确清理，任务卡在已完成菜品
 * 4. 重进游戏 / 重新加载区块后丢失烹饪进度：在 MaidCookingTask.start 持久化厨具坐标，
 *    并在世界加载后前若干 tick 由 CookingProgressReconciler 重建占用与瞬态记忆（见下方事件）。
 */
@Mod(MaidRestaurantFix.MOD_ID)
public class MaidRestaurantFix {
    public static final String MOD_ID = "maid_restaurant_fix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // 世界加载后，前若干 tick 持续触发烹饪进度对账，等待女仆与区块加载完成
    private static final Map<ServerLevel, Integer> PENDING = new ConcurrentHashMap<>();

    public MaidRestaurantFix() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Maid Restaurant: Fix loaded");
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            PENDING.put(serverLevel, 12);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<ServerLevel, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerLevel, Integer> e = it.next();
            CookingProgressReconciler.reconcile(e.getKey());
            int n = e.getValue() - 1;
            if (n <= 0) it.remove();
            else e.setValue(n);
        }
    }
}
