package com.example.stepcloner;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 核心规则：判断"玩家这一步该不该拿到脚下方块"，以及拿到什么。
 * <p>
 * 单独抽出来是为了让命令自检和 gametest 能直接调用同一套判断，而不是各写一遍。
 */
public final class StepClonerRules {

    /** 每个玩家上一次触发复制的坐标——换格子才触发，站着不动不会刷。 */
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();

    /** 每个玩家上一次触发的游戏刻，用于冷却。 */
    private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

    /** 每个玩家累计复制了多少个方块（给命令看）。 */
    private static final Map<UUID, Integer> COUNTER = new HashMap<>();

    private StepClonerRules() {
    }

    /**
     * 每个 tick 对每个在线玩家调用一次。
     */
    public static void tickPlayer(ServerPlayerEntity player) {
        World world = player.getWorld();
        long now = world.getTime();
        UUID id = player.getUuid();

        BlockPos below = blockUnderneath(player);
        if (below == null) {
            // 悬空（脚下 3 格内都是空气）——清掉记录，下次落地算"新格子"
            LAST_POS.remove(id);
            return;
        }

        BlockPos last = LAST_POS.get(id);
        if (below.equals(last)) {
            return; // 还是同一格，没走新格子
        }

        Long lastTick = LAST_TICK.get(id);
        if (lastTick != null && now - lastTick < StepClonerConfig.cooldownTicks) {
            // 冷却中：只更新位置，不补发，避免冷却结束后一次性涌出来
            LAST_POS.put(id, below);
            return;
        }

        // 创造模式默认同样生效（想关掉就设 allow_creative=false）
        if (!StepClonerConfig.allowCreative && player.isCreative()) {
            LAST_POS.put(id, below);
            return;
        }

        ItemStack reward = rewardFor(world, below);
        LAST_POS.put(id, below);
        if (reward.isEmpty()) {
            return; // 这一格不该给东西（空气 / 无物品形式 / 在黑名单里）
        }

        player.getInventory().offerOrDrop(reward); // 背包满了就掉在脚边
        LAST_TICK.put(id, now);
        COUNTER.merge(id, reward.getCount(), Integer::sum);
    }

    /**
     * 取玩家"脚下"那一格：从玩家脚部所在坐标往下找第一个非空气方块，最多找 2 格。
     * 往下多找一点是为了兼容台阶、雪层、地毯这类脚下有一层薄方块的情况。
     */
    public static BlockPos blockUnderneath(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos start = player.getBlockPos();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos pos = start.down(dy);
            if (!world.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 某个坐标的方块该复制出什么物品。返回空堆表示"这一格不复制"。
     */
    public static ItemStack rewardFor(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return ItemStack.EMPTY;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            // 没有对应物品的方块：水、岩浆、火、传送门之类
            return ItemStack.EMPTY;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (StepClonerConfig.isBlacklisted(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, StepClonerConfig.countPerStep);
    }

    /** 这个方块会不会被复制（供自检与测试使用，不关心数量）。 */
    public static boolean wouldClone(World world, BlockPos pos) {
        return !rewardFor(world, pos).isEmpty();
    }

    public static BlockPos lastPos(ServerPlayerEntity player) {
        return LAST_POS.get(player.getUuid());
    }

    public static int counterOf(ServerPlayerEntity player) {
        return COUNTER.getOrDefault(player.getUuid(), 0);
    }

    public static void forget(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        LAST_POS.remove(id);
        LAST_TICK.remove(id);
        COUNTER.remove(id);
    }

    /** 仅供测试：清空全部状态。 */
    public static void clearAll() {
        LAST_POS.clear();
        LAST_TICK.clear();
        COUNTER.clear();
    }
}
