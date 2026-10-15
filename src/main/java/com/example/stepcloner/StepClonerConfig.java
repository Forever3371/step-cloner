package com.example.stepcloner;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 极简配置：读写 config/step_cloner.properties。
 */
public final class StepClonerConfig {

    /** 总开关。 */
    public static boolean enabled = true;

    /** 两次复制之间至少间隔多少 tick（0 = 每换一格就复制）。 */
    public static int cooldownTicks = 0;

    /** 每踩到一格复制几个。 */
    public static int countPerStep = 1;

    /** 创造模式玩家是否也生效。 */
    public static boolean allowCreative = true;

    /** 是否注册 /stepcloner 命令。 */
    public static boolean enableCommand = true;

    /** 不参与复制的方块（完整 id，逗号分隔）。 */
    private static final Set<String> BLACKLIST = new HashSet<>();
    private static String blacklistRaw = "";

    /**
     * 默认排除的技术性方块：拿到手也没法正常使用，或者明显不该被复制。
     * 想复制基岩、刷怪笼之类，自己在配置里加/删即可（基岩默认是<strong>允许</strong>的）。
     */
    private static final String DEFAULT_BLACKLIST = String.join(",",
            "minecraft:barrier",
            "minecraft:light",
            "minecraft:structure_void",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block");

    private static final String FILE_NAME = "step_cloner.properties";

    private StepClonerConfig() {
    }

    public static boolean isBlacklisted(Identifier id) {
        return BLACKLIST.contains(id.toString());
    }

    public static String blacklistText() {
        return blacklistRaw;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();

        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, defaultContent(), StandardCharsets.UTF_8);
                StepCloner.LOGGER.info("[Step Cloner] 已生成默认配置：{}", path);
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException e) {
            StepCloner.LOGGER.warn("[Step Cloner] 读取配置失败，使用内置默认值", e);
        }

        enabled = readBoolean(props, "enabled", true);
        cooldownTicks = readInt(props, "cooldown_ticks", 0, 0, 72000);
        countPerStep = readInt(props, "count_per_step", 1, 1, 64);
        allowCreative = readBoolean(props, "allow_creative", true);
        enableCommand = readBoolean(props, "enable_command", true);

        blacklistRaw = props.getProperty("blacklist", DEFAULT_BLACKLIST).trim();
        BLACKLIST.clear();
        for (String part : blacklistRaw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                BLACKLIST.add(trimmed);
            }
        }
    }

    private static boolean readBoolean(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static int readInt(Properties props, String key, int fallback, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                StepCloner.LOGGER.warn("[Step Cloner] 配置项 {}={} 超出范围 [{}, {}]，使用默认值 {}",
                        key, value, min, max, fallback);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            StepCloner.LOGGER.warn("[Step Cloner] 配置项 {}={} 不是整数，使用默认值 {}", key, raw, fallback);
            return fallback;
        }
    }

    /** 供命令提示用：把黑名单列出来。 */
    public static List<String> blacklistEntries() {
        return Arrays.stream(blacklistRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String defaultContent() {
        return """
                # Step Cloner（踏地复制）配置
                # 玩家走路时，脚下踩到的方块会被复制一份放进背包——方块本身不会被挖掉。
                # 同一个格子只触发一次：站着不动、原地转圈都不会刷。
                # 但来回走（A -> B -> A）会按"踩到新格子"再触发一次，这是预期行为。

                # 总开关
                enabled=true

                # 两次复制之间至少间隔多少 tick（20 tick = 1 秒；0 = 每换一格就复制）
                cooldown_ticks=0

                # 每踩到一格复制几个（1 ~ 64）
                count_per_step=1

                # 创造模式玩家是否也生效
                allow_creative=true

                # 是否注册 /stepcloner 命令
                enable_command=true

                # 不参与复制的方块（完整 id，逗号分隔）
                # 默认排除的是技术性方块；基岩、刷怪笼等默认是允许复制的，
                # 想屏蔽就把对应 id 加进来，例如 minecraft:bedrock
                blacklist=minecraft:barrier,minecraft:light,minecraft:structure_void,minecraft:structure_block,minecraft:jigsaw,minecraft:command_block,minecraft:chain_command_block,minecraft:repeating_command_block
                """;
    }
}
