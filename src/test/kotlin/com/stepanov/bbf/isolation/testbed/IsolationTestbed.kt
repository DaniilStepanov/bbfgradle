package com.stepanov.bbf.isolation.testbed

import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.apache.log4j.PropertyConfigurator

fun main() {
    Transformation.file = PSICreator("").getPSIForText("")

    PropertyConfigurator.configure("src/main/resources/bbfLog4j.properties")
    PropertyConfigurator.configure("src/main/resources/reduktorLog4j.properties")

    reinitializeRandom(utilRandomSeed)

    // DATASET FILTERING TOOLS
//    filterSamplesByIdentity("isolation-evaluation/samples/youtrack")
//    filterSamplesByBugPresence("isolation-evaluation/samples/youtrack", defaultBugInfo)
//    reduceAllSamplesInDataset("isolation-evaluation/samples/youtrack", defaultBugInfo)

//    MutantGenerator.generate(
//        "isolation-evaluation/samples/youtrack", defaultBugInfo,
//        mutantsExportTag = "default",
//        mutations = BugIsolator.typicalMutations
//    )

//    stacktraceEvaluation("isolation-evaluation/samples/youtrack-reduced")

//    bugIsolationEvaluation(
//        "isolation-evaluation/samples/testrun",
//        sourceType = BugIsolationSourceType.MUTANTS,
//        mutantsImportTag = "default",
//        coveragesImportTag = "",
//        mutantsExportTag = "",
//        coveragesExportTag = "c2",
//        resultsExportTag = "r"
//    )

    // CALCULATING F-SCORES FOR STACKTRACE SIMILARITY RANKINGS
//    val estimatesOriginal = loadEstimationResults("isolation-evaluation/samples/youtrack-results/stacktraces.cbor")
//    val performancesOriginal = estimatesOriginal.calculateAllPossibleFScores(0.5)
}
