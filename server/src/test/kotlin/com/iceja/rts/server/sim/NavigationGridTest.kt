package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigationGridTest {
    @Test
    fun pathAvoidsObstacles() {
        val map = GameCatalog.map("pillars")
        val navigation = NavigationGrid(map)
        val path = navigation.findPath(10_000, 10_000, 118_000, 118_000)

        assertNotNull(path)
        assertTrue(path.isNotEmpty())
        path.forEach { point ->
            map.obstacles.forEach { obstacle ->
                val inside = point.x >= obstacle.x &&
                    point.x <= obstacle.x + obstacle.width &&
                    point.y >= obstacle.y &&
                    point.y <= obstacle.y + obstacle.height
                assertTrue(!inside, "Path point $point is inside obstacle $obstacle")
            }
        }
    }

    @Test
    fun explorationDoesNotCampInMapCorners() {
        val map = GameCatalog.map("duel-arena")
        val navigation = NavigationGrid(map)
        val visited = mutableSetOf<Int>()
        var x = 28_000
        var y = 96_000
        val targets = mutableListOf<Vec2>()
        repeat(12) {
            val target = navigation.nextExplorationPoint(x, y, visited)
            assertNotNull(target)
            targets += target
            x = target.x
            y = target.y
        }

        val cornerCampers = targets.count { it.x > 170_000 && it.y > 170_000 }
        assertTrue(cornerCampers < 3, "scouts should not repeatedly camp in the bottom-right corner: $targets")
        assertTrue(targets.any { it.x > 100_000 }, "exploration should reach the right half of the map: $targets")
    }

    @Test
    fun openMapReturnsDirectPath() {
        val navigation = NavigationGrid(GameCatalog.map("duel-arena"))
        val path = navigation.findPath(10_000, 64_000, 118_000, 64_000)
        assertNotNull(path)
        assertTrue(path.isNotEmpty())
    }
}
