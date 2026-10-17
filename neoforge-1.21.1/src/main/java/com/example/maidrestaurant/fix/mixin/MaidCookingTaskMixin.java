package com.example.maidrestaurant.fix.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidCookingTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复女仆烹饪任务的两类问题：
 *
 * 1) 烹饪异常保护：cookTick() 抛异常时不再传播到行为系统，避免女仆 AI 状态崩溃、请求卡死。
 *    日志按「女仆 + 配方」限流（5 秒最多报一次），避免稳定复现的异常（如版本 API 不匹配）
 *    每 tick 刷屏——真问题应由针对性修复消除，此处只是兜底。
 *
 * 2) 重进游戏 / 重载区块丢失烹饪进度：在独占占用提交点（start，与 BlockUsageManager.addUser 同一时刻）
 *    把 cookPos + chairPos 写入 CookRequest.extraData 持久化。关联只在「占用成功」后才写，因此多任务
 *    并发不会错关联（输家 start 跑不到，被 check 的占用校验弹回）。重载后由 CookingProgressReconciler
 *    重建占用并回写瞬态记忆。这里刻意不在 stop 清理，否则关机存档前若触发 stop 会把要持久化的数据清掉。
 */
@Mixin(MaidCookingTask.class)
public abstract class MaidCookingTaskMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");
    private static final long LOG_THROTTLE_MS = 5000L;
    private static final Map<String, Long> LAST_LOG_TIME = new ConcurrentHashMap<>();

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mastermarisa/maid_restaurant/api/ICookTask;cookTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/core/BlockPos;Lcom/mastermarisa/maid_restaurant/request/CookRequest;)V"
            )
    )
    private void maidrestaurant_fix$safeCookTick(ICookTask instance, ServerLevel level, EntityMaid maid, BlockPos pos, CookRequest request) {
        try {
            instance.cookTick(level, maid, pos, request);
        } catch (Throwable t) {
            String key = maid.getUUID().toString() + ":" + (request != null ? request.id : "null");
            long now = System.currentTimeMillis();
            Long last = LAST_LOG_TIME.get(key);
            if (last == null || now - last >= LOG_THROTTLE_MS) {
                LAST_LOG_TIME.put(key, now);
                LOGGER.error("CookTick crashed for recipe {} ({}), prevented maid AI corruption",
                        request != null ? request.id : "null",
                        request != null ? request.type : "null", t);
            }
        }
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void maidrestaurant_fix$persistCookPos(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (BehaviorUtils.getTargetType(maid) != 2) return;
        maid.getBrain().getMemory(ModEntities.CACHED_WORK_BLOCK.get()).ifPresent((PositionTracker tracker) -> {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request == null) return;
            request.extraData.putLong("cookPos", tracker.currentBlockPosition().asLong());
            maid.getBrain().getMemory(ModEntities.CHAIR_POS.get()).ifPresent((PositionTracker chair) ->
                    request.extraData.putLong("chairPos", chair.currentBlockPosition().asLong()));
        });
    }
}
