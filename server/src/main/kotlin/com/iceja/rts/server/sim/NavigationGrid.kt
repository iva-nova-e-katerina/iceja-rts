package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.MapDefinition
import java.util.PriorityQueue

class NavigationGrid(
    private val mapDefinition: MapDefinition
) {
    private val cellSizeMm: Int = 1_000
    private val width: Int = mapDefinition.widthMm / cellSizeMm
    private val height: Int = mapDefinition.heightMm / cellSizeMm
    private val blocked: BooleanArray = BooleanArray(width * height)

    init {
        mapDefinition.obstacles.forEach { obstacle ->
            val startX = obstacle.x / cellSizeMm
            val startY = obstacle.y / cellSizeMm
            val endX = (obstacle.x + obstacle.width - 1) / cellSizeMm
            val endY = (obstacle.y + obstacle.height - 1) / cellSizeMm
            for (y in startY..endY) {
                for (x in startX..endX) {
                    if (contains(x, y)) {
                        blocked[index(x, y)] = true
                    }
                }
            }
        }
    }

    fun findPath(startX: Int, startY: Int, goalX: Int, goalY: Int): List<Vec2>? {
        val start = nearestFreeCell(cellX(startX), cellY(startY)) ?: return null
        val goal = nearestFreeCell(cellX(goalX), cellY(goalY)) ?: return null
        if (start == goal) {
            return emptyList()
        }

        val gScore = IntArray(width * height) { Int.MAX_VALUE }
        val cameFrom = IntArray(width * height) { -1 }
        val closed = BooleanArray(width * height)
        val open = PriorityQueue<OpenNode>(
            compareBy<OpenNode> { it.f }
                .thenBy { it.g }
                .thenBy { it.index }
        )

        gScore[start] = 0
        open.add(OpenNode(start, heuristic(start, goal), 0))

        while (open.isNotEmpty()) {
            val current = open.poll()
            if (closed[current.index]) {
                continue
            }
            if (current.index == goal) {
                return reconstruct(cameFrom, start, goal)
            }
            closed[current.index] = true

            val currentX = current.index % width
            val currentY = current.index / width
            for (direction in DIRECTIONS) {
                val nextX = currentX + direction.dx
                val nextY = currentY + direction.dy
                if (!contains(nextX, nextY)) {
                    continue
                }
                val nextIndex = index(nextX, nextY)
                if (blocked[nextIndex] || closed[nextIndex]) {
                    continue
                }
                if (direction.diagonal) {
                    val sideA = index(currentX + direction.dx, currentY)
                    val sideB = index(currentX, currentY + direction.dy)
                    if (blocked[sideA] || blocked[sideB]) {
                        continue
                    }
                }
                val tentative = current.g + direction.cost
                if (tentative < gScore[nextIndex]) {
                    gScore[nextIndex] = tentative
                    cameFrom[nextIndex] = current.index
                    open.add(OpenNode(nextIndex, tentative + heuristic(nextIndex, goal), tentative))
                }
            }
        }

        return null
    }

    fun hasLineOfSight(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        var x = cellX(startX)
        var y = cellY(startY)
        val targetX = cellX(endX)
        val targetY = cellY(endY)
        val dx = kotlin.math.abs(targetX - x)
        val sx = if (x < targetX) 1 else -1
        val dy = -kotlin.math.abs(targetY - y)
        val sy = if (y < targetY) 1 else -1
        var error = dx + dy

        while (true) {
            if (blocked[index(x, y)]) {
                return false
            }
            if (x == targetX && y == targetY) {
                return true
            }
            val doubledError = 2 * error
            if (doubledError >= dy) {
                error += dy
                x += sx
            }
            if (doubledError <= dx) {
                error += dx
                y += sy
            }
        }
    }

    fun nextExplorationPoint(startX: Int, startY: Int, visited: MutableSet<Int>): Vec2? {
        val stride = 10_000
        val columns = (mapDefinition.widthMm + stride - 1) / stride
        val rows = (mapDefinition.heightMm + stride - 1) / stride
        if (columns <= 0 || rows <= 0) {
            return null
        }
        val startColumn = (startX / stride).coerceIn(0, columns - 1)
        val startRow = (startY / stride).coerceIn(0, rows - 1)
        val startKey = startRow * columns + startColumn
        visited += startKey
        val seen = BooleanArray(columns * rows)
        seen[startKey] = true
        val queue = java.util.ArrayDeque<Int>()
        queue.add(startKey)
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            val column = key % columns
            val row = key / columns
            if (key != startKey && !visited.contains(key)) {
                val centerX = (column * stride + stride / 2).coerceIn(1_000, mapDefinition.widthMm - 1_000)
                val centerY = (row * stride + stride / 2).coerceIn(1_000, mapDefinition.heightMm - 1_000)
                if (!isBlockedAt(centerX, centerY)) {
                    visited += key
                    return Vec2(centerX, centerY)
                }
            }
            directions.forEach { (dx, dy) ->
                val nextColumn = column + dx
                val nextRow = row + dy
                if (nextColumn !in 0 until columns || nextRow !in 0 until rows) {
                    return@forEach
                }
                val nextKey = nextRow * columns + nextColumn
                if (!seen[nextKey]) {
                    seen[nextKey] = true
                    queue.add(nextKey)
                }
            }
        }
        return null
    }

    fun isBlockedAt(worldX: Int, worldY: Int): Boolean {
        val x = cellX(worldX)
        val y = cellY(worldY)
        return !contains(x, y) || blocked[index(x, y)]
    }

    private fun reconstruct(cameFrom: IntArray, start: Int, goal: Int): List<Vec2> {
        val reversed = mutableListOf<Vec2>()
        var current = goal
        while (current != start && current >= 0) {
            reversed += Vec2(
                x = current % width * cellSizeMm + cellSizeMm / 2,
                y = current / width * cellSizeMm + cellSizeMm / 2
            )
            current = cameFrom[current]
        }
        reversed.reverse()
        return reversed
    }

    private fun nearestFreeCell(startX: Int, startY: Int): Int? {
        if (isFree(startX, startY)) {
            return index(startX, startY)
        }
        for (radius in 1..12) {
            for (offsetY in -radius..radius) {
                for (offsetX in -radius..radius) {
                    if (kotlin.math.abs(offsetX) != radius && kotlin.math.abs(offsetY) != radius) {
                        continue
                    }
                    val x = startX + offsetX
                    val y = startY + offsetY
                    if (isFree(x, y)) {
                        return index(x, y)
                    }
                }
            }
        }
        return null
    }

    private fun isFree(x: Int, y: Int): Boolean =
        contains(x, y) && !blocked[index(x, y)]

    private fun contains(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height

    private fun index(x: Int, y: Int): Int = y * width + x

    private fun cellX(worldX: Int): Int =
        (worldX / cellSizeMm).coerceIn(0, width - 1)

    private fun cellY(worldY: Int): Int =
        (worldY / cellSizeMm).coerceIn(0, height - 1)

    private fun heuristic(from: Int, to: Int): Int {
        val fromX = from % width
        val fromY = from / width
        val toX = to % width
        val toY = to / width
        val dx = kotlin.math.abs(fromX - toX)
        val dy = kotlin.math.abs(fromY - toY)
        return 10 * (dx + dy) - 6 * kotlin.math.min(dx, dy)
    }

    private data class OpenNode(
        val index: Int,
        val f: Int,
        val g: Int
    )

    private data class Direction(
        val dx: Int,
        val dy: Int,
        val cost: Int,
        val diagonal: Boolean
    )

    private companion object {
        val DIRECTIONS = listOf(
            Direction(1, 0, 10, false),
            Direction(-1, 0, 10, false),
            Direction(0, 1, 10, false),
            Direction(0, -1, 10, false),
            Direction(1, 1, 14, true),
            Direction(1, -1, 14, true),
            Direction(-1, 1, 14, true),
            Direction(-1, -1, 14, true)
        )
    }
}
