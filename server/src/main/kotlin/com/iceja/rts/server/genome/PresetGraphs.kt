package com.iceja.rts.server.genome

data class PresetGraph(
    val presetId: String,
    val graph: BehaviorGraph
)

object PresetGraphs {
    private fun node(
        id: String,
        type: String,
        children: List<String> = emptyList(),
        params: Map<String, Int> = emptyMap(),
        text: String = ""
    ): BehaviorNode = BehaviorNode(
        id = id,
        type = type,
        children = children,
        params = params,
        text = text
    )

    private fun sequence(id: String, vararg children: String): BehaviorNode =
        node(id, "Sequence", children.toList())

    private fun selector(id: String, vararg children: String): BehaviorNode =
        node(id, "UtilitySelector", children.toList())

    private fun graph(rootId: String, vararg nodes: BehaviorNode): BehaviorGraph =
        BehaviorGraph(
            rootId = rootId,
            nodes = nodes.toList()
        )

    val all: List<PresetGraph> = listOf(
        PresetGraph(
            presetId = "BERSERK",
            graph = graph(
                "berserk-root",
                selector(
                    "berserk-root",
                    "berserk-survive",
                    "berserk-attack",
                    "berserk-hunt",
                    "berserk-patrol"
                ),
                sequence("berserk-survive", "berserk-hp", "berserk-flee"),
                node("berserk-hp", "HpBelow", params = mapOf("pct" to 20)),
                node("berserk-flee", "Flee"),
                sequence("berserk-attack", "berserk-target", "berserk-attack-action"),
                node("berserk-target", "HasTarget"),
                node("berserk-attack-action", "AttackTarget", text = "enemy.nearest"),
                sequence("berserk-hunt", "berserk-enemy-count", "berserk-move"),
                node(
                    "berserk-enemy-count",
                    "EnemyCount",
                    params = mapOf("min" to 1, "max" to 99, "radius" to 30)
                ),
                node("berserk-move", "MoveTo", text = "enemy.nearest"),
                node("berserk-patrol", "Patrol")
            )
        ),
        PresetGraph(
            presetId = "GUARD",
            graph = graph(
                "guard-root",
                selector(
                    "guard-root",
                    "guard-return",
                    "guard-defend",
                    "guard-attack",
                    "guard-hold"
                ),
                sequence("guard-return", "guard-hp", "guard-return-action"),
                node("guard-hp", "HpBelow", params = mapOf("pct" to 15)),
                node("guard-return-action", "MoveTo", text = "hive"),
                sequence("guard-defend", "guard-hive-attacked", "guard-defend-action"),
                node("guard-hive-attacked", "HiveUnderAttack"),
                node("guard-defend-action", "AttackTarget", text = "enemy.nearest"),
                sequence("guard-attack", "guard-range", "guard-attack-action"),
                node("guard-range", "TargetInRange", params = mapOf("range" to 12)),
                node("guard-attack-action", "AttackTarget", text = "enemy.nearest"),
                node("guard-hold", "Guard")
            )
        ),
        PresetGraph(
            presetId = "PACK",
            graph = graph(
                "pack-root",
                selector(
                    "pack-root",
                    "pack-survive",
                    "pack-attack",
                    "pack-hunt",
                    "pack-regroup",
                    "pack-patrol"
                ),
                sequence("pack-survive", "pack-hp", "pack-regroup-action"),
                node("pack-hp", "HpBelow", params = mapOf("pct" to 25)),
                node("pack-regroup-action", "Regroup"),
                sequence("pack-attack", "pack-enemy-count", "pack-attack-action"),
                node(
                    "pack-enemy-count",
                    "EnemyCount",
                    params = mapOf("min" to 1, "max" to 99, "radius" to 30)
                ),
                node("pack-attack-action", "AttackTarget", text = "enemy.nearest"),
                sequence("pack-hunt", "pack-target", "pack-move"),
                node("pack-target", "HasTarget"),
                node("pack-move", "MoveTo", text = "enemy.nearest"),
                sequence("pack-regroup", "pack-ally-count", "pack-follow"),
                node(
                    "pack-ally-count",
                    "AllyCount",
                    params = mapOf("min" to 1, "max" to 99, "radius" to 20)
                ),
                node("pack-follow", "Follow"),
                node("pack-patrol", "Patrol")
            )
        ),
        PresetGraph(
            presetId = "SKIRMISHER",
            graph = graph(
                "skirmisher-root",
                selector(
                    "skirmisher-root",
                    "skirmisher-survive",
                    "skirmisher-attack",
                    "skirmisher-hunt",
                    "skirmisher-patrol"
                ),
                sequence("skirmisher-survive", "skirmisher-hp", "skirmisher-flee"),
                node("skirmisher-hp", "HpBelow", params = mapOf("pct" to 20)),
                node("skirmisher-flee", "Flee"),
                sequence("skirmisher-attack", "skirmisher-target", "skirmisher-attack-action"),
                node("skirmisher-target", "HasTarget"),
                node("skirmisher-attack-action", "AttackTarget", text = "enemy.nearest"),
                sequence("skirmisher-hunt", "skirmisher-enemy-count", "skirmisher-move"),
                node(
                    "skirmisher-enemy-count",
                    "EnemyCount",
                    params = mapOf("min" to 1, "max" to 99, "radius" to 30)
                ),
                node("skirmisher-move", "MoveTo", text = "enemy.nearest"),
                node("skirmisher-patrol", "Patrol")
            )
        ),
        PresetGraph(
            presetId = "SCAVENGER",
            graph = graph(
                "scavenger-root",
                selector(
                    "scavenger-root",
                    "scavenger-survive",
                    "scavenger-corpse",
                    "scavenger-resource",
                    "scavenger-attack",
                    "scavenger-patrol"
                ),
                sequence("scavenger-survive", "scavenger-hp", "scavenger-flee"),
                node("scavenger-hp", "HpBelow", params = mapOf("pct" to 20)),
                node("scavenger-flee", "Flee"),
                sequence("scavenger-corpse", "scavenger-corpse-near", "scavenger-eat"),
                node("scavenger-corpse-near", "CorpseNearby", params = mapOf("radius" to 20)),
                node("scavenger-eat", "EatCorpse"),
                sequence("scavenger-resource", "scavenger-resource-near", "scavenger-harvest"),
                node("scavenger-resource-near", "ResourceNearby", params = mapOf("radius" to 20)),
                node("scavenger-harvest", "Harvest"),
                sequence("scavenger-attack", "scavenger-target", "scavenger-attack-action"),
                node("scavenger-target", "HasTarget"),
                node("scavenger-attack-action", "AttackTarget", text = "enemy.nearest"),
                node("scavenger-patrol", "Patrol")
            )
        ),
        PresetGraph(
            presetId = "SIEGE",
            graph = graph(
                "siege-root",
                selector(
                    "siege-root",
                    "siege-hive-range",
                    "siege-attack",
                    "siege-move"
                ),
                sequence("siege-hive-range", "siege-hive-in-range", "siege-hive-attack"),
                node("siege-hive-in-range", "EnemyHiveInRange", params = mapOf("range" to 30)),
                node("siege-hive-attack", "AttackTarget", text = "enemyHive"),
                sequence("siege-attack", "siege-target", "siege-attack-action"),
                node("siege-target", "HasTarget"),
                node("siege-attack-action", "AttackTarget", text = "enemy.nearest"),
                node("siege-move", "Patrol")
            )
        ),
        PresetGraph(
            presetId = "DEFENDER",
            graph = graph(
                "defender-root",
                selector(
                    "defender-root",
                    "defender-return",
                    "defender-hive",
                    "defender-attack",
                    "defender-guard"
                ),
                sequence("defender-return", "defender-hp", "defender-return-action"),
                node("defender-hp", "HpBelow", params = mapOf("pct" to 15)),
                node("defender-return-action", "MoveTo", text = "hive"),
                sequence("defender-hive", "defender-hive-attacked", "defender-hive-action"),
                node("defender-hive-attacked", "HiveUnderAttack"),
                node("defender-hive-action", "AttackTarget", text = "enemy.nearest"),
                sequence("defender-attack", "defender-range", "defender-attack-action"),
                node("defender-range", "TargetInRange", params = mapOf("range" to 12)),
                node("defender-attack-action", "AttackTarget", text = "enemy.nearest"),
                node("defender-guard", "Guard")
            )
        ),
        PresetGraph(
            presetId = "FLANKER",
            graph = graph(
                "flanker-root",
                selector(
                    "flanker-root",
                    "flanker-survive",
                    "flanker-attack",
                    "flanker-hunt",
                    "flanker-patrol"
                ),
                sequence("flanker-survive", "flanker-hp", "flanker-flee"),
                node("flanker-hp", "HpBelow", params = mapOf("pct" to 25)),
                node("flanker-flee", "Flee"),
                sequence("flanker-attack", "flanker-target", "flanker-attack-action"),
                node("flanker-target", "HasTarget"),
                node("flanker-attack-action", "AttackTarget", text = "enemy.nearest"),
                sequence("flanker-hunt", "flanker-target-2", "flanker-move"),
                node("flanker-target-2", "HasTarget"),
                node("flanker-move", "MoveTo", text = "enemy.nearest"),
                node("flanker-patrol", "Patrol")
            )
        )
    )

    private val byPreset = all.associateBy { it.presetId }

    fun forPreset(presetId: String): BehaviorGraph? = byPreset[presetId]?.graph
}
