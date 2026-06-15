package com.mrndstvndv.search.util

import kotlin.math.pow

object CalculatorEngine {
    private val expressionRegex = Regex("^[0-9+\\-*/().\\s]+$")

    fun isExpression(input: String): Boolean {
        val cleaned = input.trim()
        return cleaned.isNotEmpty() && expressionRegex.matches(cleaned)
    }

    fun compute(input: String): String? {
        val cleaned = input.trim()
        if (!isExpression(cleaned)) return null
        val value = evaluateExpression(cleaned) ?: return null
        return formatCalculatorResult(value)
    }

    private fun formatCalculatorResult(value: Double): String =
        "%.8f".format(value).trimEnd('0').trimEnd('.')

    private fun evaluateExpression(expression: String): Double? {
        return try {
            object {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    pos++
                    ch = if (pos < expression.length) expression[pos].code else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < expression.length) {
                        throw IllegalArgumentException(
                            "Unexpected: ${expression[pos]}",
                        )
                    }
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        x =
                            when {
                                eat('+'.code) -> x + parseTerm()
                                eat('-'.code) -> x - parseTerm()
                                else -> return x
                            }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        x =
                            when {
                                eat('*'.code) -> x * parseFactor()
                                eat('/'.code) -> x / parseFactor()
                                else -> return x
                            }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()

                    val startPos = pos
                    val x: Double =
                        when {
                            eat('('.code) -> {
                                val inner = parseExpression()
                                if (!eat(
                                        ')'.code,
                                    )
                                ) {
                                    throw IllegalArgumentException(
                                        "Missing closing parenthesis",
                                    )
                                }
                                inner
                            }

                            ch in '0'.code..'9'.code || ch == '.'.code -> {
                                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                                expression.substring(startPos, pos).toDouble()
                            }

                            else -> throw IllegalArgumentException(
                                "Unexpected: ${if (ch == -1) "end" else expression[pos]}",
                            )
                        }

                    return if (eat('^'.code)) {
                        x.pow(parseFactor())
                    } else {
                        x
                    }
                }
            }.parse()
        } catch (_: Exception) {
            null
        }
    }
}
