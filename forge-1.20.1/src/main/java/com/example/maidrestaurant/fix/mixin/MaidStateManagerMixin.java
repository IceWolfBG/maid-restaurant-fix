package com.example.maidrestaurant.fix.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.api.recipe.soupbase.ISoupBase;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.utils.MaidStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 修复 MaidStateManager.cookState() 中 getCurrentInput() 可能抛出的异常。
 *
 * 根因：StockpotCookTask.getCurrentInput() 在配方 carrier 为空（如水煎包）时，
 * 访问 carrier().getItems()[0] 抛出 ArrayIndexOutOfBoundsException。
 * 原 @Redirect 只返回空列表，导致 cookState 认为锅中无材料，女仆进入 STORAGE 状态而非 COOK 状态。
 *
 * 修复：当 getCurrentInput 崩溃时，手动从 StockpotBlockEntity 获取锅中实际物品
 * （inputs + soupBase），确保 cookState 能正确判断女仆已有材料。
 *
 * 不使用 @Overwrite，避免 EntityMaid 方法签名不匹配的问题。
 */
@Mixin(MaidStateManager.class)
public abstract class MaidStateManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");

    @Redirect(
            method = "cookState",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mastermarisa/maid_restaurant/api/ICookTask;getCurrentInput(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/util/List;"
            )
    )
    private static List<ItemStack> maidrestaurant_fix$safeGetCurrentInput(ICookTask instance, Level level, BlockPos pos, EntityMaid maid) {
        try {
            return instance.getCurrentInput(level, pos, maid);
        } catch (Throwable t) {
            // getCurrentInput 崩溃（通常是 carrier 为空导致的数组越界）
            // 手动从锅中获取实际物品，确保 cookState 能正确判断
            List<ItemStack> fallback = new ArrayList<>();
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof StockpotBlockEntity pot) {
                    // 获取锅中已放入的输入物品
                    pot.getInputs().stream()
                            .filter(s -> !s.isEmpty())
                            .forEach(fallback::add);
                    // 获取汤底
                    ISoupBase soupBase = pot.getSoupBase();
                    if (soupBase != null) {
                        fallback.add(soupBase.getDisplayStack());
                    }
                }
            } catch (Throwable fallbackError) {
                LOGGER.warn("cookState: fallback getCurrentInput also failed for {} at {}",
                        instance.getClass().getSimpleName(), pos, fallbackError);
            }
            LOGGER.warn("cookState: getCurrentInput crashed for {} at {}, using {} fallback items (recipe carrier may be empty)",
                    instance.getClass().getSimpleName(), pos, fallback.size());
            return fallback;
        }
    }
}
