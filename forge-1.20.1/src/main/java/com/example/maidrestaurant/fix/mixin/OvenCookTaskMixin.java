package com.example.maidrestaurant.fix.mixin;

import com.renyigesai.bakeries.recipe.oven.OvenRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复 1.20.1 女仆餐厅本体（0.2.9）与烘焙坊（bakeries）版本升级后的 API 不匹配：
 *
 * 本体 OvenCookTask.cookTick 调用 OvenRecipe.getPerfect_temperature()（旧 bakeries API），而
 * bakeries 1.3.3 已把该 getter 改名为 getPerfectTemperature()（继承自 AbstractOvenRecipe），
 * 导致 NoSuchMethodError、温度设置整行没执行，女仆做法棍等烤箱食谱时不会自动调到所需温度。
 *
 * 用 @Redirect 把那处崩溃的调用替换成反射安全取温：
 *   优先 getPerfectTemperature()（bakeries 1.3.3 及以后），失败回退 getPerfect_temperature()（旧版），
 *   再失败返回 0，绝不抛异常。兼容任意 bakeries 版本。
 *
 * 关键约束：@Redirect 拦截实例方法调用时，handler 第一个参数类型必须精确等于被拦截调用的
 * owner 类型（即 OvenRecipe），否则 Mixin 在应用阶段会抛 InvalidInjectionException 导致本体崩溃。
 * 因此本 fix 工程以 compileOnly 方式引入 bakeries-1.20.1-forge-1.3.3.jar 以拿到该类型。
 */
@Mixin(targets = "com.mastermarisa.maid_restaurant.compat.bakeries.OvenCookTask")
public abstract class OvenCookTaskMixin {

    private static final Map<Class<?>, Method> TEMP_GETTER_CACHE = new ConcurrentHashMap<>();

    @Redirect(
            method = "cookTick",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/renyigesai/bakeries/recipe/oven/OvenRecipe;getPerfect_temperature()I"
            )
    )
    private int maidrestaurant_fix$getPerfectTemperature(OvenRecipe recipe) {
        return getPerfectTemperatureSafe(recipe);
    }

    private static int getPerfectTemperatureSafe(OvenRecipe recipe) {
        if (recipe == null) return 0;
        Class<?> clazz = recipe.getClass();
        try {
            Method m = TEMP_GETTER_CACHE.computeIfAbsent(clazz, c -> {
                try {
                    return c.getMethod("getPerfectTemperature");
                } catch (NoSuchMethodException e) {
                    try {
                        return c.getMethod("getPerfect_temperature");
                    } catch (NoSuchMethodException e2) {
                        return null;
                    }
                }
            });
            if (m == null) return 0;
            return (int) m.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }
}
