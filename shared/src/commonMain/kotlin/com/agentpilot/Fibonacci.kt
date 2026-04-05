package com.agentpilot

fun fibonacci(n: Int): Long {
    if (n <= 1) return n.toLong()
    var a = 0L
    var b = 1L
    repeat(n - 1) {
        val next = a + b
        a = b
        b = next
    }
    return b
}
