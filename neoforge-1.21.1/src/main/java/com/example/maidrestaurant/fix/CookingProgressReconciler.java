package com.example.maidrestaurant.fix;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.config.RestaurantConfig;
import com.mastermarisa.maid_restaurant.data.TagBlock;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mastermarisa.maid_restaurant.utils.BlockUsageManager;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import com.mastermarisa.maid_restaurant.utils.DirectionUtils;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 修复「重进游戏 / 重新加载区块后女仆丢失烹饪进度」的通用对账器。
 *
 * 根因：CookRequest 会随世界存档持久化，但女仆定位厨具靠的是瞬态脑记忆
 * （CACHED_WORK_BLOCK / TARGET_POS / CHAIR_POS / TARGET_TYPE=2）与 BlockUsageManager 占用，
 * 这两者重载即丢，导致 MaidStateManager.cookState 误判为 STORAGE、女仆不再回来取成品。
 *
 * 方案（绑定占用提交 + 重载对账）：
 * - 占用提交点（MaidCookingTask.start，与 BlockUsageManager.addUser 同一时刻）把 cookPos+chairPos
 *   写入 CookRequest.extraData，随请求持久化。关联只在「独占占用成功」后才写，因此多任务并发
 *   天然不会错关联（输家 start 跑不到，被 check 的占用校验弹回）。
 * - 重载后本对账器重建占用并回写全部瞬态记忆，让 cookState 重新返回 COOK，女仆直接续上烹饪。
 * - 厨具已损坏 / 区块未加载 / 不再是合法厨具时，清掉 cookPos 让女仆自然重新搜索。
 *
 * 注意：不在 MaidCookingTask.stop 里清 cookPos，否则服务器关闭存档前若触发 stop 会把要持久化的
 * 数据清掉，反而回到原 bug。关联的生命周期由「start 覆盖写 + 重载校验清理」保证，与本体占用一致。
 */
public final class CookingProgressReconciler {
    private static final Logger LOGGER = LogManager.getLogger("MaidRestaurantFix");

    private static final String KEY_COOK = "cookPos";
    private static final String KEY_CHAIR = "chairPos";

    private CookingProgressReconciler() {}

    /**
     * 在服务器世界加载后的最初若干 tick 调用（事件侧负责重复调用几次以等待区块加载）。
     */
    public static void reconcile(ServerLevel level) {
        if (level == null) return;
        for (Entity e : level.getEntities().getAll()) {
            if (!(e instanceof EntityMaid maid)) continue;
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request == null) continue;
            if (!request.extraData.contains(KEY_COOK)) continue;

            BlockPos cookPos = BlockPos.of(request.extraData.getLong(KEY_COOK));
            BlockPos chairPos = request.extraData.contains(KEY_CHAIR)
                    ? BlockPos.of(request.extraData.getLong(KEY_CHAIR)) : null;

            // 区块还没加载：先跳过，等后续 tick 再试（不清理，避免误删有效关联）
            if (!level.isLoaded(cookPos) || (chairPos != null && !level.isLoaded(chairPos))) {
                continue;
            }
            // 厨具已失效（被挖/不再是合法工作方块）：清关联，交还给正常搜索流程
            if (chairPos == null || !isValidCookBlock(level, maid, cookPos, request)) {
                request.extraData.remove(KEY_COOK);
                request.extraData.remove(KEY_CHAIR);
                continue;
            }

            // 重建占用（重载后 BlockUsageManager 是空的，不重建别的女仆会来抢）
            BlockUsageManager.addUser(cookPos, maid.getUUID());

            // 回写瞬态记忆，与本体 MaidCookingTask.start + MaidApproachCookBlockTask.accept 等价
            PositionTracker cookTracker = new BlockPosTracker(cookPos);
            maid.getBrain().setMemory(ModEntities.CACHED_WORK_BLOCK.get(), cookTracker);
            maid.getBrain().setMemory(ModEntities.TARGET_POS.get(), cookTracker);
            maid.getBrain().setMemory(ModEntities.CHAIR_POS.get(), new BlockPosTracker(chairPos));
            maid.getBrain().setMemory(ModEntities.TARGET_TYPE.get(), 2);
            BehaviorUtils.setLookTargetMemory(maid, cookPos);

            // 复刻 accept()：坐下烹饪 or 传送到椅子
            if (RestaurantConfig.SIT_WHILE_COOKING()) {
                BlockState state = level.getBlockState(chairPos);
                if (state.is(TagBlock.SIT_BLOCK)) {
                    Direction dir = DirectionUtils.getHorizontalDirection(
                            (double) (cookPos.getX() - chairPos.getX()),
                            (double) (cookPos.getZ() - chairPos.getZ()));
                    BehaviorUtils.startRide(level, maid, chairPos, dir);
                }
            } else {
                Vec3 chairV = Vec3.atBottomCenterOf(chairPos).relative(
                        DirectionUtils.getHorizontalDirection(
                                cookPos.getX() - chairPos.getX(),
                                cookPos.getZ() - chairPos.getZ()), 0.2F);
                maid.teleportTo(chairV.x, chairV.y, chairV.z);
                maid.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private static boolean isValidCookBlock(ServerLevel level, EntityMaid maid, BlockPos pos, CookRequest request) {
        ICookTask iCookTask = CookTasks.getTask(request.type);
        return iCookTask.isValidWorkBlock(level, maid, pos);
    }
}
