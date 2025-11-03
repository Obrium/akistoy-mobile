package com.akiestoy.beacons.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation que formatea un RUT chileno con puntos y guión
 * Entrada: 193849571
 * Salida visual: 19.384.957-1
 */
class RutVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text

        // Formatear el texto
        val formatted = formatRut(originalText)

        // Mapear las posiciones
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0

                var transformedOffset = 0
                var originalCount = 0

                for (char in formatted) {
                    if (originalCount >= offset) break
                    transformedOffset++
                    if (char != '.' && char != '-') {
                        originalCount++
                    }
                }

                return transformedOffset
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset == 0) return 0

                var originalOffset = 0
                var transformedCount = 0

                for (char in formatted) {
                    if (transformedCount >= offset) break
                    transformedCount++
                    if (char != '.' && char != '-') {
                        originalOffset++
                    }
                }

                return originalOffset.coerceAtMost(originalText.length)
            }
        }

        return TransformedText(
            AnnotatedString(formatted),
            offsetMapping
        )
    }

    private fun formatRut(input: String): String {
        if (input.isEmpty()) return ""

        // Separar números del verificador
        val numbers = input.take(input.length.coerceAtMost(8))
        val verifier = if (input.length > 8) input.substring(8, 9) else ""

        // Formatear la parte numérica con puntos (de derecha a izquierda)
        val formatted = numbers
            .reversed()
            .chunked(3)
            .map { it.reversed() }
            .reversed()
            .joinToString(".")

        // Agregar el verificador con guión si existe
        return if (verifier.isNotEmpty()) {
            "$formatted-$verifier"
        } else {
            formatted
        }
    }
}
