package com.iceja.rts.server.store

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals

class CreatureExperienceTest {
    @Test
    fun experienceIsStoredAndCapped() {
        val genome = GenomeTemplates.all.first { it.name == "Brawler" }
        val analysis = GenomeAnalyzer.analyze(genome)
        val repository = CreatureRepository()
        val record = repository.create("owner", genome, analysis.toValidationResult())

        repository.addExperience(record.id, 350)
        assertEquals(350, repository.find(record.id)?.experience)
        assertEquals(350, repository.find(record.id)?.toView()?.experience)

        repository.addExperience(record.id, GameCatalog.ruleset.maxExperience * 2)
        assertEquals(GameCatalog.ruleset.maxExperience, repository.find(record.id)?.experience)
    }
}
