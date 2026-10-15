package com.example.stepcloner;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step Cloner（踏地复制）
 * <p>
 * 玩家走路时，把脚下踩到的方块<strong>复制一份</strong>放进背包——方块本身不会被挖掉，
 * 也就是说这是一份"走过就白拿"的方块（可配置开关与过滤规则）。
 * <p>
 * 实现思路：
 * <ol>
 *     <li>服务端每个 tick 遍历在线玩家（每玩家一次，开销极小）。</li>
 *     <li>取玩家脚下那一格（从脚部所在坐标往下找，最多找 2 格，兼容台阶与雪层）。</li>
 *     <li>和这个玩家上一次"踩到过的坐标"比较：
 *         只有<strong>换了一格</strong>才触发复制，所以站着不动、原地转圈都不会刷。</li>
 *     <li>冷却没到就跳过（防止高频率来回走刷得太快，可配置）。</li>
 * </ol>
 */
public class StepCloner implements ModInitializer {

    public static final String MOD_ID = "step_cloner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        StepClonerConfig.load();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!StepClonerConfig.enabled) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                StepClonerRules.tickPlayer(player);
            }
        });

        if (StepClonerConfig.enableCommand) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    StepClonerCommand.register(dispatcher));
        }

        // 玩家下线就清掉他的记录，别在内存里留垃圾
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                StepClonerRules.forget(handler.getPlayer()));

        LOGGER.info("[Step Cloner] 已加载：踩到的方块会在行走时被复制进背包（{}）",
                StepClonerConfig.enabled ? "已启用" : "已禁用");
    }

    /** 供命令与测试使用：查询某个玩家上一次触发复制的坐标。 */
    static BlockPos debugLastPos(ServerPlayerEntity player) {
        return StepClonerRules.lastPos(player);
    }
}
