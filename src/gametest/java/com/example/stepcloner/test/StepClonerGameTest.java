package com.example.stepcloner.test;

import com.example.stepcloner.StepClonerConfig;
import com.example.stepcloner.StepClonerRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * 自动化测试：在无头服务端里真的让"玩家"站到方块上，检查有没有复制进背包。
 * 运行：./gradlew runGameTest
 */
public class StepClonerGameTest implements FabricGameTest {

    // ---------------------------------------------------------- 基本行为

    /** 站在石头上就该拿到一个石头 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void standingOnBlockCopiesIt(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        standOn(context, player, Blocks.STONE.getDefaultState());

        StepClonerRules.tickPlayer(player);

        context.assertTrue(countOf(player, Items.STONE) == 1,
                "站在石头上应当复制到 1 个石头，实际 " + countOf(player, Items.STONE));
        context.complete();
    }

    /** 同一个格子站着不动，不能反复刷 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void sameBlockDoesNotRepeat(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        standOn(context, player, Blocks.STONE.getDefaultState());

        for (int i = 0; i < 20; i++) {
            StepClonerRules.tickPlayer(player);
        }

        context.assertTrue(countOf(player, Items.STONE) == 1,
                "原地站着不该重复复制，实际拿到 " + countOf(player, Items.STONE) + " 个");
        context.complete();
    }

    /** 走到新的一格，应再复制一次 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void walkingToNewBlockCopiesAgain(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();

        // 两块相邻的石头
        BlockPos first = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos second = context.getAbsolutePos(new BlockPos(1, 1, 0));
        world.setBlockState(first, Blocks.STONE.getDefaultState());
        world.setBlockState(second, Blocks.DIRT.getDefaultState());

        standOnPos(context, player, first);
        StepClonerRules.tickPlayer(player);
        standOnPos(context, player, second);
        StepClonerRules.tickPlayer(player);

        context.assertTrue(countOf(player, Items.STONE) == 1,
                "第一格应当复制 1 个石头，实际 " + countOf(player, Items.STONE));
        context.assertTrue(countOf(player, Items.DIRT) == 1,
                "第二格应当复制 1 个泥土，实际 " + countOf(player, Items.DIRT));
        context.complete();
    }

    /** 别的方块同样按它自己的物品形式给（比如钻石矿给钻石矿，而不是钻石） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blockIsCopiedAsItsOwnItem(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        standOn(context, player, Blocks.DIAMOND_ORE.getDefaultState());

        StepClonerRules.tickPlayer(player);

        context.assertTrue(countOf(player, Items.DIAMOND_ORE) == 1,
                "钻石矿应当以钻石矿的形式复制，实际 " + countOf(player, Items.DIAMOND_ORE)
                        + "，钻石 " + countOf(player, Items.DIAMOND));
        context.complete();
    }

    // ---------------------------------------------------------- 不该复制的情况

    /** 悬空时脚下没有方块，什么都不给 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void airGivesNothing(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        BlockPos high = context.getAbsolutePos(new BlockPos(0, 10, 0));
        player.teleport(world, high.getX() + 0.5, high.getY(), high.getZ() + 0.5, 0.0F, 0.0F);

        int before = totalItems(player);
        StepClonerRules.tickPlayer(player);

        context.assertTrue(totalItems(player) == before,
                "悬空时不该拿到任何东西，实际多了 " + (totalItems(player) - before) + " 个");
        context.complete();
    }

    /** 没有物品形式的方块（水）不给东西 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blocksWithoutItemFormGiveNothing(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 1, 0));
        world.setBlockState(pos, Blocks.WATER.getDefaultState());
        standOnPos(context, player, pos);

        int before = totalItems(player);
        StepClonerRules.tickPlayer(player);

        context.assertTrue(totalItems(player) == before,
                "水没有物品形式，不该给东西，实际多了 " + (totalItems(player) - before) + " 个");
        context.complete();
    }

    /** 黑名单里的方块不给（默认黑名单里有屏障） */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blacklistedBlockGivesNothing(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 1, 0));
        world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
        standOnPos(context, player, pos);

        int before = totalItems(player);
        StepClonerRules.tickPlayer(player);

        context.assertTrue(totalItems(player) == before,
                "屏障在黑名单里，不该给东西，实际多了 " + (totalItems(player) - before) + " 个");
        context.complete();
    }

    // ---------------------------------------------------------- 配置生效

    /** 关掉"创造模式生效"后，创造模式玩家不该拿到东西 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void creativeCanBeExcludedByConfig(TestContext context) {
        StepClonerRules.clearAll();
        useTestSettings();
        StepClonerConfig.allowCreative = false;
        try {
            ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
            standOn(context, player, Blocks.STONE.getDefaultState());

            int before = totalItems(player);
            StepClonerRules.tickPlayer(player);

            context.assertTrue(totalItems(player) == before,
                    "allow_creative=false 时创造模式不该拿到东西，实际多了 "
                            + (totalItems(player) - before) + " 个");

            // 还原，避免影响其它测试
            StepClonerConfig.allowCreative = true;
            StepClonerRules.clearAll();
            standOn(context, player, Blocks.STONE.getDefaultState());
            StepClonerRules.tickPlayer(player);
            context.assertTrue(countOf(player, Items.STONE) == 1,
                    "恢复 allow_creative=true 后应当又能复制，实际 " + countOf(player, Items.STONE));
        } finally {
            StepClonerConfig.allowCreative = true;
        }
        context.complete();
    }

    // ---------------------------------------------------------- 辅助方法

    /** 把测试期间的行为固定下来，不受玩家本地配置文件影响。 */
    private static void useTestSettings() {
        StepClonerConfig.enabled = true;
        StepClonerConfig.allowCreative = true;
        StepClonerConfig.cooldownTicks = 0;
        StepClonerConfig.countPerStep = 1;
    }

    /** 在玩家脚下的位置放一个方块并把他放上去。 */
    private static void standOn(TestContext context, ServerPlayerEntity player,
                                net.minecraft.block.BlockState state) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(0, 1, 0));
        context.getWorld().setBlockState(pos, state);
        standOnPos(context, player, pos);
    }

    /** 把玩家放到指定（绝对）坐标方块的上面。 */
    private static void standOnPos(TestContext context, ServerPlayerEntity player, BlockPos pos) {
        player.teleport(context.getWorld(),
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0F, 0.0F);
    }

    private static int countOf(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int totalItems(ServerPlayerEntity player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            total += player.getInventory().getStack(i).getCount();
        }
        return total;
    }
}
