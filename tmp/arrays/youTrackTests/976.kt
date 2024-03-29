// Original bug: KT-44671

import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.*

fun myRun(c: () -> Unit) {
    c()
}

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
fun main() {
    var contiuation: Continuation<Unit>? = null
    val c: suspend () -> Unit = {
        suspendCoroutine {
            contiuation = it
        }
    }
    var exception: Throwable? = null

    myRun {
        c.startCoroutineUninterceptedOrReturn(Continuation(EmptyCoroutineContext) {
            exception = it.exceptionOrNull()
        })
    }

    contiuation?.resumeWithException(RuntimeException("OK"))

    println(exception)
}
