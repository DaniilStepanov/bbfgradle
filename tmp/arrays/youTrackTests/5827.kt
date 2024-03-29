// Original bug: KT-32038

@file:UseExperimental(ExperimentalTypeInference::class)

import kotlin.experimental.ExperimentalTypeInference

interface MyFlow<out T>
interface MyFlowCollector<in T>

fun <F> flow(@BuilderInference block: MyFlowCollector<F>.() -> Unit): MyFlow<F> = TODO()

interface SendChannel<in E> {
    fun send(element: E)
}

fun <P> produce(@BuilderInference block: SendChannel<P>.() -> Unit) {}

fun <C> MyFlow<C>.collect(action: (C) -> Unit) {}

private fun <T> MyFlow<T>.idScoped(): MyFlow<T> {
    return flow {
        produce { // Error in NI, OK in OI
            collect {
                send(it)
            }
        }
    }
}
