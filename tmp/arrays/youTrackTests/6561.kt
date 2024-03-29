// Original bug: KT-18241

package crs

fun parenB(p: () -> Unit) = p()
fun parenP() {}
fun parenBP(p: () -> () -> Unit): () -> Unit = p()
fun parenPP(): () -> Unit = {}
fun parenPB(p: (() -> Unit) -> Unit): (() -> Unit) -> Unit = p

fun some(p1: () -> Unit, p2: (() -> Unit) -> Unit) {
    parenB { p1() }
    parenP()
//    parenBP { p1 }()
//    parenPP()()
    (parenPB (p2)) {}
} 