//File A.java
import kotlin.Metadata;

public final class A {
   public final int component1() {
      return 1;
   }

   public final int component2() {
      return 2;
   }
}


//File Main.kt



fun box() : String {
    var (a, b) = A()

    val local = {
        a = 3
    }
    local()
    return if (a == 3 && b == 2) "OK" else "fail"
}

