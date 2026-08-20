package com.example.maidrestaurant.fix.mixin;

import com.mastermarisa.maid_restaurant.client.gui.screen.ordering.OrderingScreen;
import com.mastermarisa.maid_restaurant.utils.component.RecipeData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单去重：将同一物品的不同数量配方（如熟兔肉*1、*2、*3...）
 * 统一为只保留 *1、*4、*9 三种，减少菜单冗余。
 */
@Mixin(OrderingScreen.class)
public abstract class OrderingScreenMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");
    private static final int[] PREFERRED_COUNTS = {1, 4, 9};

    @Inject(method = "filter", at = @At("RETURN"), cancellable = true)
    private void maidrestaurant_fix$onFilterReturn(List<RecipeData> input, String filterText, CallbackInfoReturnable<List<RecipeData>> cir) {
        try {
            List<RecipeData> original = cir.getReturnValue();
            if (original == null || original.isEmpty()) return;

            // 按物品分组
            Map<String, List<RecipeData>> groups = new HashMap<>();
            for (RecipeData data : original) {
                if (data == null || data.result == null || data.result.isEmpty()) continue;
                String key = data.result.getItem().toString();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(data);
            }

            List<RecipeData> result = new ArrayList<>();
            for (Map.Entry<String, List<RecipeData>> entry : groups.entrySet()) {
                List<RecipeData> group = entry.getValue();
                if (group.size() <= 1) {
                    // 只有一个配方，直接保留
                    result.addAll(group);
                    continue;
                }

                // 收集该组所有可用的数量
                Map<Integer, RecipeData> byCount = new HashMap<>();
                for (RecipeData data : group) {
                    int count = data.result.getCount();
                    byCount.putIfAbsent(count, data);
                }

                // 优先保留 1、4、9
                boolean addedAny = false;
                for (int pref : PREFERRED_COUNTS) {
                    RecipeData data = byCount.get(pref);
                    if (data != null) {
                        result.add(data);
                        addedAny = true;
                    }
                }

                // 如果没有任何首选数量，保留数量最少的和最多的
                if (!addedAny) {
                    int minCount = Integer.MAX_VALUE;
                    int maxCount = Integer.MIN_VALUE;
                    RecipeData minData = null;
                    RecipeData maxData = null;
                    for (RecipeData data : group) {
                        int count = data.result.getCount();
                        if (count < minCount) {
                            minCount = count;
                            minData = data;
                        }
                        if (count > maxCount) {
                            maxCount = count;
                            maxData = data;
                        }
                    }
                    if (minData != null) result.add(minData);
                    if (maxData != null && maxData != minData) result.add(maxData);
                }
            }

            // 保持原始顺序
            List<RecipeData> orderedResult = new ArrayList<>();
            for (RecipeData data : original) {
                if (result.contains(data) && !orderedResult.contains(data)) {
                    orderedResult.add(data);
                }
            }

            cir.setReturnValue(orderedResult);
        } catch (Throwable t) {
            LOGGER.error("Menu dedup error", t);
            // 出错时保持原列表不变
        }
    }
}
