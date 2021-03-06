package org.ice1000.julia.lang.editing

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import icons.JuliaIcons
import org.ice1000.julia.lang.*
import org.ice1000.julia.lang.editing.JuliaBasicCompletionContributor.CompletionHolder.STRING_COLORS_PRIORITY
import org.ice1000.julia.lang.module.JULIA_COLOR_CONSTANTS
import org.ice1000.julia.lang.psi.*
import org.ice1000.julia.lang.psi.impl.IJuliaFunctionDeclaration
import org.ice1000.julia.lang.psi.impl.prevRealSibling

open class JuliaCompletionProvider(private val list: List<LookupElement>) : CompletionProvider<CompletionParameters>() {
	override fun addCompletions(
		parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) =
		list.forEach(result::addElement)
}

class JuliaModuleStubCompletionProvider : CompletionProvider<CompletionParameters>() {
	override fun addCompletions(
		parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
		val text = parameters.originalPosition?.text ?: return
		val project = parameters.editor.project ?: return
		val keys = JuliaModuleDeclarationIndex.getAllKeys(project)
		keys.asSequence()
			.filter { it.contains(text, true) }
			.forEach { str ->
				val k = JuliaModuleDeclarationIndex.findElementsByName(project, str)
				k.forEach {
					result.addElement(LookupElementBuilder
						.create(str)
						.withIcon(JuliaIcons.JULIA_MODULE_ICON)
						.withTypeText(it.containingFile.presentText(), true)
						.prioritized(0))
				}
			}
	}
}

class JuliaUsingMemberAccessCompletionProvider : CompletionProvider<CompletionParameters>() {
	override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
		val elem = parameters.position
		val file = elem.containingFile
		val project = file.project
		val currentSymbol = elem.parent
		val moduleSymbol = currentSymbol.parent.firstChild as? JuliaSymbol ?: return
		if (moduleSymbol.symbolKind != JuliaSymbolKind.ModuleName) return
		val modules = JuliaModuleDeclarationIndex.get(moduleSymbol.text, project, GlobalSearchScope.allScope(project))
		for (moduleDeclaration in modules) {
			val stmts = moduleDeclaration.statements ?: continue
			stmts.children.asSequence()
				.filter {
					it is IJuliaFunctionDeclaration
				}.forEach {
					val ident = (it as IJuliaFunctionDeclaration).nameIdentifier?.text ?: return@forEach
					result.addElement(LookupElementBuilder
						.create(ident)
						.withIcon(it.getIcon())
						.withTypeText(it.containingFile.presentText(), true)
						.prioritized(0))
				}
		}
	}
}

class JuliaColorCompletionProvider : CompletionProvider<CompletionParameters>() {
	override fun addCompletions(
		parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
		val psiElement = parameters.originalPosition ?: return
		val text = psiElement.text ?: return
		if (psiElement.parent.parent.prevRealSibling?.text != "colorant") return
		JULIA_COLOR_CONSTANTS.filter { it.key.contains(text) }.forEach {
			result.addElement(LookupElementBuilder
				.create(it.key)
				.withIcon(ColorIcon(20, it.value.toColor()))
				.prioritized(STRING_COLORS_PRIORITY))
		}
	}
}

class JuliaTypeStubCompletionProvider : CompletionProvider<CompletionParameters>() {
	override fun addCompletions(
		parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
		val text = parameters.originalPosition?.text ?: return
		val project = parameters.editor.project ?: return
		val keys = JuliaTypeDeclarationIndex.getAllKeys(project)
		keys
			.filter { it.contains(text, true) }
			.forEach { str ->
				val types = JuliaTypeDeclarationIndex.findElementsByName(project, str)
				types.forEach {
					result.addElement(LookupElementBuilder
						.create(str)
						.withIcon(JuliaIcons.JULIA_TYPE_ICON)
						.withTypeText(it.containingFile.presentText(), true)
						.prioritized(0))
				}
			}
		val abstractKeys = JuliaAbstractTypeDeclarationIndex.getAllKeys(project)
		abstractKeys
			.filter { it.contains(text, true) }
			.forEach { str ->
				val types = JuliaAbstractTypeDeclarationIndex.findElementsByName(project, str)
				types.forEach {
					result.addElement(LookupElementBuilder
						.create(str)
						.withIcon(JuliaIcons.JULIA_ABSTRACT_TYPE_ICON)
						.withTypeText(it.containingFile.presentText(), true)
						.prioritized(0))
				}
			}
	}
}

