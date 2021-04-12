package com.stepanov.bbf.bugfinder.isolation

import com.stepanov.bbf.bugfinder.executor.*
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.bugfinder.mutator.transformations.*
import com.stepanov.bbf.coverage.CompilerInstrumentation
import com.stepanov.bbf.coverage.ProgramCoverage
import com.stepanov.bbf.reduktor.executor.CompilerTestChecker
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.apache.log4j.Logger
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class BugIsolator(
        private val mutations: List<Transformation>,
        private val rankingFormula: RankingFormula,
        val shouldResultsBeSerialized: Boolean = false,
        private val serializationDirPath: String? = null
) : Checker() {

    var mutantsExportTag: String = "default"
    var coveragesExportTag: String = "default"
    var resultsExportTag: String = "default"

    fun isolate(
            sampleFilePath: String, bugInfo: BugInfo,
            createChecker: (() -> CompilerTestChecker)? = null,
            serializationTag: String? = null
    ): RankedProgramEntities {
        require(!shouldResultsBeSerialized || serializationTag != null) {
            "An ID-tag has to be provided if results of the isolation are to be serialized."
        }
        if (!shouldResultsBeSerialized && serializationTag != null) {
            logger.debug("A serialization ID-tag was provided even though results will not be serialized.")
        }

        logger.debug("Isolating a bug in $sampleFilePath ...")

        currentChecker = createChecker?.invoke() ?: constructChecker(bugInfo)
        logger.debug("Bug checker of choice: $currentChecker")

        // sometimes PSICreator trips up badly and there's nothing we can do about it
        val initialFile: KtFile
        try {
            initialFile = psiCreator.getPSIForFile(sampleFilePath)
        } catch (e: Throwable) {
            throw PSICreatorException(e)
        }

        // getting rid of possible leftovers from somewhere else
        CompilerInstrumentation.clearRecords()

        // since Kotlin compiler is stateful,
        // we spend some small amount of time finding the minimal original coverage
        val initCoverages = mutableListOf<ProgramCoverage>()
        for (i in 0 until originalSampleRecompilationTimes) {
            val currentCheckerRef = createChecker?.invoke() ?: constructChecker(bugInfo)
            val (isBugPresent, coverage) = compile(initialFile.text, currentCheckerRef)
            if (!isBugPresent) throw NoBugFoundException("$sampleFilePath contains no described bugs on used compiler version.")
            if (coverage != null) initCoverages.add(coverage)
        }
        val originalCoverage = initCoverages.minBy { c -> c.size } ?: throw IllegalStateException("No coverage was generated for $sampleFilePath for unknown reason.")

        logger.debug("Gathered coverage for original $sampleFilePath sample: ${originalCoverage.size} program entities")

        // setting up the Transformation environment
        // the checker ref should not change throughout the entire bug isolation process
        Transformation.file = initialFile
        Transformation.checker = this

        // setting up this class's environment
        buggedCoverages = mutableSetOf()
        bugFreeCoverages = mutableSetOf()
        mutantsCatalog = mutableSetOf()

        logger.debug("Gathering sample mutants and their coverages...")

        // generating mutants and their coverages for following fault localization
        for (mutation in mutations) {
            try {
                currentMutation = mutation.name
                mutation.transform()
            } catch (e: Throwable) {
                // if something bad happens when we mutate, we just
                // halt a particular mutation and turn to the next one
                logger.debug(e.message)
            }
        }

        logger.debug("Gathered ${buggedCoverages.size} coverages of bugged mutants")
        logger.debug("Gathered ${bugFreeCoverages.size} coverages of bug-free mutants")

        logger.debug("Ranking program entities' suspiciousness ...")
        val executionStatistics = ExecutionStatistics.compose(originalCoverage, buggedCoverages, bugFreeCoverages)
        val rankedProgramEntities = RankedProgramEntities.rank(executionStatistics, rankingFormula)

        logger.debug("Serializing results ...")
        // serializing intermediate and final results for later use if necessary
        if (shouldResultsBeSerialized) {
            File("$serializationDirPath/$serializationTag").mkdirs()

            exportMutants(
                "$serializationDirPath/$serializationTag/mutants-$mutantsExportTag.cbor",
                initialFile.text, mutantsCatalog.toList()
            )

            val coveragesFullExportTag = "$mutantsExportTag-$coveragesExportTag"
            exportCoverages(
                "$serializationDirPath/$serializationTag/coverages-$coveragesFullExportTag.cbor",
                originalCoverage, buggedCoverages.toList(), bugFreeCoverages.toList()
            )

            val resultsFullExportTag = "$coveragesFullExportTag-$resultsExportTag"
            val resultsPath = "$serializationDirPath/$serializationTag/results-$resultsFullExportTag.json"
            logger.debug("Results go to $resultsPath")
            rankedProgramEntities.export(resultsPath)
        }

        currentChecker = null // just introducing some consistency

        logger.debug("$sampleFilePath's bug successfully isolated!")
        logger.debug("")

        return rankedProgramEntities
    }

    fun isolate(
            mutants: MutantsForIsolation, bugInfo: BugInfo,
            createChecker: (() -> CompilerTestChecker)? = null,
            serializationTag: String? = null
    ): RankedProgramEntities {
        require(!shouldResultsBeSerialized || serializationTag != null) {
            "An ID-tag has to be provided if results of the isolation are to be serialized."
        }
        if (!shouldResultsBeSerialized && serializationTag != null) {
            logger.debug("A serialization ID-tag was provided even though results will not be serialized.")
        }

        logger.debug("Isolating a bug in the sample below using mutants\n${mutants.originalSample}")

        // we don't need to use mutations so we can just use a local checker object
        val checker = createChecker?.invoke() ?: constructChecker(bugInfo)
        logger.debug("Bug checker of choice: $checker")

        // sometimes PSICreator trips up badly and there's nothing we can do about it
        val initialFile: KtFile
        try {
            initialFile = psiCreator.getPSIForText(mutants.originalSample)
        } catch (e: Throwable) {
            throw PSICreatorException(e)
        }

        // getting rid of possible leftovers from somewhere else
        CompilerInstrumentation.clearRecords()

        // since Kotlin compiler is stateful,
        // we spend some small amount of time finding the minimal original coverage
        val initCoverages = mutableListOf<ProgramCoverage>()
        for (i in 0 until originalSampleRecompilationTimes) {
            val (isBugPresent, coverage) = compile(initialFile.text, checker)
            if (!isBugPresent) throw NoBugFoundException("Original sample contains no described bugs on used compiler version.")
            if (coverage != null) initCoverages.add(coverage)
        }
        val originalCoverage = initCoverages.minBy { c -> c.size } ?: throw IllegalStateException("No coverage was generated for original sample for unknown reason.")

        logger.debug("Gathered coverage for original sample: ${originalCoverage.size} program entities")

        // setting up this class's environment
        val localBuggedCoverages = mutableSetOf<ProgramCoverage>()
        val localBugFreeCoverages = mutableSetOf<ProgramCoverage>()

        logger.debug("Gathering sample mutants' coverages...")

        // generating mutants' coverages for following fault localization
        for (mutant in mutants.mutants) {
            val (isBugPresent, coverage) = compile(mutant, checker)
            if (coverage != null) {
                if (isBugPresent)
                    localBuggedCoverages.add(coverage)
                else
                    localBugFreeCoverages.add(coverage)
            }
        }

        logger.debug("Gathered ${buggedCoverages.size} coverages of bugged mutants")
        logger.debug("Gathered ${bugFreeCoverages.size} coverages of bug-free mutants")

        logger.debug("Ranking program entities' suspiciousness ...")
        val executionStatistics = ExecutionStatistics.compose(originalCoverage, localBuggedCoverages, localBugFreeCoverages)
        val rankedProgramEntities = RankedProgramEntities.rank(executionStatistics, rankingFormula)

        logger.debug("Serializing results ...")
        // serializing intermediate and final results for later use if necessary
        if (shouldResultsBeSerialized) {
            File("$serializationDirPath/$serializationTag").mkdirs()

            val coveragesFullExportTag = "$mutantsExportTag-$coveragesExportTag"
            exportCoverages(
                "$serializationDirPath/$serializationTag/coverages-$coveragesFullExportTag.cbor",
                originalCoverage, buggedCoverages.toList(), bugFreeCoverages.toList()
            )

            val resultsFullExportTag = "$coveragesFullExportTag-$resultsExportTag"
            val resultsPath = "$serializationDirPath/$serializationTag/results-$resultsFullExportTag.json"
            logger.debug("Results go to $resultsPath")
            rankedProgramEntities.export(resultsPath)
        }

        logger.debug("Bug successfully isolated!")
        logger.debug("")

        return rankedProgramEntities
    }

    fun isolate(
            coverages: CoveragesForIsolation,
            serializationTag: String? = null
    ): RankedProgramEntities {
        require(!shouldResultsBeSerialized || serializationTag != null) {
            "An ID-tag has to be provided if results of the isolation are to be serialized."
        }
        if (!shouldResultsBeSerialized && serializationTag != null) {
            logger.debug("A serialization ID-tag was provided even though results will not be serialized.")
        }

        logger.debug("Isolating a bug using coverages ...")

        logger.debug("Ranking program entities' suspiciousness ...")
        val executionStatistics = ExecutionStatistics.compose(coverages)
        val rankedProgramEntities = RankedProgramEntities.rank(executionStatistics, rankingFormula)

        logger.debug("Serializing results ...")
        // serializing final results for later use if necessary
        if (shouldResultsBeSerialized) {
            File("$serializationDirPath/$serializationTag").mkdirs()

            val resultsFullExportTag = "${coverages.exportTag}-$resultsExportTag"
            val resultsPath = "$serializationDirPath/$serializationTag/results-$resultsFullExportTag.json"
            logger.debug("Results go to $resultsPath")
            rankedProgramEntities.export(resultsPath)
        }

        logger.debug("Bug successfully isolated!")
        logger.debug("")

        return rankedProgramEntities
    }

    override fun checkCompiling(file: KtFile): Boolean = checkTextCompiling(file.text)

    override fun checkTextCompiling(text: String): Boolean {
        val checker = currentChecker ?: throw IllegalStateException("Do not call checks from BugIsolator directly.")
        val (isBugPresent, coverage) = compile(text, checker)

        if (coverage != null) {
            val wasAdded = if (isBugPresent)
                buggedCoverages.add(coverage)
            else
                bugFreeCoverages.add(coverage)
            if (wasAdded) mutantsCatalog.add(text)
        }

        return false // keeping original sample mutating
    }

    private fun exportMutants(exportPath: String, originalSample: String, mutants: List<String>) {
        logger.debug("Mutants go to $exportPath")
        MutantsForIsolation(mutantsExportTag, originalSample, mutants).export(exportPath)
    }

    private fun exportCoverages(
        exportPath: String, originalCoverage: ProgramCoverage,
        buggedCoverages: List<ProgramCoverage>, bugFreeCoverages: List<ProgramCoverage>
    ) {
        logger.debug("Coverages go to $exportPath")
        CoveragesForIsolation(exportPath, originalCoverage, buggedCoverages, bugFreeCoverages).export(exportPath)
    }

    // collections of coverages for until all mutations finish their execution
    private lateinit var buggedCoverages: MutableSet<ProgramCoverage>
    private lateinit var bugFreeCoverages: MutableSet<ProgramCoverage>

    // a collection of all interesting mutants in case we want to serialize them
    private lateinit var mutantsCatalog: MutableSet<String>

    private val psiCreator = PSICreator("")

    // an oracle to determine whether a code mutant has a bug or not
    private var currentChecker: CompilerTestChecker? = null

    // a mutation's name, saved for debug purposes
    private lateinit var currentMutation: String

    private val logger = Logger.getLogger("isolationLogger")

    init {
        require(!shouldResultsBeSerialized || serializationDirPath != null) {
            "A directory path has to be provided if results of the isolation are to be serialized."
        }
        if (!shouldResultsBeSerialized && serializationDirPath != null) {
            logger.debug("A serialization directory path was provided even though results will not be serialized.")
        }
    }

    companion object {
        // different bugs need different oracles for fault localization
        // this function attempts to provide them
        fun constructChecker(bugInfo: BugInfo, filterInvalidCode: Boolean = false): CompilerTestChecker {
            val checker = when (bugInfo.type) {
                BugType.BACKEND, BugType.FRONTEND -> MultiCompilerCrashChecker(bugInfo.firstCompiler)
                // TODO check if next two checkers work correctly with this isolator
                BugType.DIFFBEHAVIOR -> DiffBehaviorChecker(bugInfo.compilers)
                BugType.DIFFCOMPILE -> DiffCompileChecker(bugInfo.compilers)
                BugType.UNKNOWN -> throw IllegalArgumentException("Unknown bug type detected.")
            }
            checker.filterInvalidCode = filterInvalidCode
            return checker
        }

        val typicalMutations = listOf(
                AddBlockToExpression(),
                AddBracketsToExpression(),
                AddDefaultValueToArg(),
                AddNotNullAssertions(),
                AddNullabilityTransformer(),
                ChangeOperators(),
                ChangeOperatorsToFunInvocations(),
                ChangeRandomASTNodes(),
                ChangeRandomASTNodesFromAnotherTrees(),
                ChangeRandomLines(),
                RemoveRandomASTNodes(),
                RemoveRandomLines()
        )

        // magical constants which I don't know what to do with
        var originalSampleRecompilationTimes: Int = 4

        private fun compile(text: String, checker: CompilerTestChecker): Pair<Boolean, ProgramCoverage?> {
            val isBugPresent = checker.checkTest(text, "tmp/localization_tmp.kt")

            // absence of coverage should indicate we already stumbled upon this mutant before
            return if (!CompilerInstrumentation.isEmpty) {
                val coverage = ProgramCoverage.createFromProbes()
                CompilerInstrumentation.clearRecords() // making sure the previous comment holds
                isBugPresent to coverage
            } else {
                isBugPresent to null
            }
        }
    }

}