// Original bug: KT-36773

fun testUIntRangeLiteral(a: UInt, b: UInt): Int {
    var s = 0
    for (x in a .. b) {
        s += x.toInt()
    }
    return s
}

fun testULongRangeLiteral(a: ULong, b: ULong): Int {
    var s = 0
    for (x in a .. b) {
        s += x.toInt()
    }
    return s
}

fun testUIntUntil(a: UInt, b: UInt): Int {
    var s = 0
    for (x in a until b) {
        s += x.toInt()
    }
    return s
}

fun testULongUntil(a: ULong, b: ULong): Int {
    var s = 0
    for (x in a until b) {
        s += x.toInt()
    }
    return s
}

fun testUIntDownTo(a: UInt, b: UInt): Int {
    var s = 0
    for (x in a downTo b) {
        s += x.toInt()
    }
    return s
}

fun testULongDownTo(a: ULong, b: ULong): Int {
    var s = 0
    for (x in a downTo b) {
        s += x.toInt()
    }
    return s
}
