package me.diamondforge.horizon.api.sanitizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SanitizerTest {
    private val sanitizer = Sanitizer()

    // ── transform ────────────────────────────────────────────────────────────

    @Test
    fun `transform - basic substitutions and deduplication`() {
        assertEquals("this", sanitizer.transform("t-h.1.\$"))
        assertEquals("this", sanitizer.transform("thhhhiiiisss"))
    }

    @Test
    fun `transform - empty string returns empty`() {
        assertEquals("", sanitizer.transform(""))
    }

    @Test
    fun `transform - special chars with substitutions`() {
        // ! → i, - and . stripped → "iii", length 3 → no dedup
        assertEquals("iii", sanitizer.transform("---...!!!"))
    }

    @Test
    fun `transform - short string skips deduplication`() {
        // "aaa" has length 3 after stripping → dedup is NOT applied (only if length > 3)
        assertEquals("aaa", sanitizer.transform("aaa"))
    }

    @Test
    fun `transform - string longer than 3 gets deduplicated`() {
        // "aaaab" → dedup → "ab"
        assertEquals("ab", sanitizer.transform("aaaab"))
    }

    @Test
    fun `transform - deduplication disabled keeps repeated chars`() {
        assertEquals("aaaab", sanitizer.transform("aaaab", deduplicate = false))
    }

    @Test
    fun `transform - all digit substitutions`() {
        // 0→o, 1→i, 2→z, 3→e, 4→a, 5→s, 6→b, 7→t, 8→b, 9→g
        assertEquals("o", sanitizer.transform("0"))
        assertEquals("i", sanitizer.transform("1"))
        assertEquals("z", sanitizer.transform("2"))
        assertEquals("e", sanitizer.transform("3"))
        assertEquals("a", sanitizer.transform("4"))
        assertEquals("s", sanitizer.transform("5"))
        assertEquals("b", sanitizer.transform("6"))
        assertEquals("t", sanitizer.transform("7"))
        assertEquals("b", sanitizer.transform("8"))
        assertEquals("g", sanitizer.transform("9"))
    }

    @Test
    fun `transform - special char substitutions`() {
        // $→s, @→a, !→i, +→t
        assertEquals("s", sanitizer.transform("\$"))
        assertEquals("a", sanitizer.transform("@"))
        assertEquals("i", sanitizer.transform("!"))
        assertEquals("t", sanitizer.transform("+"))
    }

    @Test
    fun `transform - uppercase becomes lowercase and deduplicates`() {
        // "HELLO" → "hello" → dedup (ll→l) → "helo"
        assertEquals("helo", sanitizer.transform("HELLO"))
    }

    // ── replacement ──────────────────────────────────────────────────────────

    @Test
    fun `replacement - length zero returns empty string`() {
        assertEquals("", sanitizer.replacement(0, "*"))
        assertEquals("", sanitizer.replacement(-1, "*"))
    }

    @Test
    fun `replacement - repeats given symbol`() {
        assertEquals("****", sanitizer.replacement(4, "*"))
        assertEquals("###", sanitizer.replacement(3, "#"))
    }

    @Test
    fun `replacement - null symbol returns random chars of correct length`() {
        val result = sanitizer.replacement(10, null)
        assertEquals(10, result.length)
        assertTrue(result.all { it in "@*#" })
    }

    // ── convert ──────────────────────────────────────────────────────────────

    @Test
    fun `convert - null returns empty string`() {
        assertEquals("", sanitizer.convert(null, true))
    }

    @Test
    fun `convert - strict false returns original string unchanged`() {
        val input = "𝔱𝔥𝔦𝔰"
        assertEquals(input, sanitizer.convert(input, false))
    }

    @Test
    fun `convert - empty string returns empty`() {
        assertEquals("", sanitizer.convert("", true))
    }

    @Test
    fun `convert - emoji homoglyphs`() {
        assertEquals("012", sanitizer.convert("0️⃣1️⃣2️⃣", true))
    }

    @Test
    fun `convert - mixed homoglyphs`() {
        assertEquals("ExAmPlE Clean up this text", sanitizer.convert("ỆᶍǍᶆṔƚÉ ℭ𝔩𝔢𝔞𝔫 𝓾𝓹 𝕥𝕙𝕚𝕤 🆃🅴🆇🆃", true))
    }

    // ── replace ──────────────────────────────────────────────────────────────

    @Test
    fun `replace - empty blacklist returns original`() {
        val input = "nothing to censor here"
        assertEquals(input, sanitizer.replace(input, emptyList()))
    }

    @Test
    fun `replace - no match returns original`() {
        val input = "hello world"
        assertEquals(input, sanitizer.replace(input, listOf("badword")))
    }

    @Test
    fun `replace - exact match no wildcard`() {
        assertEquals("**** world", sanitizer.replace("this world", listOf("this"), "*"))
    }

    @Test
    fun `replace - prefix wildcard`() {
        // "*pple" → censors from start up to end of match
        assertEquals("i **** ******** ** ****", sanitizer.replace("i like pineapple on pizza.", listOf("*pple", "pizz*", "*ik*", "on"), "*"))
    }

    @Test
    fun `replace - suffix wildcard`() {
        val result = sanitizer.replace("badword here", listOf("bad*"), "*")
        assertTrue(result.startsWith("*"))
    }

    @Test
    fun `replace - both wildcards censors whole word`() {
        val result = sanitizer.replace("something here", listOf("*omething*"), "*")
        assertTrue(result.startsWith("*") && !result.contains("something"))
    }

    @Test
    fun `replace - multiple different words censored`() {
        val result = sanitizer.replace("foo and bar", listOf("foo", "bar"), "*")
        assertTrue(result.contains("***") && !result.contains("foo") && !result.contains("bar"))
    }

    @Test
    fun `replace - null replacement uses random symbols`() {
        val result = sanitizer.replace("badword here", listOf("badword"), null)
        val censoredWord = result.split(" ").first()
        assertTrue(censoredWord.all { it in "@*#" })
        assertEquals(7, censoredWord.length)
    }

    @Test
    fun `replace - strict mode catches homoglyph bypasses`() {
        val input = "t-h.1.\$ thhhhiiiisss 𝔱𝔥𝔦𝔰 𝕥𝕙𝕚𝕤 ᴛʜɪꜱ"
        val result = sanitizer.replace(input, listOf("this"), "*", strict = true)
        val words = result.split(" ")
        assertEquals(5, words.count { it == "****" })
    }

    @Test
    fun `replace - non-strict mode does not convert homoglyphs`() {
        val input = "𝔱𝔥𝔦𝔰"
        // strict=false → homoglyphs not converted → pattern 'this' won't match
        val result = sanitizer.replace(input, listOf("this"), "*", strict = false)
        assertEquals(input, result)
    }

    @Test
    fun `replace - empty pattern from homoglyph with strict false does not censor`() {
        val input = "This is a normal sentence."
        val result = sanitizer.replace(input, listOf("𝔱𝔥𝔦𝔰"), "*", strict = false)
        assertEquals(input, result)
    }

    @Test
    fun `replace - empty string returns empty`() {
        assertEquals("", sanitizer.replace("", listOf("badword"), "*"))
    }

    @Test
    fun `replace - same pattern matches multiple words`() {
        assertEquals("**** and ****", sanitizer.replace("this and this", listOf("this"), "*"))
    }

    @Test
    fun `replace - double space is preserved`() {
        assertEquals("foo  ****", sanitizer.replace("foo  this", listOf("this"), "*"))
    }
}
