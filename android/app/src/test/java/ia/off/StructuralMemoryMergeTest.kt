package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralMemoryMergeTest {
    @Test
    fun structuralHitAugmentsMemoriaSelectedContextWithoutInventingConfidence() {
        val semantic = MemoryResolution(
            status = MemoryStatus.UNRESOLVED,
            contextItems = emptyList(),
            memoryIds = emptyList(),
            confidence = null,
        )
        val structural = StructuralMemoryResolution(
            status = MemoryStatus.HIT,
            contexts = listOf(
                StructuralMemoryContext(
                    sourceText = "Meu gato se chama Alt.",
                    sourceIds = listOf("m1", "m2"),
                    score = 0.91,
                    exactOverlap = 2,
                    associationMass = 0.51,
                    repetitions = 2,
                ),
            ),
        )

        val merged = mergeMemoryResolutions(semantic, structural)

        assertEquals(MemoryStatus.HIT, merged.status)
        assertEquals(listOf("Meu gato se chama Alt."), merged.contextItems)
        assertEquals(listOf("m1", "m2"), merged.memoryIds)
        assertNull(merged.confidence)

        val prompt = materializePrompt("Qual nome do meu gato?", merged)
        assertTrue(prompt.contains("Meu gato se chama Alt."))
        assertTrue(prompt.contains("Qual nome do meu gato?"))
        assertFalse(prompt.contains("score"))
        assertFalse(prompt.contains("association_mass"))
    }

    @Test
    fun semanticAndStructuralContextsAreDeduplicatedWithoutLocalReranking() {
        val semantic = MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("Meu gato se chama Alt."),
            memoryIds = listOf("m1"),
            confidence = 0.8,
        )
        val structural = StructuralMemoryResolution(
            status = MemoryStatus.HIT,
            contexts = listOf(
                StructuralMemoryContext(
                    sourceText = "Meu gato se chama Alt.",
                    sourceIds = listOf("m1", "m2"),
                    repetitions = 2,
                ),
                StructuralMemoryContext(
                    sourceText = "Meu gato dorme no sofa.",
                    sourceIds = listOf("m3"),
                ),
            ),
        )

        val merged = mergeMemoryResolutions(semantic, structural)

        assertEquals(
            listOf("Meu gato se chama Alt.", "Meu gato dorme no sofa."),
            merged.contextItems,
        )
        assertEquals(listOf("m1", "m2", "m3"), merged.memoryIds)
        assertEquals(0.8, merged.confidence!!, 0.0)
    }

    @Test
    fun unavailableStructuralLayerLeavesSemanticResolutionUntouched() {
        val semantic = MemoryResolution(
            status = MemoryStatus.MISS,
            contextItems = emptyList(),
            memoryIds = listOf("semantic-audit"),
            confidence = null,
        )

        val merged = mergeMemoryResolutions(
            semantic,
            StructuralMemoryResolution(status = MemoryStatus.UNAVAILABLE),
        )

        assertEquals(semantic, merged)
    }
}
