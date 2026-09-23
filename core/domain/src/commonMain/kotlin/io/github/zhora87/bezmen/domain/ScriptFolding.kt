package io.github.zhora87.bezmen.domain

/**
 * OCR engines routinely emit Latin look-alikes inside Cyrillic words ("Kr" for "кг", "FPH" for "ГРН")
 * and the other way round. Folding maps a token onto the pack's script before it is compared with
 * unit aliases, currency symbols and markers. Display text is never folded.
 */
object ScriptFolding {
    private val latinToCyrillic: Map<Char, Char> = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'f' to 'г', 'h' to 'н', 'i' to 'і', 'k' to 'к',
        'm' to 'м', 'o' to 'о', 'p' to 'р', 'r' to 'г', 't' to 'т', 'x' to 'х', 'y' to 'у',
        'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'F' to 'Г', 'H' to 'Н', 'I' to 'І', 'K' to 'К',
        'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
    )
    private val cyrillicToLatin: Map<Char, Char> = mapOf(
        'а' to 'a', 'в' to 'b', 'с' to 'c', 'е' to 'e', 'н' to 'h', 'і' to 'i', 'к' to 'k', 'м' to 'm',
        'о' to 'o', 'р' to 'p', 'г' to 'r', 'т' to 't', 'х' to 'x', 'у' to 'y',
        'А' to 'A', 'В' to 'B', 'С' to 'C', 'Е' to 'E', 'Н' to 'H', 'І' to 'I', 'К' to 'K', 'М' to 'M',
        'О' to 'O', 'Р' to 'P', 'Г' to 'F', 'Т' to 'T', 'Х' to 'X', 'У' to 'Y',
    )
    private val digitToCyrillic: Map<Char, Char> = mapOf('3' to 'з', '0' to 'о')
    private val digitToLatin: Map<Char, Char> = mapOf('0' to 'o')

    fun fold(text: String, script: Script): String {
        val letters = if (script == Script.CYRILLIC) latinToCyrillic else cyrillicToLatin
        val digits = if (script == Script.CYRILLIC) digitToCyrillic else digitToLatin
        return text.split(' ').joinToString(" ") { foldChunk(it, letters, digits) }
    }

    private fun foldChunk(chunk: String, letters: Map<Char, Char>, digits: Map<Char, Char>): String {
        val folded = buildString(chunk.length) { chunk.forEach { append(letters[it] ?: it) } }
        val letterCount = folded.count(Char::isLetter)
        val digitCount = folded.count(Char::isDigit)
        if (letterCount <= digitCount) return folded
        // Letter-dominant chunk such as "3НИЖКА": digits glued to letters are misread letters.
        return buildString(folded.length) {
            folded.forEachIndexed { i, c ->
                val neighbour = folded.getOrNull(i - 1)?.takeIf(Char::isLetter)
                    ?: folded.getOrNull(i + 1)?.takeIf(Char::isLetter)
                val letter = digits[c]
                append(
                    when {
                        neighbour == null || letter == null -> c
                        neighbour.isUpperCase() -> letter.uppercaseChar()
                        else -> letter
                    },
                )
            }
        }
    }
}
