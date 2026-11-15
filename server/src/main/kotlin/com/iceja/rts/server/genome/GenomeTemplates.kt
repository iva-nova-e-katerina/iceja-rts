package com.iceja.rts.server.genome

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.SocketType

object GenomeTemplates {
    private fun part(
        partId: String,
        socketId: SocketType,
        mirrored: Boolean = false
    ): PartPlacement = PartPlacement(
        partId = partId,
        socketId = socketId,
        mirrored = mirrored
    )

    val all: List<Genome> = listOf(
        Genome(
            chassisId = "CRAWLER",
            name = "Scout",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SCUTTLER", SocketType.LEFT, mirrored = true),
                part("FACET", SocketType.FRONT),
                part("CHEMO", SocketType.FRONT),
                part("SPINE", SocketType.TOP)
            ),
            behavior = BehaviorSpec(presetId = "SKIRMISHER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "BRUTE",
            name = "Brawler",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("CLAWED", SocketType.LEFT, mirrored = true),
                part("MANDIBLE", SocketType.FRONT),
                part("FACET", SocketType.FRONT),
                part("CHITIN", SocketType.TOP),
                part("BONEPLATE", SocketType.TOP)
            ),
            behavior = BehaviorSpec(presetId = "BERSERK"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "CRAWLER",
            name = "Spitter",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SCUTTLER", SocketType.LEFT, mirrored = true),
                part("SPINE", SocketType.TOP),
                part("FACET", SocketType.FRONT),
                part("CHEMO", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "SKIRMISHER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "BRUTE",
            name = "Tank",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SLUGFOOT", SocketType.UNDER),
                part("CLAWED", SocketType.LEFT, mirrored = true),
                part("FACET", SocketType.FRONT),
                part("CHITIN", SocketType.TOP),
                part("BONEPLATE", SocketType.TOP)
            ),
            behavior = BehaviorSpec(presetId = "DEFENDER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "GRUB",
            name = "Swarm",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SLUGFOOT", SocketType.UNDER),
                part("MANDIBLE", SocketType.FRONT),
                part("FACET", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "BERSERK"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "BRUTE",
            name = "Artillery",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SLUGFOOT", SocketType.UNDER),
                part("SPOREMORTAR", SocketType.TOP),
                part("TARGETING", SocketType.TOP),
                part("CHEMO", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "SIEGE"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "CRAWLER",
            name = "Healer",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("SYMBIOTE", SocketType.CORE),
                part("SCUTTLER", SocketType.LEFT, mirrored = true),
                part("FACET", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "GUARD"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "BURROWER",
            name = "Burrower",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("BURROWCLAW", SocketType.UNDER),
                part("CLAWS", SocketType.LEFT),
                part("CAMO", SocketType.TOP),
                part("FACET", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "FLANKER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "FLYER",
            name = "Flyer",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("WINGS", SocketType.TOP),
                part("SPINE", SocketType.TOP),
                part("FACET", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "SKIRMISHER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        ),
        Genome(
            chassisId = "CRAWLER",
            name = "Harvester",
            parts = listOf(
                part("GANGLION", SocketType.CORE),
                part("HEART", SocketType.CORE),
                part("NECTARCROP", SocketType.CORE),
                part("SCUTTLER", SocketType.LEFT, mirrored = true),
                part("SCAVENGER", SocketType.FRONT),
                part("FACET", SocketType.FRONT)
            ),
            behavior = BehaviorSpec(presetId = "SCAVENGER"),
            catalogVersion = GameCatalog.CATALOG_VERSION
        )
    )
}
