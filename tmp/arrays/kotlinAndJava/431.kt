//File Main.kt
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM

// WITH_REFLECT

import java.util.HashMap

val a by lazy {
    with(HashMap<String, R>()) {
        put("result", object : R {
            override fun result(): String = "OK"
        })
        this
    }
}

fun box(): String {
    val r = a["result"]!!

    // Check that reflection won't fail
    r.javaClass.getEnclosingMethod().toString()

    return r.result()
}



//File R.java
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

public interface R {
   @NotNull
   String result();
}
