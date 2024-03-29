// Original bug: KT-22735

interface Level2
class BiType<out X, out Y> {}

fun <X> pullXno(x: X): BiType<X, Nothing> = TODO()
fun <Y> pullYno(y: Y): BiType<Nothing, Y> = TODO()

// NB: Same return types.
fun <X> pullXnoc(x: X): BiType<X, Level2> = pullXno(x)
fun <X> pullYnoc(l2: Level2): BiType<X, Level2> = pullYno(l2)

fun <X> adjustIt(f1: () -> X, f2: () -> X): X = TODO()

fun <X> callAdjustIt(x: X, l2: Level2) {
    adjustIt({ pullXnoc(x) }, { pullYnoc(l2) })  // Refactor here.
}
