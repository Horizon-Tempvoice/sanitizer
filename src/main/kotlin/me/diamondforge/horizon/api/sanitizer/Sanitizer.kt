package me.diamondforge.horizon.api.sanitizer

class Sanitizer {

    /**
     * Transforms a string into a normalized form for comparison.
     * This includes converting to lowercase, replacing common character substitutions,
     * stripping non-alphanumeric characters, and deduplicating repeated letters.
     *
     * @param input The string to transform.
     * @return The normalized string.
     */
    fun transform(input: String, deduplicate: Boolean = true): String {
        var out = input.lowercase()

        // 1. Replace common character substitutions
        val replaced = StringBuilder()
        for (char in out) {
            replaced.append(SUBSTITUTIONS[char] ?: char)
        }
        out = replaced.toString()

        // 2. Strip everything that's not a-z0-9
        out = NON_ALPHANUMERIC_REGEX.replace(out, "")

        // 3. Deduplicate repeated letters if the word is long enough
        if (deduplicate && out.length > 3) {
            out = DUPLICATE_LETTERS_REGEX.replace(out, "$1")
        }

        return out
    }

    /**
     * Generates a replacement string of a given length.
     *
     * @param length The desired length of the replacement string.
     * @param symbol The symbol to use for replacement. If null, a random mix of @, * and # is used.
     * @return The replacement string.
     */
    fun replacement(length: Int, symbol: String?): String {
        if (length <= 0) return ""
        if (symbol != null) return symbol.repeat(length)

        return CharArray(length) { REPLACEMENT_CHARS.random() }.concatToString()
    }

    /**
     * Converts homoglyphs and special characters in a string to their Latin equivalents.
     *
     * @param str The string to convert.
     * @param strict Whether to perform the conversion.
     * @return The converted string, an empty string if input is null, or the original string if strict is false.
     */
    fun convert(str: String?, strict: Boolean): String {
        if (str == null) return ""
        if (!strict) return str

        val result = StringBuilder()
        var i = 0
        while (i < str.length) {
            var matched = false
            for (len in MAX_HOMOGLYPH_LENGTH downTo 1) {
                if (i + len <= str.length) {
                    val s = str.substring(i, i + len)
                    val replacement = HOMOGLYPHS[s]
                    if (replacement != null) {
                        result.append(replacement)
                        i += len
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                result.append(str[i])
                i++
            }
        }
        return result.toString()
    }

    private data class WordInfo(
        val original: String,
        val transformed: String,
        val illegal: String?,
        val pattern: String
    )

    /**
     * Replaces blacklisted words in a string with censored versions.
     *
     * @param originalString The string to process.
     * @param toCensorArray A list of patterns to censor. Patterns can use '*' as wildcards at the beginning or end.
     * @param replacement The symbol to use for censorship. If null, random symbols are used.
     * @param strict Whether to use strict normalization (e.g. homoglyph conversion).
     * @return The censored string.
     */
    fun replace(originalString: String, toCensorArray: List<String>, replacement: String? = null, strict: Boolean = true, deduplicate: Boolean = true): String {
        val preparedCensorArray = toCensorArray.mapIndexedNotNull { index, s ->
            val transformed = transform(convert(s, strict), deduplicate)
            if (transformed.isNotEmpty()) index to transformed else null
        }

        val preparedArray = originalString.split(' ').map { originalWord ->
            val latinizedWord = convert(originalWord, strict)
            val transformedWord = transform(latinizedWord, deduplicate)

            var censoredWord: String? = null
            var foundPatternIndex = -1

            for ((index, toReplace) in preparedCensorArray) {
                if (transformedWord.contains(toReplace)) {
                    censoredWord = toReplace
                    foundPatternIndex = index
                    break
                }
            }

            WordInfo(
                original = originalWord,
                transformed = transformedWord,
                illegal = censoredWord,
                pattern = if (foundPatternIndex != -1) toCensorArray[foundPatternIndex] else ""
            )
        }

        val censoredArray = mutableListOf<String>()
        for (word in preparedArray) {
            if (word.illegal != null) {
                val start = word.transformed.indexOf(word.illegal)
                val end = start + word.illegal.length

                val before = word.pattern.startsWith('*')
                val after = word.pattern.endsWith('*')

                val censored = when {
                    before && after -> replacement(word.transformed.length, replacement)
                    before -> replacement(end, replacement) + word.transformed.substring(end)
                    after -> word.transformed.substring(0, start) + replacement(word.transformed.length - start, replacement)
                    else -> word.transformed.substring(0, start) + replacement(end - start, replacement) + word.transformed.substring(end)
                }
                censoredArray.add(censored)
            } else {
                censoredArray.add(word.original)
            }
        }

        return censoredArray.joinToString(" ")
    }

    companion object {
        private val MAX_HOMOGLYPH_LENGTH = HOMOGLYPHS.keys.maxOf { it.length }

        private val SUBSTITUTIONS = mapOf(
            '0' to "o",
            '1' to "i",
            '2' to "z",
            '3' to "e",
            '4' to "a",
            '5' to "s",
            '6' to "b",
            '7' to "t",
            '8' to "b",
            '9' to "g",
            '$' to "s",
            '@' to "a",
            '!' to "i",
            '+' to "t"
        )

        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
        private val DUPLICATE_LETTERS_REGEX = Regex("([a-z])\\1+")
        private const val REPLACEMENT_CHARS = "@*#"
    }
}
