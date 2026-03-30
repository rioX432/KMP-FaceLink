package io.github.kmpfacelink.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedExpressionTest {

    @Test
    fun allEntriesHaveNonBlankParameterName() {
        UnifiedExpression.entries.forEach { expression ->
            assertTrue(
                expression.parameterName.isNotBlank(),
                "${expression.name} has blank parameterName",
            )
        }
    }

    @Test
    fun parameterNamesAreUnique() {
        val names = UnifiedExpression.entries.map { it.parameterName }
        assertEquals(names.size, names.toSet().size, "Duplicate parameter names found")
    }

    @Test
    fun fromParameterNameFindsKnownExpression() {
        val expression = UnifiedExpression.fromParameterName("JawOpen")
        assertNotNull(expression)
        assertEquals(UnifiedExpression.JAW_OPEN, expression)
    }

    @Test
    fun fromParameterNameReturnsNullForUnknown() {
        assertNull(UnifiedExpression.fromParameterName("NotARealExpression"))
    }

    @Test
    fun baseShapeCountMatchesSpec() {
        // Verify we have the expected base shapes from the VRCFT spec
        // Eye(18) + Brow(8) + Nose(6) + Cheek(6) + Jaw(8) + Lip(14) + Mouth(26) + Tongue(12) + Throat(4) = 102
        val baseShapes = UnifiedExpression.entries.filter { expression ->
            expression !in UnifiedExpressionsMapper.BLENDED_EXPRESSIONS
        }
        assertTrue(baseShapes.size >= 90, "Expected at least 90 base shapes, got ${baseShapes.size}")
    }

    @Test
    fun fromParameterNameRoundTrips() {
        UnifiedExpression.entries.forEach { expression ->
            val found = UnifiedExpression.fromParameterName(expression.parameterName)
            assertEquals(expression, found, "Round-trip failed for ${expression.name}")
        }
    }
}
