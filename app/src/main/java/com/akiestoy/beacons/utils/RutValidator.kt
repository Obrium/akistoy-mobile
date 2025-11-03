package com.akiestoy.beacons.utils

/**
 * Utilidades para validación y formateo de RUT chileno
 */
object RutValidator {

    /**
     * Formatea un RUT con puntos y guión mientras el usuario escribe
     * Ejemplo: 123456789 -> 12.345.678-9
     */
    fun formatRutAsTyping(input: String): String {
        val cleaned = cleanRut(input)
        if (cleaned.isEmpty()) return ""

        // Separar números del verificador
        val numbers = cleaned.take(cleaned.length.coerceAtMost(8))
        val verifier = if (cleaned.length > 8) cleaned.substring(8, 9) else ""

        // Formatear la parte numérica con puntos (de derecha a izquierda)
        // Invertir, separar en grupos de 3, revertir cada grupo, revertir el orden
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

    /**
     * Formatea un RUT con puntos y guión
     * Ejemplo: 12345678-9 -> 12.345.678-9
     */
    fun formatRut(rut: String): String {
        val cleanRut = cleanRut(rut)
        if (cleanRut.length < 2) return cleanRut

        val number = cleanRut.dropLast(1)
        val verifier = cleanRut.last()

        val formatted = number.reversed().chunked(3).joinToString(".").reversed()
        return "$formatted-$verifier"
    }

    /**
     * Limpia el RUT removiendo puntos y guiones
     * Ejemplo: 12.345.678-9 -> 123456789
     */
    fun cleanRut(rut: String): String {
        return rut.replace(".", "").replace("-", "").trim()
    }

    /**
     * Valida el formato básico del RUT (debe tener entre 8 y 9 dígitos + verificador)
     */
    fun isValidFormat(rut: String): Boolean {
        val cleanRut = cleanRut(rut)
        if (cleanRut.length < 8 || cleanRut.length > 9) return false

        val number = cleanRut.dropLast(1)
        val verifier = cleanRut.last()

        // Verificar que el número contenga solo dígitos
        if (!number.all { it.isDigit() }) return false

        // Verificar que el verificador sea dígito o K
        if (!verifier.isDigit() && verifier.uppercaseChar() != 'K') return false

        return true
    }

    /**
     * Valida completamente el RUT usando el algoritmo de verificación
     */
    fun isValid(rut: String): Boolean {
        if (!isValidFormat(rut)) return false

        val cleanRut = cleanRut(rut)
        val number = cleanRut.dropLast(1).toIntOrNull() ?: return false
        val verifier = cleanRut.last().uppercaseChar()

        val calculatedVerifier = calculateVerifier(number)
        return verifier == calculatedVerifier
    }

    /**
     * Calcula el dígito verificador de un RUT
     */
    private fun calculateVerifier(rut: Int): Char {
        var sum = 0
        var multiplier = 2
        var number = rut

        while (number > 0) {
            sum += (number % 10) * multiplier
            number /= 10
            multiplier = if (multiplier == 7) 2 else multiplier + 1
        }

        val remainder = 11 - (sum % 11)
        return when (remainder) {
            11 -> '0'
            10 -> 'K'
            else -> remainder.toString()[0]
        }
    }
}
