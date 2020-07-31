package com.stepanov.bbf.bugfinder.mutator.transformations.constructor

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import com.stepanov.bbf.bugfinder.util.flatMap
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getAllWithoutLast
import com.stepanov.bbf.bugfinder.util.removeDuplicatesBy
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.serialization.deserialization.DeserializedPackageFragmentImpl
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedCallableMemberDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedPropertyDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

object UsageSamplesGeneratorWithStLibrary {

    val descriptorDecl: List<DeclarationDescriptor>

    init {
        val (psi, ctx) = PSICreator("").let { it.getPSIForText("val a: StringBuilder = StringBuilder(\"\")") to it.ctx!! }
        val kType = psi.getAllPSIChildrenOfType<KtProperty>().map { it.typeReference?.getAbbreviatedTypeOrType(ctx) }[0]!!
        val module = kType.constructor.declarationDescriptor!!.module
        val stringPackages = module.getSubPackagesOf(FqName("kotlin")) {true}
        val packages = stringPackages.map { module.getPackage(it) }
        descriptorDecl = packages.flatMap { it.memberScope.getDescriptorsFiltered { true } }
    }

    fun generateForStandardType(type: KotlinType, needType: String): List<List<PsiElement>> {
        val resForType = gen(type, needType)
        val resForSuperTypes = type.supertypes().getAllWithoutLast().flatMap { gen(it, needType) }.toList()
        val res = resForType + resForSuperTypes
        return res.removeDuplicatesBy { it.createTypeCallSeq() }
    }

    private fun gen(type: KotlinType, needType: String, prefix: List<PsiElement> = listOf()): List<List<PsiElement>> {
        if (prefix.size == maxDepth) return listOf()
        val typeParamToArg = type.constructor.parameters.zip(type.arguments)
        val ext = getExtensionFuncsFromStdLibrary(type, needType)
        val funList: MutableList<List<PsiElement>> = mutableListOf()
        for (decl in ext) {
            val typeParamNameToRealArg =
                decl.recType!!.arguments.map { it.toString() }.zip(typeParamToArg.map { it.second.toString() }).toMap()
            val retValueType = decl.retValueType!!
            val realType = retValueType.replaceTypeArgsToTypes(typeParamNameToRealArg)
            if (realType == needType) {
                createPsiElement(decl.descriptor.containingDeclaration!!)?.let { funList.add(prefix + it) }
            }
        }
        val derivedTypes = mutableListOf<Pair<List<PsiElement>, KotlinType>>()
        for (mem in getMemberFields(type)) {
            val descriptor =
                mem as? DeserializedCallableMemberDescriptor ?: mem as? CallableMemberDescriptor ?: continue
            if (descriptor.returnType?.toString() == needType) {
                createPsiElement(descriptor)?.let { funList.add(prefix + it) }
            } else {
                val anotherType = descriptor.returnType ?: continue
                createPsiElement(descriptor)?.let { derivedTypes.add(prefix + it to anotherType) }
            }
        }
        derivedTypes
            .filter { it.second.toString() != type.toString() }
            .removeDuplicatesBy { it.second.toString() }
            .flatMap { gen(it.second, needType, it.first) }
            .forEach { funList.add(prefix + it) }
        return funList
    }

    private fun List<PsiElement>.createTypeCallSeq(): String =
        this.joinToString(".") {
            when (it) {
                is KtProperty -> it.name ?: ""
                else -> (it as KtNamedFunction).name ?: ""
            }
        }


    private fun createPsiElement(descriptor: DeclarationDescriptor): PsiElement? =
        when (descriptor) {
            is DeserializedSimpleFunctionDescriptor, is SimpleFunctionDescriptor -> {
                createFunDefinitionFromDeclarationDescriptor(mapOf(), descriptor)
            }
            is DeserializedPropertyDescriptor -> {
                Factory.psiFactory.createProperty("val ${descriptor.name}: ${descriptor.returnType}")
            }
            is CallableMemberDescriptor -> {
                Factory.psiFactory.createProperty("val ${descriptor.name}: ${descriptor.returnType}")
            }
            else -> null
        }


