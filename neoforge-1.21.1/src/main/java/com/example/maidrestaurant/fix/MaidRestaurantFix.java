package com.example.maidrestaurant.fix;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆餐厅：修复（Maid Restaurant: Fix）— NeoForge 1.21.1
 *
 * 修复内容：
 * 1. 汤锅烹饪异常保护：配方不存在 / 空 carrier（水煎包）时的崩溃修复与手动取产品
 * 2. cookTick 异常保护：防止烹饪异常导致女仆 AI 状态崩溃、请求卡死
 * 3. getCurrentInput 崩溃兜底：从汤锅实体取材料与汤底
 * 4. 同产物多配方替代搜索（如煎蛋可用鸡蛋 / 海龟蛋）
 * 5. 点餐菜单去重 + 可选 JEC 拼音搜索
 * 6. 重进游戏 / 重新加载区块后丢失烹饪进度：在 MaidCookingTask.start 持久化厨具坐标，
 *    并在世界加载后前若干 tick 由 CookingProgressReconciler 重建占用与瞬态记忆。
 */
@Mod(MaidRestaurantFix.MOD_ID)
public class MaidRestaurantFix {
    public static final String MOD_ID = "maid_restaurant_fix";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // 世界加载后，前若干 tick 持续触发烹饪进度对账，等待女仆与区块加载完成
    private static final Map<ServerLevel, Integer> PENDING = new ConcurrentHashMap<>();

    public MaidRestaurantFix(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Maid Restaurant: Fix loaded (NeoForge 1.21.1)");
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            PENDING.put(serverLevel, 12);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
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
