package com.example.maidrestaurant.fix.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidGetFromStorageTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1. getCurrentInput 空/异常保护。
 * 2. 同一产物存在多个配方时（如煎蛋可用鸡蛋或海龟蛋），原配方材料不足则自动尝试替代配方。
 *
 * 1.21.1 注意：RecipeManager.byKey / getAllRecipesFor 均返回 RecipeHolder，
 * 取配方用 holder.value()，取 ID 用 holder.id()。
 */
@Mixin(MaidGetFromStorageTask.class)
public abstract class MaidGetFromStorageTaskMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");
    private static final ThreadLocal<Boolean> isSearchingAlternative = ThreadLocal.withInitial(() -> false);
    private static final int MAX_ALTERNATIVE_RECIPES = 3;

    @Shadow
    protected abstract boolean search(ServerLevel level, EntityMaid maid);

    @Redirect(
            method = {"containsRequired", "accept"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mastermarisa/maid_restaurant/api/ICookTask;getCurrentInput(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/util/List;"
            )
    )
    private List<ItemStack> maidrestaurant_fix$safeGetCurrentInput(ICookTask instance, Level level, BlockPos pos, EntityMaid maid) {
        try {
            List<ItemStack> result = instance.getCurrentInput(level, pos, maid);
            return result != null ? result : new ArrayList<>();
        } catch (Throwable t) {
            LOGGER.warn("MaidGetFromStorageTask.getCurrentInput crashed for {} at {}, returning empty list",
                    instance.getClass().getSimpleName(), pos, t);
            return new ArrayList<>();
        }
    }

    @Inject(method = "search", at = @At("RETURN"), cancellable = true)
    private void maidrestaurant_fix$onSearchReturn(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (isSearchingAlternative.get()) return;

        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, 0);
            if (request == null || request.id == null) return;

            ICookTask cookTask = CookTasks.getTask(request.type);
            if (cookTask == null) return;

            RecipeHolder<?> currentHolder = level.getRecipeManager().byKey(request.id).orElse(null);
            if (currentHolder == null) return;
            ItemStack currentResult = currentHolder.value().getResultItem(level.registryAccess());
            if (currentResult == null || currentResult.isEmpty()) return;

            @SuppressWarnings({"unchecked", "rawtypes"})
            Collection<RecipeHolder<?>> allRecipes =
                    (Collection) level.getRecipeManager().getAllRecipesFor((RecipeType) cookTask.getType());

            List<ResourceLocation> alternatives = new ArrayList<>();
            for (RecipeHolder<?> holder : allRecipes) {
                if (holder.id().equals(request.id)) continue;
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (result != null && !result.isEmpty()
                        && result.getItem() == currentResult.getItem()
                        && result.getCount() == currentResult.getCount()) {
                    alternatives.add(holder.id());
                    if (alternatives.size() >= MAX_ALTERNATIVE_RECIPES) break;
                }
            }

            if (alternatives.isEmpty()) return;

            LOGGER.debug("Multi-recipe: found {} alternatives for {}, trying them", alternatives.size(), request.id);

            isSearchingAlternative.set(true);
            ResourceLocation originalId = request.id;
            try {
                for (ResourceLocation altId : alternatives) {
                    request.id = altId;
                    boolean found = search(level, maid);
                    if (found) {
                        LOGGER.info("Multi-recipe: switched from {} to {}", originalId, altId);
                        cir.setReturnValue(true);
                        return;
                    }
                }
                request.id = originalId;
            } finally {
                isSearchingAlternative.remove();
            }
        } catch (Throwable t) {
            LOGGER.error("Multi-recipe search error", t);
        }
    }
}
