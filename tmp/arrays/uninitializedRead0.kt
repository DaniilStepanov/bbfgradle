// WITH_RUNTIME

// IGNORE_BACKEND: WASM
// WASM_MUTE_REASON: REFLECTION

import kotlin.UninitializedPropertyAccessException

fun box(): String {
    lateinit var str: String
    var str2: String = ""
    try {
        str2 = str
        return "Should throw an exception"
    }
    catch (e: UninitializedPropertyAccessException) {
        return "OK"
    }
    catch (e: Throwable) {
        return "Unexpected exception: ${e::class}"
    }
}
