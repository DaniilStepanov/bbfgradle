//File A.java
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

public interface A {
   @NotNull
   String f();

   @NotNull
   String g();
}


//File Main.kt
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM

// WITH_RUNTIME

import kotlin.test.assertEquals

fun foo(block: () -> String) = block()

inline fun<reified T : Any> className(): String = T::class.java.getName()

fun box(): String {
    val x = foo() {
        className<String>()
    }

    assertEquals("java.lang.String", x)

    val y: A = object : A {
        override fun f(): String = foo { className<String>() }
        override fun g(): String = foo { className<Int>() }
    }

    assertEquals("java.lang.String", y.f())
    assertEquals("java.lang.Integer", y.g())

    return "OK"
}