    private fun KotlinType.replaceTypeArgsToTypes(map: Map<String, String>): String {
        val realType =
            if (isTypeParameter())
                map[this.constructor.toString()] ?: map["${this.constructor}?"] ?: this.toString()
            else
                this.constructor.toString()
        val typeParams = this.arguments.map { it.type.replaceTypeArgsToTypes(map) }
        return if (typeParams.isNotEmpty()) "$realType<${typeParams.joinToString()}>" else realType
    }

    private fun getExtensionFuncsFromStdLibrary(type: KotlinType, needType: String): List<DeclarationDescr> {
        //val containingDeclaration = type.constructor.declarationDescriptor!!.containingDeclaration
        //val scope = (containingDeclaration as PackageFragmentDescriptor).getMemberScope()
        //val descriptorDecl = scope.getDescriptorsFiltered { true }
        val res = mutableListOf<DeclarationDescr>()
        for (desc in descriptorDecl) {
            val rec = when (desc) {
                is DeserializedSimpleFunctionDescriptor -> desc.dispatchReceiverParameter
                    ?: desc.extensionReceiverParameter
                is DeserializedPropertyDescriptor -> desc.dispatchReceiverParameter ?: desc.extensionReceiverParameter
                else -> null
            }
            val retValueType = (desc as? DeserializedCallableMemberDescriptor)?.returnType
            if (rec?.value?.type?.toString()?.substringBefore('<') == type.toString().substringBefore('<'))
                res.add(DeclarationDescr(rec, rec.returnType, retValueType))
        }
        return res
    }

    private fun getMemberFields(type: KotlinType): List<DeclarationDescriptor> =
        type.memberScope.getDescriptorsFiltered { true }
            .filter { !it.toString().contains("private") }

    private fun createFunDefinitionFromDeclarationDescriptor(
        typeParamsToArgs: Map<String, String>,
        decl: DeclarationDescriptor
    ): KtNamedFunction? {
        val funDecl =
            decl as? CallableMemberDescriptor ?: return Factory.psiFactory.createFunction("fun a()")
        val typeParam =
            if (funDecl.typeParameters.isNotEmpty()) {
                val withUpperBounds = funDecl.typeParameters.map {
                    val type = it.defaultType.replaceTypeArgsToTypes(typeParamsToArgs)
                    val upperBounds = if (it.upperBounds.size == 1 && it.upperBounds[0].isAnyOrNullableAny())
                        ""
                    else
                        it.upperBounds
                            .map { it.replaceTypeArgsToTypes(typeParamsToArgs) }
                            .filter { it.isNotEmpty() }
                            .joinToString()
                    if (upperBounds.isEmpty()) type else "$type : $upperBounds"
                }.joinToString()
                "<$withUpperBounds>"
            } else ""
        val rec = funDecl.dispatchReceiverParameter ?: funDecl.extensionReceiverParameter
        val recWithoutTypeParam = rec?.value?.type?.replaceTypeArgsToTypes(typeParamsToArgs) ?: rec.toString()
        val params = handleParams(decl.valueParameters, typeParamsToArgs)
        val func = "fun $typeParam $recWithoutTypeParam.${decl.name}($params): ${decl.returnType}{}"
        return try {
            Factory.psiFactory.createFunction(func)
        } catch (e: Exception) {
            println("cant create fun ${func}")
            System.exit(1)
            null
        }
    }

    private fun handleParams(valParamDesc: List<ValueParameterDescriptor>, typeParamsToArgs: Map<String, String>) =
        valParamDesc.map { "${it.name}: ${it.type.replaceTypeArgsToTypes(typeParamsToArgs)}" }.joinToString()

    private val maxDepth = 2
}

data class DeclarationDescr(
    val descriptor: DeclarationDescriptor,
    val recType: KotlinType?,
    val retValueType: KotlinType?
)