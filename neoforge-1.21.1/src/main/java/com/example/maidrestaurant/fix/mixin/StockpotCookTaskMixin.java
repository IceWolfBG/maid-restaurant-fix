package com.example.maidrestaurant.fix.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.mastermarisa.maid_restaurant.cooktask.StockpotCookTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * 修复汤锅烹饪任务的问题（NeoForge 1.21.1）：
 * 1. cookTick HEAD 预检查：方块实体和配方不存在时取消，避免崩溃
 * 2. cookTick TAIL 补充处理：当配方 carrier 为空（如水煎包）时，
 *    原方法无法取出产品，在 TAIL 中手动取出。
 *    为了与原版行为一致（拿锅盖后等一个 tick 再取产品），
 *    如果本 tick 刚开始时有锅盖，说明原方法刚拿了锅盖，本 tick 不取出，等下一个 tick。
 *
 * 1.21.1 注意：RecipeManager.byKey 返回 Optional<RecipeHolder<?>>，需 holder.value() 取配方。
 */
@Mixin(StockpotCookTask.class)
public abstract class StockpotCookTaskMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");

    private boolean maidrestaurant_fix$hadLidAtHead = false;

    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_fix$onCookTickHead(ServerLevel level, EntityMaid maid, BlockPos pos, CookRequest request, CallbackInfo ci) {
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                ci.cancel();
                return;
            }
            if (request != null && request.id != null) {
                if (level.getRecipeManager().byKey(request.id).isEmpty()) {
                    ci.cancel();
                    return;
                }
            }
            if (be instanceof StockpotBlockEntity pot) {
                maidrestaurant_fix$hadLidAtHead = pot.hasLid();
            }
        } catch (Throwable t) {
            LOGGER.error("Stockpot cookTick pre-check error", t);
            ci.cancel();
        }
    }

    @Inject(method = "cookTick", at = @At("TAIL"))
    private void maidrestaurant_fix$onCookTickTail(ServerLevel level, EntityMaid maid, BlockPos pos, CookRequest request, CallbackInfo ci) {
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof StockpotBlockEntity pot)) return;
            if (pot.getStatus() != 3 || pot.hasLid() || pot.getTakeoutCount() <= 0) return;
            if (maidrestaurant_fix$hadLidAtHead) return;

            Optional<? extends RecipeHolder<?>> holderOpt = level.getRecipeManager().byKey(request.id);
            if (holderOpt.isEmpty()) return;
            if (!(holderOpt.get().value() instanceof StockpotRecipe recipe)) return;

            Ingredient carrier = recipe.carrier();
            if (carrier == null || !carrier.isEmpty()) return; // 只处理空 carrier 的情况

            boolean success = pot.takeOutProduct(level, maid, ItemStack.EMPTY);
            if (success) {
                ((LivingEntity) maid).swing(InteractionHand.OFF_HAND);
                if (pot.getTakeoutCount() == 0) {
                    request.remain--;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Stockpot cookTick tail error", t);
        }
    }
}
