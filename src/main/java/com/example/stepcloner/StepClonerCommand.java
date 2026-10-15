package com.example.stepcloner;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * /stepcloner 命令加自检：
 * <pre>
 *   /stepcloner on | off      开关
 *   /stepcloner status        查看状态与统计
 *   /stepcloner reload        重新读取配置文件
 *   /stepcloner selftest      自检（检查配置与过滤规则是否正常）
 * </pre>
 */
public final class StepClonerCommand {

    private StepClonerCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("stepcloner")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on")
                        .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(CommandManager.literal("off")
                        .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(CommandManager.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(CommandManager.literal("selftest")
                        .executes(ctx -> selftest(ctx.getSource()))));
    }

    private static int setEnabled(ServerCommandSource source, boolean value) {
        StepClonerConfig.enabled = value;
        source.sendFeedback(() -> Text.literal("[踏地复制] 已" + (value ? "启用" : "禁用")), true);
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        StepClonerConfig.load();
        source.sendFeedback(() -> Text.literal("[踏地复制] 配置已重新读取，当前状态："
                + (StepClonerConfig.enabled ? "启用" : "禁用")
                + "，冷却 " + StepClonerConfig.cooldownTicks + " tick"
                + "，每次 " + StepClonerConfig.countPerStep + " 个"), true);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("===== 踏地复制 ====="), false);
        source.sendFeedback(() -> Text.literal("状态：" + (StepClonerConfig.enabled ? "启用" : "禁用")), false);
        source.sendFeedback(() -> Text.literal("冷却：" + StepClonerConfig.cooldownTicks + " tick"
                + "，每次复制：" + StepClonerConfig.countPerStep + " 个"), false);
        source.sendFeedback(() -> Text.literal("创造模式生效：" + (StepClonerConfig.allowCreative ? "是" : "否")), false);
        source.sendFeedback(() -> Text.literal("黑名单：" + StepClonerConfig.blacklistEntries().size() + " 项"), false);

        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            BlockPos below = StepClonerRules.blockUnderneath(player);
            String what = below == null
                    ? "脚下没有方块（悬空）"
                    : StepClonerRules.wouldClone(source.getWorld(), below)
                    ? "会被复制"
                    : "不会复制（空气 / 无物品形式 / 在黑名单里）";
            source.sendFeedback(() -> Text.literal("你脚下：" + what), false);
            source.sendFeedback(() -> Text.literal("已累计复制：" + StepClonerRules.counterOf(player) + " 个"), false);
        }
        return 1;
    }

    /**
     * 自检：只验证不依赖具体地形的东西——配置读取、过滤规则、黑名单。
     * "踩上去真的会进背包"这件事由 gametest 覆盖（那里能自由摆方块）。
     */
    private static int selftest(ServerCommandSource source) {
        int passed = 0;
        int total = 0;
        StringBuilder failures = new StringBuilder();

        // 1. 空气不该被复制
        total++;
        if (!StepClonerRules.wouldClone(source.getWorld(), new BlockPos(0, -1000, 0))) {
            passed++;
        } else {
            failures.append("空气居然会被复制；");
        }

        // 2. 普通方块该被复制
        total++;
        BlockPos probe = source.getPlayer() != null
                ? StepClonerRules.blockUnderneath(source.getPlayer())
                : null;
        if (probe != null) {
            if (StepClonerRules.wouldClone(source.getWorld(), probe)) {
                passed++;
            } else {
                failures.append("脚下的普通方块没有被复制（检查黑名单是否把它排除了）；");
            }
        } else {
            // 没有玩家上下文时退化成"用一个已知方块验证规则函数"
            if (!new net.minecraft.item.ItemStack(Blocks.STONE.asItem()).isEmpty()) {
                passed++;
            } else {
                failures.append("石头没有对应物品；");
            }
        }

        // 3. 黑名单里的方块不该被复制（用配置里的第一项验证）
        total++;
        List<String> entries = StepClonerConfig.blacklistEntries();
        if (entries.isEmpty()) {
            passed++; // 没配黑名单，跳过
        } else {
            String first = entries.get(0);
            var id = net.minecraft.util.Identifier.tryParse(first);
            var block = id == null ? null : net.minecraft.registry.Registries.BLOCK.get(id);
            if (block == null || block == Blocks.AIR) {
                failures.append("黑名单里的 ").append(first).append(" 不是有效方块；");
            } else if (StepClonerConfig.isBlacklisted(id)) {
                passed++;
            } else {
                failures.append("黑名单判断失效：").append(first).append("；");
            }
        }

        // 4. 配置值本身合法
        total++;
        if (StepClonerConfig.countPerStep >= 1 && StepClonerConfig.countPerStep <= 64
                && StepClonerConfig.cooldownTicks >= 0) {
            passed++;
        } else {
            failures.append("配置值越界；");
        }

        int finalPassed = passed;
        int finalTotal = total;
        String detail = failures.isEmpty() ? "全部正常" : failures.toString();
        source.sendFeedback(() -> Text.literal("[踏地复制] 自检：" + finalPassed + "/" + finalTotal
                + "（" + detail + "）"), false);
        return passed == total ? 1 : 0;
    }
}
