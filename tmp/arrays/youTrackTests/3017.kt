// Original bug: KT-25617

// CollectionExtensions.kt
// Time Complexity: O(N)

fun <T> Iterable<T>.updated(old: T, new: T): List<T> = map { if (it == old) new else it }
fun <T> Iterable<T>.updated(index: Int, new: T): List<T> = mapIndexed { i, e -> if (i == index) new else e }
