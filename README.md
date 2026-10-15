# Step Cloner（踏地复制）

Minecraft **1.21.1** / **Fabric** 模组：**玩家走路时，脚下踩到的方块会复制一份进背包**——方块本身不会被挖掉。

## 行为

| 情况 | 结果 |
|---|---|
| 踩到新的一格 | 背包里多一个那个方块 |
| 站在同一格不动 | 不会重复给（原地转圈也不刷） |
| A → B → A 来回走 | **会重复触发**，这是"走过就复制"的字面行为（见下方"可调项"） |
| 悬空（脚下 3 格内都是空气） | 什么都不给 |
| 背包满了 | 复制出来的方块掉在脚边 |

方块以**它自己的物品形式**给出：钻石矿给钻石矿（不是钻石），石头给石头，草方块给草方块。

## 不会复制的方块

- 空气
- 没有物品形式的方块：水、岩浆、火、传送门……
- 配置里黑名单上的方块，默认排除的是技术性方块：
  屏障、光源、结构空位、结构方块、拼图方块、三种命令方块

> **基岩、刷怪笼等默认是允许复制的**。想屏蔽就把对应 id 加进 `blacklist`。

## 配置

首次启动生成 `config/step_cloner.properties`：

```properties
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
blacklist=minecraft:barrier,minecraft:light,...
```

改完可以用 `/stepcloner reload` 当场生效，或者重启游戏。

### 可调项说明

- **怕来回走刷太多**：把 `cooldown_ticks` 调大（比如 10 = 每 0.5 秒最多一次）
  —— 代价是跑动时可能漏掉一些格子。
- **不想在创造模式里也生效**：`allow_creative=false`

## 游戏内命令

```
/stepcloner on | off     开关
/stepcloner status       查看状态、脚下这一格会不会被复制、累计拿了多少
/stepcloner reload       重新读取配置文件
/stepcloner selftest     自检
```

需要权限等级 2（单人游戏开作弊即可，服务器上需要 OP）。

## ⚠️ 这是作弊向模组

走到钻石矿上就能拿钻石矿，走到任何方块上都能拿它。**别用在正常的生存存档里**，
会直接把资源获取的意义抹掉。适合拿来搭建筑、做测试、或者当"创造模式替代品"。

## 测试

```bash
./gradlew build          # 顺带跑 gametest
./gradlew runGameTest    # 只跑测试（8 项）
```

gametest 覆盖：站上方块能拿到、同格不重复、走到新格再拿、方块以自身物品形式给出、
悬空不给、水不给、黑名单不给、创造模式可被配置排除。

生产验证：`tools/production-test/` 是一个数据包，丢进存档的 `datapacks/` 后
服务器启动时会自动跑 `/stepcloner selftest` 并用 `execute store success` 断言结果，
日志里出现 `PASS_STEPCLONER_SELFTEST` 即为通过。已实测通过。

## 实现要点

- 服务端 `END_SERVER_TICK` 遍历在线玩家，每个玩家每 tick 只做一次坐标比较，开销极小。
- "脚下"是**从玩家脚部坐标往下找第一个非空气方块**（最多 2 格），这样台阶、雪层、地毯都不会错位。
- 只有**坐标发生变化**才触发，所以站着不动不会刷。
- 玩家下线时清掉记录，不会在内存里堆垃圾。

## 许可

MIT
