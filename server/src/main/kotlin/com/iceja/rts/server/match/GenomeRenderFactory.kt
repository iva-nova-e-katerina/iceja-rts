package com.iceja.rts.server.match

import com.iceja.rts.server.api.CreatureRenderPart
import com.iceja.rts.server.api.CreatureRenderView
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.PartVisibility
import com.iceja.rts.server.genome.DerivedStats
import com.iceja.rts.server.genome.Genome

object GenomeRenderFactory {
    fun build(genome: Genome, stats: DerivedStats): CreatureRenderView {
        val chassis = GameCatalog.chassisById[genome.chassisId]
        val sizeClass = chassis?.sizeClass ?: "S"
        val bodyRadiusMm = when (sizeClass) {
            "S" -> 1_400
            "M" -> 1_900
            "L" -> 2_600
            else -> 1_600
        }
        val parts = genome.parts.mapNotNull { placement ->
            val part = GameCatalog.partsById[placement.partId] ?: return@mapNotNull null
            if (part.visual.visibility != PartVisibility.EXTERNAL) {
                return@mapNotNull null
            }
            CreatureRenderPart(
                partId = part.id,
                socketId = placement.socketId,
                shape = part.visual.shape,
                scale = part.visual.scale,
                mirrored = placement.mirrored
            )
        }
        return CreatureRenderView(
            chassisId = genome.chassisId,
            sizeClass = sizeClass,
            bodyRadiusMm = maxOf(bodyRadiusMm, stats.radiusMm * 2),
            parts = parts
        )
    }
}
