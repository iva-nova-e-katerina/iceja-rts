package com.iceja.rts.server.genome

data class BehaviorGraphValidation(
    val cp: Int,
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>
)

object BehaviorGraphValidator {
    private val compositeTypes = setOf(
        "Selector",
        "Sequence",
        "Parallel",
        "UtilitySelector",
        "RandomSelector",
        "Cooldown",
        "Repeat",
        "Inverter",
        "Subtree"
    )

    private val conditionTypes = setOf(
        "HasTarget",
        "TargetInRange",
        "TargetVisible",
        "HpBelow",
        "HpAbove",
        "EnergyBelow",
        "EnergyAbove",
        "HeatAbove",
        "EnemyCount",
        "AllyCount",
        "IsUnderAttack",
        "HiveUnderAttack",
        "HiveHpBelow",
        "EnemyHiveInRange",
        "HasStatus",
        "TimerElapsed",
        "RandomChance",
        "ResourceNearby",
        "CorpseNearby",
        "IsRole",
        "HasLineOfSight"
    )

    private val actionTypes = setOf(
        "MoveTo",
        "MoveToPoint",
        "AttackTarget",
        "AttackMove",
        "Flee",
        "Follow",
        "Regroup",
        "HoldPosition",
        "Patrol",
        "Guard",
        "Harvest",
        "EatCorpse",
        "UseAbility",
        "Burrow",
        "Unburrow",
        "Reproduce",
        "Taunt",
        "Wait"
    )

    fun validate(graph: BehaviorGraph): BehaviorGraphValidation {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val nodesById = graph.nodes.associateBy { it.id }

        if (graph.nodes.isEmpty()) {
            errors += issue("NO_ROOT", "Behavior graph has no nodes", "root")
            return BehaviorGraphValidation(0, errors, warnings)
        }

        if (graph.rootId.isBlank() || !nodesById.containsKey(graph.rootId)) {
            errors += issue("NO_ROOT", "Behavior graph root is missing", graph.rootId)
        }

        if (graph.nodes.size > 64) {
            errors += issue("TOO_MANY_NODES", "Behavior graph has more than 64 nodes", "graph")
        }

        val duplicateIds = graph.nodes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        duplicateIds.forEach { duplicateId ->
            errors += issue("DUPLICATE_NODE", "Behavior node id is duplicated: $duplicateId", duplicateId)
        }

        graph.nodes.forEach { node ->
            val known = compositeTypes.contains(node.type) ||
                conditionTypes.contains(node.type) ||
                actionTypes.contains(node.type)
            if (!known) {
                errors += issue("UNKNOWN_NODE", "Unknown behavior node type: ${node.type}", node.id)
            }
            if (!compositeTypes.contains(node.type) && node.children.isNotEmpty()) {
                errors += issue("INVALID_PORT", "Leaf node cannot have children", node.id)
            }
            node.children.forEach { childId ->
                if (!nodesById.containsKey(childId)) {
                    errors += issue("ORPHAN_NODE", "Behavior node references missing child: $childId", node.id)
                }
            }
        }

        val reachable = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        if (nodesById.containsKey(graph.rootId)) {
            stack.addLast(graph.rootId)
        }
        while (stack.isNotEmpty()) {
            val nodeId = stack.removeLast()
            if (!reachable.add(nodeId)) {
                continue
            }
            nodesById[nodeId]?.children?.forEach { childId ->
                if (nodesById.containsKey(childId)) {
                    stack.addLast(childId)
                }
            }
        }
        graph.nodes.filter { !reachable.contains(it.id) }.forEach { orphan ->
            errors += issue("ORPHAN_NODE", "Behavior node is not connected to root", orphan.id)
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun detectCycle(nodeId: String): Boolean {
            if (visiting.contains(nodeId)) {
                return true
            }
            if (visited.contains(nodeId)) {
                return false
            }
            visiting += nodeId
            val node = nodesById[nodeId] ?: return false
            val cycle = node.children.any { childId -> detectCycle(childId) }
            visiting -= nodeId
            visited += nodeId
            return cycle
        }
        if (detectCycle(graph.rootId)) {
            errors += issue("CYCLE", "Behavior graph contains a cycle", graph.rootId)
        }

        fun depth(nodeId: String, seen: Set<String>): Int {
            if (seen.contains(nodeId)) {
                return 0
            }
            val node = nodesById[nodeId] ?: return 0
            val childDepth = node.children.maxOfOrNull { childId ->
                depth(childId, seen + nodeId)
            } ?: 0
            return childDepth + 1
        }
        if (nodesById.containsKey(graph.rootId) && depth(graph.rootId, emptySet()) > 8) {
            errors += issue("TOO_DEEP", "Behavior graph depth is greater than 8", graph.rootId)
        }

        var cp = 0
        graph.nodes.forEach { node ->
            cp += when {
                compositeTypes.contains(node.type) -> 2
                actionTypes.contains(node.type) -> 2
                conditionTypes.contains(node.type) -> 1
                else -> 0
            }
            if (node.params.size > 1) {
                cp += 1
            }
        }
        if (cp > 100) {
            errors += issue("TOO_COMPLEX", "Behavior graph complexity is greater than 100 CP", "graph")
        }
        if (graph.nodes.size < 5) {
            warnings += warning("BEHAVIOR_TOO_SIMPLE", "Behavior graph has fewer than 5 nodes", "graph")
        }

        return BehaviorGraphValidation(cp, errors, warnings)
    }

    private fun issue(code: String, message: String, nodeId: String): ValidationIssue =
        ValidationIssue(code, message, ValidationSeverity.ERROR, nodeId)

    private fun warning(code: String, message: String, nodeId: String): ValidationIssue =
        ValidationIssue(code, message, ValidationSeverity.WARNING, nodeId)
}
