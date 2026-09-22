package com.example.maidrestaurant.fix.mixin;

import com.mastermarisa.maid_restaurant.client.gui.screen.ordering.OrderingScreen;
import com.mastermarisa.maid_restaurant.utils.component.RecipeData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 菜单增强：
 * 1. 拼音搜索：软依赖 JustEnoughCharacters(JEC)，装了 JEC 即可用全拼/声母组合搜索中文菜名；
 *    没装 JEC 时自动降级为原文匹配，行为与原版完全一致。
 * 2. 菜单去重：将同一物品的不同数量配方（如 *1、*2、*3...）统一为 *1、*4、*9，减少菜单冗余。
 */
@Mixin(OrderingScreen.class)
public abstract class OrderingScreenMixin {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");
    private static final int[] PREFERRED_COUNTS = {1, 4, 9};

    // ===== 软依赖 JEC 拼音匹配（纯反射，不强制安装、不打包字库）=====
    private static volatile Method jecContainsMethod;
    private static volatile boolean jecLookupDone = false;
    private static volatile boolean jecAvailable = false;

    private static boolean jecContains(String displayName, String query) {
        try {
            if (!jecLookupDone) {
                synchronized (OrderingScreenMixin.class) {
                    if (!jecLookupDone) {
                        try {
                            Class<?> cls = Class.forName("me.towdium.jecharacters.utils.Match");
                            jecContainsMethod = cls.getMethod("contains", String.class, CharSequence.class);
                            jecAvailable = true;
                            LOGGER.info("Pinyin search: JustEnoughCharacters detected, pinyin search enabled.");
                        } catch (Throwable t) {
                            jecAvailable = false;
                            LOGGER.info("Pinyin search: JustEnoughCharacters not installed, falling back to plain text match.");
                        }
                        jecLookupDone = true;
                    }
                }
            }
            if (!jecAvailable || jecContainsMethod == null) return false;
            Object res = jecContainsMethod.invoke(null, displayName, query);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean matchesQuery(RecipeData data, String lowerQuery) {
        String displayName = data.result.getHoverName().getString();
        String registerName = data.result.getDescriptionId();
        if (displayName.toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (registerName != null && registerName.toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        return jecContains(displayName, lowerQuery);
    }

    @Inject(method = "filter", at = @At("HEAD"), cancellable = true)
    private void maidrestaurant_fix$onFilterHead(List<RecipeData> input, String filterText, CallbackInfoReturnable<List<RecipeData>> cir) {
        try {
            if (input == null || input.isEmpty()) return;

            if (filterText == null || filterText.isEmpty()) {
                cir.setReturnValue(dedup(input));
                return;
            }

            String lowerQuery = filterText.toLowerCase(Locale.ROOT);

            List<RecipeData> matched = new ArrayList<>();
            for (RecipeData data : input) {
                if (data == null || data.result == null || data.result.isEmpty()) continue;
                if (matchesQuery(data, lowerQuery)) {
                    matched.add(data);
                }
            }

            cir.setReturnValue(dedup(matched));
        } catch (Throwable t) {
            LOGGER.error("Menu filter error", t);
            // 出错时不 cancel，让原方法走原始逻辑
        }
    }

    private static List<RecipeData> dedup(List<RecipeData> original) {
        if (original == null || original.isEmpty()) return original;

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
                result.addAll(group);
                continue;
            }

            Map<Integer, RecipeData> byCount = new HashMap<>();
            for (RecipeData data : group) {
                int count = data.result.getCount();
                byCount.putIfAbsent(count, data);
            }

            boolean addedAny = false;
            for (int pref : PREFERRED_COUNTS) {
                RecipeData data = byCount.get(pref);
                if (data != null) {
                    result.add(data);
                    addedAny = true;
                }
            }

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

        List<RecipeData> orderedResult = new ArrayList<>();
        for (RecipeData data : original) {
            if (result.contains(data) && !orderedResult.contains(data)) {
                orderedResult.add(data);
            }
        }
        return orderedResult;
    }
}
