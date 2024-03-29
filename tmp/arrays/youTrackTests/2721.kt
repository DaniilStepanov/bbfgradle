// Original bug: KT-38664

package bug

interface C<A : Any, B : Any> { // actually in a separate file
    fun foo(a: A): B
    companion object {
        inline fun <reified A : Any, reified B : Any> inlinefun(crossinline fooParam: (A) -> B): C<A, B> {
            return object : C<A, B> {
                override fun foo(a: A) = fooParam(a)
            }
        }
    }
}


data class A(val s: String) {
    companion object : C<A, String> by C.inlinefun(
            fooParam = { it.s }
    )
}

class OtherB {
    var a: String? = null
}

data class B(val a: A?) {
    companion object : C<B, OtherB> by C.inlinefun(
            fooParam = {
                OtherB().apply {
                    a = it.a?.let(A::foo) // <-- Crashes the compiler, IDE does not complain
                }
            }
    )
}
