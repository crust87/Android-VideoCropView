package com.crust87.videocropview

fun gcd(i: Int, j: Int): Int {
    var n = i
    var m = j
    while (m != 0) {
        val t = n % m
        n = m
        m = t
    }

    return Math.abs(n)
}