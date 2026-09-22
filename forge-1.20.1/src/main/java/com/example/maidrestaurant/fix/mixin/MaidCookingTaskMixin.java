package com.example.maidrestaurant.fix.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidCookingTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复女仆烹饪任务的异常处理问题。
 * 原代码中 cookTick() 抛出的异常会直接传播到行为系统，
 * 导致女仆AI状态崩溃和请求未正确清理。
 */
@Mixin(MaidCookingTask.class)
public abstract class MaidCookingTaskMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");

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
            LOGGER.error("CookTick crashed for recipe {} ({}), prevented maid AI corruption",
                    request != null ? request.id : "null",
                    request != null ? request.type : "null", t);
        }
    }
}
