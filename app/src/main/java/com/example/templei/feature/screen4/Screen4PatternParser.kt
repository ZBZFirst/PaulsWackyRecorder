package com.example.templei.feature.screen4

/**
 * Small Tidal-inspired parser for one pattern stream.
 *
 * Supported grammar:
 * - tokens separated by whitespace
 * - `~` for rests
 * - `( ... )` for grouped sequences
 * - `*N` for repeating a token or group
 * - `sampleId{gain=0.8,pan=-0.2,speed=1.2}` for per-event parameters
 */
class Screen4PatternParser {
    fun parse(source: String): Result<Screen4ParsedPattern> {
        val lexer = Lexer(source)
        val tokens = lexer.lex().getOrElse { return Result.failure(it) }
        val parser = Parser(tokens)
        return parser.parse()
    }

    private enum class TokenType {
        IDENTIFIER,
        NUMBER,
        REST,
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        EQUALS,
        STAR,
        COMMA,
        EOF,
    }

    private data class Token(
        val type: TokenType,
        val text: String,
        val index: Int,
    )

    private class Lexer(private val source: String) {
        private var cursor: Int = 0

        fun lex(): Result<List<Token>> {
            val tokens = mutableListOf<Token>()
            while (cursor < source.length) {
                val current = source[cursor]
                when {
                    current.isWhitespace() -> cursor++
                    current == '~' -> tokens += consume(TokenType.REST, "~")
                    current == '(' -> tokens += consume(TokenType.LPAREN, "(")
                    current == ')' -> tokens += consume(TokenType.RPAREN, ")")
                    current == '{' -> tokens += consume(TokenType.LBRACE, "{")
                    current == '}' -> tokens += consume(TokenType.RBRACE, "}")
                    current == '=' -> tokens += consume(TokenType.EQUALS, "=")
                    current == '*' -> tokens += consume(TokenType.STAR, "*")
                    current == ',' -> tokens += consume(TokenType.COMMA, ",")
                    current.isDigit() || current == '-' || current == '.' -> tokens += lexNumber()
                    current.isIdentifierStart() -> tokens += lexIdentifier()
                    else -> return Result.failure(IllegalArgumentException("Unexpected token '$current' at character ${cursor + 1}."))
                }
            }
            tokens += Token(TokenType.EOF, "", source.length)
            return Result.success(tokens)
        }

        private fun consume(type: TokenType, text: String): Token {
            val token = Token(type, text, cursor)
            cursor++
            return token
        }

        private fun lexIdentifier(): Token {
            val start = cursor
            while (cursor < source.length && source[cursor].isIdentifierPart()) {
                cursor++
            }
            return Token(TokenType.IDENTIFIER, source.substring(start, cursor), start)
        }

        private fun lexNumber(): Token {
            val start = cursor
            var hasDecimal = false
            if (source[cursor] == '-') {
                cursor++
            }
            while (cursor < source.length) {
                val current = source[cursor]
                when {
                    current.isDigit() -> cursor++
                    current == '.' && !hasDecimal -> {
                        hasDecimal = true
                        cursor++
                    }
                    else -> break
                }
            }
            return Token(TokenType.NUMBER, source.substring(start, cursor), start)
        }

        private fun Char.isIdentifierStart(): Boolean {
            return isLetter() || this == '_' || this == '/'
        }

        private fun Char.isIdentifierPart(): Boolean {
            return isLetterOrDigit() || this == '_' || this == '-' || this == '/' || this == '.'
        }
    }

    private class Parser(private val tokens: List<Token>) {
        private var cursor: Int = 0

        fun parse(): Result<Screen4ParsedPattern> {
            return runCatching {
                val sequence = parseSequence(stopAt = TokenType.EOF)
                expect(TokenType.EOF)
                Screen4ParsedPattern(sequence)
            }
        }

        private fun parseSequence(stopAt: TokenType): Screen4PatternNode {
            val children = mutableListOf<Screen4PatternNode>()
            while (!check(stopAt) && !check(TokenType.EOF)) {
                children += parseFactor()
            }
            if (children.isEmpty()) {
                throw parseError(peek(), "Pattern must contain at least one token.")
            }
            return if (children.size == 1) children.first() else Screen4PatternNode.Sequence(children)
        }

        private fun parseFactor(): Screen4PatternNode {
            var node = parsePrimary()
            if (match(TokenType.STAR)) {
                val count = expect(TokenType.NUMBER).text.toIntOrNull()
                    ?: throw parseError(previous(), "Repeat count must be an integer.")
                if (count <= 0) {
                    throw parseError(previous(), "Repeat count must be greater than zero.")
                }
                node = Screen4PatternNode.Repeat(node, count)
            }
            return node
        }

        private fun parsePrimary(): Screen4PatternNode {
            return when {
                match(TokenType.REST) -> Screen4PatternNode.Rest
                match(TokenType.LPAREN) -> {
                    val node = parseSequence(stopAt = TokenType.RPAREN)
                    expect(TokenType.RPAREN)
                    node
                }
                check(TokenType.IDENTIFIER) -> parseEvent()
                else -> throw parseError(peek(), "Expected a sample id, rest, or group.")
            }
        }

        private fun parseEvent(): Screen4PatternNode.Event {
            val sampleId = expect(TokenType.IDENTIFIER).text
            val params = if (match(TokenType.LBRACE)) parseParameters() else Screen4EventParameters()
            return Screen4PatternNode.Event(sampleId = sampleId, params = params)
        }

        private fun parseParameters(): Screen4EventParameters {
            var gain = 1f
            var pan = 0f
            var speed = 1f
            var consumedAtLeastOne = false

            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                val key = expect(TokenType.IDENTIFIER).text.lowercase()
                expect(TokenType.EQUALS)
                val value = expect(TokenType.NUMBER).text.toFloatOrNull()
                    ?: throw parseError(previous(), "Expected a numeric parameter value.")
                when (key) {
                    "gain" -> gain = value
                    "pan" -> pan = value
                    "speed" -> speed = value
                    else -> throw parseError(previous(), "Unknown event parameter '$key'.")
                }
                consumedAtLeastOne = true
                if (!match(TokenType.COMMA)) break
            }

            if (!consumedAtLeastOne) {
                throw parseError(peek(), "Parameter block cannot be empty.")
            }

            expect(TokenType.RBRACE)
            return Screen4EventParameters(
                gain = gain,
                pan = pan,
                speed = speed,
            )
        }

        private fun match(type: TokenType): Boolean {
            if (!check(type)) return false
            cursor++
            return true
        }

        private fun expect(type: TokenType): Token {
            if (check(type)) {
                cursor++
                return previous()
            }
            throw parseError(peek(), "Expected ${type.name.lowercase()}.")
        }

        private fun check(type: TokenType): Boolean {
            return peek().type == type
        }

        private fun peek(): Token = tokens[cursor]

        private fun previous(): Token = tokens[cursor - 1]

        private fun parseError(token: Token, message: String): IllegalArgumentException {
            return IllegalArgumentException("$message Token position ${token.index + 1}.")
        }
    }
}
