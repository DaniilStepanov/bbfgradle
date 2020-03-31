package com.stepanov.bbf.bugfinder.executor

import com.intellij.psi.PsiErrorElement
import com.stepanov.bbf.reduktor.util.getAllChildrenNodes
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.Stream
import com.stepanov.bbf.bugfinder.util.checkCompilingForAllBackends
import org.apache.log4j.Logger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

// Transformation is here only for PSIFactory
class TracesChecker(private val compilers: List<CommonCompiler>) : Transformation() {

    override val name = "TracesChecker"

    private object FalsePositivesTemplates {
        //Regex and replacing
        val exclErrorMessages = listOf(
            "IndexOutOfBoundsException"
        )
    }

    fun checkTest(text: String): List<CommonCompiler>? {
        var resText = text
        if (!resText.contains("fun main(")) {
            resText += "fun main(args: Array<String>) {\n" +
                    "    println(box())\n" +
                    "}"
        }
        val writer = BufferedWriter(FileWriter(CompilerArgs.pathToTmpFile))
        writer.write(resText)
        writer.close()
        val res = checkTest(resText, CompilerArgs.pathToTmpFile)
        File(CompilerArgs.pathToTmpFile).delete()
        return res
    }


    fun checkTest(text: String, pathToFile: String): List<CommonCompiler>? {
        val hash = text.hashCode()
        if (alreadyChecked.containsKey(hash)) {
            log.debug("ALREADY CHECKED!!!")
            return alreadyChecked[hash]!!
        }

        val psiFile = psiFactory.createFile(text)
        //Check for syntax correctness
        if (psiFile.node.getAllChildrenNodes().any { it.psi is PsiErrorElement }) {
            log.debug("Not correct syntax")
            alreadyChecked[hash] = null
            return null
        }

        log.debug("Trying to compile with main function:")
        if (!compilers.checkCompilingForAllBackends(psiFile)) {
            log.debug("Cannot compile with main")
            return null
        }

        log.debug("Executing traced code:\n$text")
        val results = mutableListOf<Pair<CommonCompiler, String>>()
        for (comp in compilers) {
            val status = comp.compile(pathToFile)
            if (status.status == -1)
                return null
            val res = comp.exec(status.pathToCompiled)
            val errors = comp.exec(status.pathToCompiled, Stream.ERROR)
            log.debug("Result of ${comp.compilerInfo}: $res\n")
            log.debug("Errors: $errors")
            if (FalsePositivesTemplates.exclErrorMessages.any { errors.contains(it) })
                return null
            results.add(comp to res.trim())
        }
        val groupedRes = results.groupBy({ it.second }, valueTransform = { it.first })
        return if (groupedRes.size == 1) {
            null
        } else {
            val res = groupedRes.map { it.value.first() }
            alreadyChecked[hash] = res
            res
        }
    }

    override fun transform() = TODO()

    var alreadyChecked: HashMap<Int, List<CommonCompiler>?> = HashMap()
    private val log = Logger.getLogger("bugFinderLogger")
}
