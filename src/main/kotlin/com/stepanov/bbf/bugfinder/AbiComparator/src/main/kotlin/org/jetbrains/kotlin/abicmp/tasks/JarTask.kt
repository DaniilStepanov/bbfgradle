package org.jetbrains.kotlin.abicmp.tasks

import org.jetbrains.kotlin.abicmp.checkers.isSamAdapterName
import org.jetbrains.kotlin.abicmp.classFlags
import org.jetbrains.kotlin.abicmp.isSynthetic
import org.jetbrains.kotlin.abicmp.listOfNotNull
import org.jetbrains.kotlin.abicmp.reports.JarReport
import org.jetbrains.kotlin.abicmp.reports.REPORT_CSS
import org.jetbrains.kotlin.abicmp.reports.isNotEmpty
import org.jetbrains.kotlin.abicmp.tag
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InnerClassNode
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.util.jar.JarFile
import org.apache.log4j.Logger

class JarTask(
    header: String,
    private val jarFile1: JarFile,
    private val jarFile2: JarFile,
    private val header1: String,
    private val header2: String,
    private val outputFile: File,
    private val checkerConfiguration: CheckerConfiguration
) : Runnable {

    private val ignoreAnonymousOrLocalClasses = true
    private val ignoreJavaClasses = true

    private val report = JarReport(header, header1, header2,
        jarFile1.name
        //jarFile1.name.substringAfter('/').substringBefore(".jar").dropLast(1)
        ,
        jarFile2.name
        //jarFile2.name.substringAfter('/').substringBefore(".jar").dropLast(1)
    )

    val defectReport get() = report.defectReport

    private var totalDiffs = 0

    private val names1 = HashSet<String>()

    private val log = Logger.getLogger("mutatorLogger")

    override fun run() {
        log.debug("Comparing jars: ${jarFile1.name}, ${jarFile2.name}")

        addJarsInfo()

        checkJarFile1()
        checkJarFile2()

        writeReportIfRequired()
    }

    fun execute(): Pair<Int, File>? {
        log.debug("Comparing jars: ${jarFile1.name}, ${jarFile2.name}")

        addJarsInfo()

        checkJarFile1()
        checkJarFile2()

        if (totalDiffs == 0) return null
        writeReportIfRequired()
        return totalDiffs to outputFile
    }

    private fun checkJarFile1() {
        jarFile1.stream().forEach { entry1 ->
            val name1 = entry1.name
            if (name1.endsWith(".class")) {
                if (name1.contains("kotlin/")) return@forEach
                names1.add(name1)
                val entry2 = jarFile2.getEntry(name1)
                if (entry2 == null) {
                    val classNode = parseClassNode(jarFile1.getInputStream(entry1))
                    if (!classNode.shouldBeIgnored()) {
                        report.addMissingClassName2("$name1 ${classNode.access.classFlags()}")
                        ++totalDiffs
                    }
                } else {
                    val class1 = parseClassNode(jarFile1.getInputStream(entry1))
                    val class2 = parseClassNode(jarFile2.getInputStream(entry2))

                    if (!class1.shouldBeIgnored() || !class2.shouldBeIgnored()) {
                        log.debug("Comparing classes: ${class1.name}")
                        val classReport = report.classReport(class1.name)
                        val classTask = ClassTask(checkerConfiguration, class1, class2, classReport)
                        classTask.run()
                        if (classReport.isNotEmpty()) {
                            ++totalDiffs
                        }
                    } else {
                        log.debug("Skipping $name1")
                    }
                }
            }
        }
    }

    private fun parseClassNode(input: InputStream): ClassNode =
        ClassNode().also { ClassReader(input).accept(it, ClassReader.SKIP_CODE) }

    private fun checkJarFile2() {
        jarFile2.stream().forEach { entry2 ->
            val name2 = entry2.name
            if (name2.endsWith(".class") && name2 !in names1) {
                if (name2.contains("kotlin/")) return@forEach
                val classNode = parseClassNode(jarFile2.getInputStream(entry2))
                if (!classNode.shouldBeIgnored()) {
                    report.addMissingClassName1("$name2 ${classNode.access.classFlags()}")
                    ++totalDiffs
                }
            }
        }
    }

    private fun writeReportIfRequired() {
        println("TD = ${totalDiffs}")

        if (report.isNotEmpty()) {
            PrintWriter(outputFile).use { out ->
                out.tag("html") {
                    out.tag("head") {
                        out.printCss()
                    }
                    out.tag("body") {
                        report.write(out)
                    }
                }
            }
        }
    }

    private fun PrintWriter.printCss() {
        tag("style", REPORT_CSS)
    }

    private fun ClassNode.shouldBeIgnored(): Boolean {
        if (ignoreJavaClasses && isJavaClass()) return true
        if (access.isSynthetic()) return true
        if (ignoreAnonymousOrLocalClasses && isAnonymousOrLocalClass()) return true
        return false
    }

    private fun ClassNode.isAnonymousOrLocalClass() =
        isSamAdapterName(name) ||
                innerClasses.listOfNotNull<InnerClassNode>().any {
                    it.name == this.name && it.outerName == null
                }

    private fun ClassNode.isJavaClass() =
        sourceFile != null && sourceFile.endsWith(".java")

    private fun addJarsInfo() {
        report.info {
            tag("p") {
                tag("b", header1)
                println(": ${jarFile1.name}")
            }
            tag("p") {
                tag("b", header2)
                println(": ${jarFile2.name}")
            }
        }
    }
}



