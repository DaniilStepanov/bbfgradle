// Original bug: KT-36225

class Builder<Base : Any> {
    @Deprecated(message = "", level = DeprecationLevel.WARNING, replaceWith = ReplaceWith("foo(list)"))
    inline fun <reified T: Base> addFoo(list: List<T>) {

    }

    inline fun <reified T: Base> foo(list: List<T>) {

    }

}

fun test() {
    val b = Builder<Number>()
    b.addFoo(listOf(1))
}
