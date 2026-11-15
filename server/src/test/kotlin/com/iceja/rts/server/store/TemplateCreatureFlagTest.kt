package com.iceja.rts.server.store

import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateCreatureFlagTest {
    @Test
    fun templateCreaturesAreMarkedSeparately() {
        val genome = GenomeTemplates.all.first { it.name == "Brawler" }
        val repo = CreatureRepository()
        val custom = repo.create("owner", genome, GenomeAnalyzer.analyze(genome).toValidationResult())
        val template = repo.create(
            "owner",
            genome,
            GenomeAnalyzer.analyze(genome).toValidationResult(),
            isTemplate = true
        )

        assertFalse(custom.toView().isTemplate)
        assertTrue(template.toView().isTemplate)
    }
}
