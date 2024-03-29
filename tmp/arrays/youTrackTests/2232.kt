// Original bug: KT-42554

import kotlin.reflect.jvm.reflect

class C {
    val x: suspend (String) -> Unit = { OK: String -> }
}

fun box(): String {
    return C().x.reflect()?.parameters?.singleOrNull()?.name ?: "null"
}
