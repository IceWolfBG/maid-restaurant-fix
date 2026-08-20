package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

/**
 * 编译用 stub 类，仅用于编译期类型引用。
 * 运行时使用真实的 EntityMaid 类，此 stub 不打包到 jar 中。
 */
public abstract class EntityMaid extends Mob {
    protected EntityMaid(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    /**
     * 获取女仆可用物品栏。
     */
    public abstract IItemHandler getAvailableInv(boolean includeBackpack);
}