class JuliaBasicCompletionContributor : CompletionContributor() {
	companion object CompletionHolder {
		/**
		 * the lowest priority of completion, just make it less than [KEYWORDS_PRIORITY].
		 */
		const val BUILTIN_TAB_PRIORITY = -0xcafe
		/**
		 * This completion is lower than [JuliaSymbolRef]
		 * @see [CompletionProcessor]
		 */
		const val KEYWORDS_PRIORITY = -0xbabe

		/**
		 * This completion is lower than [KEYWORDS_PRIORITY]
		 * @see [CompletionProcessor]
		 */
		const val STRING_COLORS_PRIORITY = -0xC101A

		private val statementBegin = listOf(
			"type ",
			"abstract type ",
			"primitive type ",
			"immutable ",
			"module ",
			"baremodule ",
			"import ",
			"using ",
			"include ",
			"export ",
			"typealias ",
			"while ",
			"for ",
			"try ",
			"if ",
			"mutable struct ",
			"struct ",
			"begin ",
			"let ",
			"quote ",
			"const ",
			"local ",
			"macro ",
			"function ",
			"end"
		).map {
			LookupElementBuilder
				.create(it)
				.withIcon(JuliaIcons.JULIA_BIG_ICON)
				.withTypeText(JuliaBundle.message("julia.completion.keyword.tail"), true)
				.prioritized(KEYWORDS_PRIORITY)
		}
		private val tryInside = listOf(
			"catch ",
			"finally"
		).map {
			LookupElementBuilder
				.create(it)
				.withIcon(JuliaIcons.JULIA_BIG_ICON)
				.prioritized(KEYWORDS_PRIORITY)
		}
		private val loopInside = listOf(
			"break",
			"continue"
		).map {
			LookupElementBuilder
				.create(it)
				.withIcon(JuliaIcons.JULIA_BIG_ICON)
				.withTypeText(JuliaBundle.message("julia.completion.jump.tail"), true)
				.prioritized(KEYWORDS_PRIORITY)
		}
		private val ifInside = listOf(
			"elseif ",
			"else"
		).map { LookupElementBuilder.create(it).withIcon(JuliaIcons.JULIA_BIG_ICON).prioritized(KEYWORDS_PRIORITY) }
		private val functionInside = listOf(LookupElementBuilder.create("return").prioritized(KEYWORDS_PRIORITY))

		private val builtinV06 by lazy {
			this::class.java.getResource("builtin-v0.6.txt")
				.openStream()
				.bufferedReader().lineSequence().toList()
		}
		private val builtinV10 by lazy {
			this::class.java.getResource("builtin-v1.0.txt")
				.openStream()
				.bufferedReader().lineSequence().toList()
		}

		val builtins by lazy {
			(builtinV06 + builtinV10).distinct()
		}

		// FIXME temp workaround. Should be replaced by reference resolving.
		private val builtinFunction = (builtinV06 + builtinV10).map {
			LookupElementBuilder
				.create(it)
				.withIcon(JuliaIcons.JULIA_FUNCTION_ICON)
				.withTypeText(
					when (it) {
						!in builtinV10 -> "0.6 Predefined symbol"
						!in builtinV06 -> "1.0 Predefined symbol"
						else -> "Predefined symbol"
					}, true)
				.prioritized(BUILTIN_TAB_PRIORITY)
		}

		private val where = listOf(
			LookupElementBuilder
				.create("where")
				.withIcon(JuliaIcons.JULIA_BIG_ICON)
				.withTypeText("Keyword", true)
				.prioritized(KEYWORDS_PRIORITY)
		)
	}

	override fun invokeAutoPopup(position: PsiElement, typeChar: Char) =
		position.parent !is JuliaString &&
			position.parent !is JuliaStringContent &&
			position.parent !is JuliaCommand &&
			typeChar in ".([ "

	init {
		// static
		// `where`
		extend(CompletionType.BASIC, psiElement()
			.inside(JuliaFunction::class.java)
			.afterLeaf(")")
			.andNot(psiElement()
				.withParent(JuliaStatements::class.java)),
			JuliaCompletionProvider(where))
		// keywords
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStatements::class.java)
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java)))
				.andNot(psiElement().withAncestor(2, psiElement(JuliaComment::class.java)))
				.andNot(psiElement().withAncestor(3, psiElement(JuliaUsing::class.java))),
			JuliaCompletionProvider(statementBegin))
		// functions
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStatements::class.java)
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java)))
				.andNot(psiElement().withAncestor(2, psiElement(JuliaComment::class.java)))
				.andNot(psiElement().withAncestor(3, psiElement(JuliaUsing::class.java))),
			JuliaCompletionProvider(builtinFunction))
		// `catch`, `finally`
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStatements::class.java)
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java)))
				.andNot(psiElement().withAncestor(3, psiElement(JuliaUsing::class.java))),
			JuliaCompletionProvider(tryInside))
		// `break`, `continue`
		extend(CompletionType.BASIC,
			psiElement()
				.andOr(
					psiElement().inside(JuliaWhileExpr::class.java),
					psiElement().inside(JuliaForExpr::class.java))
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java))),
			JuliaCompletionProvider(loopInside))
		// `elseif`, `else`
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaIfExpr::class.java)
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java))),
			JuliaCompletionProvider(ifInside))
		extend(CompletionType.BASIC,
			psiElement()
				.andOr(
					psiElement().inside(JuliaFunction::class.java),
					psiElement().inside(JuliaMacro::class.java))
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java))),
			JuliaCompletionProvider(functionInside))

		// dynamic
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStatements::class.java)
				.andNot(psiElement().withAncestor(2, psiElement(JuliaString::class.java)))
				.andNot(psiElement().withAncestor(2, psiElement(JuliaComment::class.java))),
			JuliaTypeStubCompletionProvider())
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStatements::class.java),
			JuliaModuleStubCompletionProvider())
		extend(CompletionType.BASIC,
			psiElement()
				.inside(JuliaStringContent::class.java),
			JuliaColorCompletionProvider())
		extend(CompletionType.BASIC,
			psiElement()
				.withParent(JuliaSymbol::class.java)
				.withAncestor(3, psiElement(JuliaUsing::class.java)),
			JuliaUsingMemberAccessCompletionProvider())
	}
}

/**
 * convert a LookupElementBuilder into a Prioritized LookupElement
 * @receiver [LookupElementBuilder]
 * @param priority [Int] the priority of current LookupElementBuilder
 * @return [LookupElement] Prioritized LookupElement
 */
fun LookupElementBuilder.prioritized(priority: Int): LookupElement = PrioritizedLookupElement.withPriority(this, priority.toDouble())