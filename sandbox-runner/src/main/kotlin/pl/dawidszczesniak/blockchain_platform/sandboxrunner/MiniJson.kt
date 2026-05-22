package pl.dawidszczesniak.blockchain_platform.sandboxrunner

internal object MiniJson {
    fun parse(text: String): Any? = Parser(text).parse()

    fun stringify(value: Any?): String = buildString {
        appendJsonValue(this, value, sortKeys = false)
    }

    fun stringifyCanonical(value: Any?): String = buildString {
        appendJsonValue(this, value, sortKeys = true)
    }

    private fun appendJsonValue(builder: StringBuilder, value: Any?, sortKeys: Boolean) {
        when (value) {
            null -> builder.append("null")
            is String -> appendJsonString(builder, value)
            is Boolean, is Int, is Long, is Double, is Float, is Short, is Byte -> builder.append(value.toString())
            is Number -> builder.append(value.toString())
            is Map<*, *> -> {
                builder.append('{')
                val entries = value.entries
                    .filter { it.key is String }
                    .map { it.key as String to it.value }
                    .let { if (sortKeys) it.sortedBy { entry -> entry.first } else it }
                entries.forEachIndexed { index, (key, itemValue) ->
                    if (index > 0) {
                        builder.append(',')
                    }
                    appendJsonString(builder, key)
                    builder.append(':')
                    appendJsonValue(builder, itemValue, sortKeys)
                }
                builder.append('}')
            }
            is Iterable<*> -> {
                builder.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) {
                        builder.append(',')
                    }
                    appendJsonValue(builder, item, sortKeys)
                }
                builder.append(']')
            }
            is Array<*> -> {
                builder.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) {
                        builder.append(',')
                    }
                    appendJsonValue(builder, item, sortKeys)
                }
                builder.append(']')
            }
            else -> appendJsonString(builder, value.toString())
        }
    }

    private fun appendJsonString(builder: StringBuilder, value: String) {
        builder.append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        builder.append("\\u")
                        builder.append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(character)
                    }
                }
            }
        }
        builder.append('"')
    }

    private class Parser(
        private val text: String,
    ) {
        private var index: Int = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != text.length) {
                throw IllegalArgumentException("Unexpected trailing JSON content at position $index.")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (index >= text.length) {
                throw IllegalArgumentException("Unexpected end of JSON input.")
            }
            return when (val character = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token '$character' at position $index.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            if (peek('}')) {
                expect('}')
                return linkedMapOf()
            }
            val values = linkedMapOf<String, Any?>()
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                values[key] = value
                skipWhitespace()
                when {
                    peek(',') -> {
                        expect(',')
                    }
                    peek('}') -> {
                        expect('}')
                        return values
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at position $index.")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            if (peek(']')) {
                expect(']')
                return emptyList()
            }
            val values = mutableListOf<Any?>()
            while (true) {
                values += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> expect(',')
                    peek(']') -> {
                        expect(']')
                        return values
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at position $index.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < text.length) {
                val character = text[index++]
                when (character) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (index >= text.length) {
                            throw IllegalArgumentException("Invalid JSON escape at end of input.")
                        }
                        val escaped = text[index++]
                        when (escaped) {
                            '"', '\\', '/' -> builder.append(escaped)
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) {
                                    throw IllegalArgumentException("Invalid unicode escape at position $index.")
                                }
                                val codePoint = text.substring(index, index + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid unicode escape at position $index.")
                                builder.append(codePoint.toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Invalid JSON escape '\\$escaped' at position $index.")
                        }
                    }
                    else -> builder.append(character)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string.")
        }

        private fun parseNumber(): Number {
            val startedAt = index
            if (peek('-')) {
                index += 1
            }
            parseDigits()
            var isFloatingPoint = false
            if (peek('.')) {
                isFloatingPoint = true
                index += 1
                parseDigits()
            }
            if (peek('e') || peek('E')) {
                isFloatingPoint = true
                index += 1
                if (peek('+') || peek('-')) {
                    index += 1
                }
                parseDigits()
            }
            val token = text.substring(startedAt, index)
            return if (isFloatingPoint) {
                token.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid JSON number '$token'.")
            } else {
                token.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid JSON number '$token'.")
            }
        }

        private fun parseDigits() {
            val startedAt = index
            while (index < text.length && text[index].isDigit()) {
                index += 1
            }
            if (startedAt == index) {
                throw IllegalArgumentException("Expected digit at position $index.")
            }
        }

        private fun parseLiteral(token: String, value: Any?): Any? {
            if (!text.regionMatches(index, token, 0, token.length)) {
                throw IllegalArgumentException("Expected '$token' at position $index.")
            }
            index += token.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index += 1
            }
        }

        private fun expect(expected: Char) {
            if (index >= text.length || text[index] != expected) {
                throw IllegalArgumentException("Expected '$expected' at position $index.")
            }
            index += 1
        }

        private fun peek(expected: Char): Boolean = index < text.length && text[index] == expected
    }
}
