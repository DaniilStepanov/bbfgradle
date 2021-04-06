// Original bug: KT-39488
// Duplicated bug: KT-21778

class Example {
    private val objectExpression = object {
        inline fun inlineFunction() = Unit
    }
    
    fun run() = println(objectExpression.inlineFunction())
}

fun main()= Example().run()
