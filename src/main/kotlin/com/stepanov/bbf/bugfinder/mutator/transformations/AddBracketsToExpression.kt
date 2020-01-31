package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.executor.MutationChecker
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getRandomBoolean

class AddBracketsToExpression : Transformation() {

    override fun transform() {
        file.getAllPSIChildrenOfType<KtExpression>()
            .filter { getRandomBoolean(4) }
            .forEach {
                if (it is KtWhenExpression) return@forEach
                try {
                    val newExpr = psiFactory.createExpression("(${it.text})")
                    MutationChecker.replacePSINodeIfPossible(file, it, newExpr)
                } catch (e: Exception) {
                    return@forEach
                }
            }
    }
}