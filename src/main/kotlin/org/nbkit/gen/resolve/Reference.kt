package org.nbkit.gen.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.squareup.kotlinpoet.*
import org.nbkit.lang.ScopeRule
import org.nbkit.gen.BaseSpec
import java.nio.file.Path

class ReferenceSpec(
        fileNamePrefix: String,
        basePackageName: String,
        genPath: Path,
        scopeRules: List<ScopeRule>
) : BaseSpec(fileNamePrefix, basePackageName, genPath, scopeRules) {
    override fun generate() {
        TypeSpec.interfaceBuilder(className)
                .addSuperinterface(PsiReference::class)
                .addFunction(FunSpec.builder("getElement")
                        .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                        .returns(elementClass)
                        .build())
                .addFunction(FunSpec.builder("resolve")
                        .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                        .returns(PsiElement::class.asClassName().asNullable())
                        .build())
                .build()
                .also { addType(it) }

        TypeSpec.classBuilder("${className}Base")
                .addModifiers(KModifier.ABSTRACT)
                .addTypeVariable(variableType.withBounds(referenceElementClass))
                .addSuperinterface(referenceClass)
                .superclass(ParameterizedTypeName.get(
                        PsiReferenceBase::class.asClassName(),
                        variableType
                ))
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("element", variableType)
                        .build())
                .addSuperclassConstructorParameter("element")
                .addSuperclassConstructorParameter("%T(0, element.textLength)", TextRange::class)
                .addFunction(FunSpec.builder("handleElementRename")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("newName", String::class)
                        .returns(PsiElement::class)
                        .addStatement("element.referenceNameElement?.let { doRename(it, newName) }")
                        .addStatement("return element")
                        .build())
                .companionObject(TypeSpec.companionObjectBuilder()
                        .addFunction(FunSpec.builder("doRename")
                                .addModifiers(KModifier.PRIVATE)
                                .addParameter("oldNameIdentifier", PsiElement::class)
                                .addParameter("rawName", String::class)
                                .addStatement("val name = rawName.removeSuffix('.' + %T.defaultExtension)", fileTypeClass)
                                .addStatement("if (!%T().isIdentifier(name, oldNameIdentifier.project)) return", namesValidatorClass)
                                .addStatement("val factory = %T(oldNameIdentifier.project)", psiFactoryClass)
                                .addCode(
                                        buildString {
                                            appendln("val newNameIdentifier = when (oldNameIdentifier) {")
                                            for (className in (referableClasses + referenceClasses)) {
                                                appendln("    is %T -> factory.create${className.commonName}(name)")
                                            }
                                            appendln("    else -> return")
                                            appendln("}")
                                        }.trimMargin(),
                                        *(referableClasses + referenceClasses).toTypedArray()
                                )
                                .addStatement("")
                                .addStatement("oldNameIdentifier.replace(newNameIdentifier)")
                                .build())
                        .build())
                .build()
                .also { addType(it) }

        writeToFile()
    }
}
