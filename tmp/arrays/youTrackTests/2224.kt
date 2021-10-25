// Original bug: KT-42562

import kotlin.reflect.*

object E

operator fun E.getValue(receiver: Any?, property: KProperty<*>): String =
    if (property.returnType.classifier == String::class) "OK" else "Fail"

fun box(): String = {
    val x: String by E
    x
}()
