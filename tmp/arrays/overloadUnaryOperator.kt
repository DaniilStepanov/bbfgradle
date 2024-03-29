// DONT_TARGET_EXACT_BACKEND: WASM
// WASM_MUTE_REASON: STDLIB_COLLECTIONS
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME
class ArrayWrapper<T>() {
    val contents = ArrayList<T>()

    fun add(item: T) {
       contents.add(item)
    }

    operator fun unaryMinus(): ArrayWrapper<T> {
        val result = ArrayWrapper<T>()
        result.contents.addAll(contents)
        result.contents.reverse()
        return result
    }

    operator fun get(index: Int): T {
        return contents.get(index)!!
    }
}

fun box(): String {
    val v1 = ArrayWrapper<String>()
    v1.add("foo")
    v1.add("bar")
    val v2 = -v1
    return if (v2[0] == "bar" && v2[1] == "foo") "OK" else "fail"
}
