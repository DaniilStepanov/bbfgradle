package com.stepanov.bbf.bugfinder.executor

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.reduktor.executor.CompilerTestChecker
import com.stepanov.bbf.reduktor.executor.error.Error
import com.stepanov.bbf.reduktor.util.getAllChildrenNodes
import com.stepanov.bbf.reduktor.util.getAllParentsWithoutNode
import com.stepanov.bbf.reduktor.util.replaceThis
import org.apache.log4j.Logger
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import org.jacoco.core.data.ExecutionDataStore
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

open class MultiCompilerCrashChecker(private val compiler: CommonCompiler?) : CompilerTestChecker, Transformation() {

    override val name = "MultiCompilerCrashChecker"

    override fun removeNodeIfPossible(file: KtFile, node: ASTNode): Boolean {
        val tmp = KtPsiFactory(file.project).createWhiteSpace("\n")
        return replaceNodeIfPossible(file, node, tmp.node)
    }

    override fun removeNodeIfPossible(file: FileASTNode, node: ASTNode) {
        val ktFile = file.psi as KtFile
        removeNodeIfPossible(ktFile, node)
    }

    override fun removeNodesIfPossible(file: KtFile, nodes: List<ASTNode>): Boolean {
        val copies = mutableListOf<ASTNode>()
        val whiteSpaces = mutableListOf<ASTNode>()
        nodes.forEach { copies.add(it.copyElement()); whiteSpaces.add(KtPsiFactory(file.project).createWhiteSpace("\n").node) }
        for ((i, node) in nodes.withIndex()) {
            for (p in node.getAllParentsWithoutNode()) {
                try {
                    p.replaceChild(node, whiteSpaces[i])
                    break
                } catch (e: AssertionError) {
                }
            }
        }
        if (!checkTest(file.text)) {
            for ((i, node) in nodes.withIndex()) {
                for (p in whiteSpaces[i].getAllParentsWithoutNode()) {
                    try {
                        p.replaceChild(whiteSpaces[i], node)
                        break
                    } catch (e: AssertionError) {
                    }
                }
            }
            return false
        } else return true
    }


    override fun replaceNodeIfPossible(file: KtFile, node: ASTNode, replacement: ASTNode): Boolean {
        if (node.text.isEmpty() || node == replacement) return checkTest(file.text, file.name)

        //If we trying to replace parent node to it child
        if (node.getAllChildrenNodes().contains(replacement)) {
            val backup = node.copyElement()
            node.replaceThis(replacement)
            if (!checkTest(file.text, file.name)) {
                log.debug("REPLACING BACK")
                replacement.replaceThis(backup)
            } else {
                log.debug("SUCCESSFUL DELETING")
                return true
            }
        }

        //Else
        for (p in node.getAllParentsWithoutNode()) {
            try {
                val oldText = file.text
                if ((node as TreeElement).treeParent !== p) continue
                p.replaceChild(node, replacement)
                if (oldText == file.text)
                    continue
                if (!checkTest(file.text, file.name)) {
                    log.debug("REPLACING BACK")
                    p.replaceChild(replacement, node)
                    return false
                } else {
                    log.debug("SUCCESSFUL DELETING")
                    return true
                }
            } catch (e: AssertionError) {
                log.debug("Exception while deleting ${node.text} from $p")
            }
        }
        return false
    }

    fun checkErrsMatching(a: String, b: String): Double {
        val diffs = patch.diffMain(a, b)
        var sameNum = 0
        var difNum = 0
        for (dif in diffs) {
            when (dif.operation.name) {
                "EQUAL" -> sameNum += dif.text.length
                else -> difNum += dif.text.length
            }
        }
        return if (sameNum == 0) Double.MAX_VALUE else difNum.toDouble() / sameNum.toDouble()
    }

    fun isAlreadyCheckedOrWrong(text: String): Pair<Boolean, Boolean> {
        val hash = text.hashCode()
        if (alreadyChecked.containsKey(hash)) {
            log.debug("ALREADY CHECKED!!!")
            // TODO A null check might be necessary: a NPE was caught in the same place at isCoverageAlreadyCollected()
            return true to alreadyChecked[hash]!!
        }
        if (psiFactory.createFile(text).node.getAllChildrenNodes().any { it.psi is PsiErrorElement }) {
            log.debug("Not correct syntax")
            alreadyChecked[hash] = false
            return true to false
        }
        return false to false
    }

