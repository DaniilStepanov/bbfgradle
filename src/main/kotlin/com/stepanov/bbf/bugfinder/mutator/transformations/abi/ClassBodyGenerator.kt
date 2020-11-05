package com.stepanov.bbf.bugfinder.mutator.transformations.abi

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.UsageSamplesGeneratorWithStLibrary
import com.stepanov.bbf.bugfinder.util.getTrue
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random

open class ClassBodyGenerator(
    private val file: KtFile,
    private val ctx: BindingContext,
    private val gClass: GClass): DSGenerator(file, ctx) {

    private fun generateOverridings(specifiers: List<KotlinType>): String {
        val res = StringBuilder()
        for (specifier in specifiers) {
            val membersToOverride = UsageSamplesGeneratorWithStLibrary.getMembersToOverride(specifier)
            val filteredMembers =
                if (gClass.modifiers.let { it.contains("interface") || it.contains("abstract") })
                    membersToOverride.filter { Random.getTrue(30) }
                else
                    membersToOverride
            for (member in filteredMembers) {
                val rtv = member.toString().substringAfterLast(":").substringBefore(" defined")
                if (member is PropertyDescriptor) {
                    val varOrVal = if (member.isVar) "var" else "val"
                    val name = member.name
                    res.append(
                        "\noverride $varOrVal $name: $rtv\n" +
                                "    get() = TODO()\n"
                    )
                } else if (member is FunctionDescriptor) {
                    val f = member.toString().substringAfter("fun").substringBefore(" defined")
                    res.append("\noverride fun$f = TODO()\n")
                }
            }
        }
        return res.toString()
    }

    private fun generateFields(): String {
        val res = StringBuilder()
        val randomFunGenerator = RandomFunctionGenerator(file, ctx)
        with(res) {
            repeat(5) {
                append("\n")
                append(randomFunGenerator.generate()?.text)
                append("\n")
            }
        }
        return res.toString()
    }


    fun generate(specifiers: List<KotlinType>) =
        with(StringBuilder()) {
            append(generateOverridings(specifiers))
            append(generateFields())
        }.toString()


    override fun generate(): PsiElement? {
        TODO("Not yet implemented")
    }

}