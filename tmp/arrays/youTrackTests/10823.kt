// Original bug: KT-962

fun stdout(): java.io.PrintStream? {
  System.out?.println("stdout")
  return System.out
}

fun main(args : Array<String>) {
  stdout()?.println("Hello, world!")
}