    override fun checkTest(text: String): Boolean = checkTest(text, pathToFile)

    override fun checkTest(text: String, pathToFile: String): Boolean {
        val firstCheck = isAlreadyCheckedOrWrong(text)
        if (firstCheck.first) return firstCheck.second
//        val hash = text.hashCode()
//        if (alreadyChecked.containsKey(hash)) {
//            log.debug("ALREADY CHECKED!!!")
//            return alreadyChecked[hash]!!
//        }
//        //Check for syntax correctness
//        if (psiFactory.createFile(text).node.getAllChildrenNodes().any { it.psi is PsiErrorElement }) {
//            log.debug("Not correct syntax")
//            alreadyChecked[hash] = false
//            return false
//        }
        val oldText = File(pathToFile).bufferedReader().readText()
        var writer = File(pathToFile).bufferedWriter()
        writer.write(text)
        writer.close()
        val res = compiler!!.isCompilerBug(pathToFile)
        writer = File(pathToFile).bufferedWriter()
        writer.write(oldText)
        writer.close()
        alreadyChecked[text.hashCode()] = res
        return res
    }

    override fun checkTest(tree: List<ASTNode>): Boolean {
        if (tree.isEmpty()) return false
        val text = StringBuilder()
        for (el in tree)
            text.append(el.text)
        log.debug("Checking : $text")
        return checkTest(text.toString())
    }

    private fun isCoverageAlreadyCollected(text: String): Pair<Boolean, Pair<Boolean, ExecutionDataStore>?> {
        val hash = text.hashCode()
        if (alreadyCheckedCoverage.containsKey(hash)) {
            log.debug("ALREADY CHECKED!!!")
            return try {
                true to alreadyCheckedCoverage[hash]!!
            } catch (e: NullPointerException) {
                false to null
            }
        }
        return false to null
    }

    override fun getExecutionDataWithStatus(text: String): Pair<Boolean, ExecutionDataStore> = getExecutionDataWithStatus(text, pathToFile)

    override fun getExecutionDataWithStatus(text: String, pathToFile: String): Pair<Boolean, ExecutionDataStore> {
        val firstCheck = isCoverageAlreadyCollected(text)
        if (firstCheck.first) return firstCheck.second!!
        val oldText = File(pathToFile).bufferedReader().readText()
        var writer = File(pathToFile).bufferedWriter()
        writer.write(text)
        writer.close()
        val res = compiler!!.getExecutionDataWithStatus(pathToFile)
        writer = File(pathToFile).bufferedWriter()
        writer.write(oldText)
        writer.close()
        alreadyCheckedCoverage[text.hashCode()] = res
        return res
    }

    override fun getExecutionDataWithStatus(tree: List<ASTNode>): Pair<Boolean, ExecutionDataStore> {
        val text = StringBuilder()
        for (el in tree)
            text.append(el.text)
        log.debug("Checking : $text")
        return getExecutionDataWithStatus(text.toString())
    }

    override fun init(compilingPath: String, psiFactory: KtPsiFactory?): Error {
        pathToFile = CompilerArgs.pathToTmpFile
        errs = compiler?.getErrorMessage(compilingPath) ?: ""
        return Error("")
    }

    override fun reinit(): Error {
        //init(compilingPath)
        return Error("")
    }


    override fun transform() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getErrorInfo(): Error =
        Error("")
    override fun getErrorMessage(): String = errs


    lateinit var compilingPath: String
    override lateinit var pathToFile: String
    private lateinit var errs: String

    private val log = Logger.getLogger("reducerLogger")
    private val patch = DiffMatchPatch()
    private val threshold = 0.5
    override var alreadyChecked = HashMap<Int, Boolean>()
    override var alreadyCheckedCoverage = HashMap<Int, Pair<Boolean, ExecutionDataStore>?>()

}