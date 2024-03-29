// Original bug: KT-32189

class B {
    class Builder
}

typealias ApplyRestrictions = B.Builder.() -> B.Builder

fun applyRestrictions1(): ApplyRestrictions = TODO()
fun applyRestrictions2() = applyRestrictions1()
fun <K> applyRestrictions3(e: K) = applyRestrictions1()

fun buildB() {
    val a1 = applyRestrictions1()
    val a2 = applyRestrictions2()
    val a3 = applyRestrictions3("foo")

    B.Builder().a1() // OK
    B.Builder().a2() // OK
    B.Builder().a3() // Error
}
