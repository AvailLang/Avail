/*
 * Styles.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.anvil

import avail.anvil.AdaptiveColor.Companion.hex
import avail.anvil.PhrasePathStyleApplicator.LocalDefinitionAttributeKey
import avail.anvil.PhrasePathStyleApplicator.LocalUseAttributeKey
import avail.anvil.PhrasePathStyleApplicator.PhraseNodeAttributeKey
import avail.anvil.PhrasePathStyleApplicator.TokenStyle
import avail.anvil.StylePatternCompiler.Companion.compile
import avail.anvil.StylePatternCompiler.ExactMatchToken
import avail.anvil.StylePatternCompiler.FailedMatchToken
import avail.anvil.StylePatternCompiler.SubsequenceToken
import avail.anvil.StylePatternCompiler.SuccessionToken
import avail.anvil.StylePatternCompiler.Token.Companion.classifierPrefixCode
import avail.anvil.StyleRuleContextState.ACCEPTED
import avail.anvil.StyleRuleContextState.PAUSED
import avail.anvil.StyleRuleContextState.REJECTED
import avail.anvil.StyleRuleContextState.RUNNING
import avail.anvil.StyleRuleExecutor.endOfSequenceLiteral
import avail.anvil.StyleRuleExecutor.execute
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.FORK
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.FORK_N
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_END_OF_SEQUENCE
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_0
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_1
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_2
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_3
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_N
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_OR_JUMP_0
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_OR_JUMP_1
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_OR_JUMP_2
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_OR_JUMP_3
import avail.anvil.StyleRuleInstruction.StyleRuleInstructionOpcode.MATCH_LITERAL_CLASSIFIER_OR_JUMP_N
import avail.anvil.StyleRuleInstructionCoder.Companion.decodeInstruction
import avail.anvil.streams.StreamStyle
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.numbers.AbstractNumberDescriptor.Order
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.interpreter.execution.AvailLoader
import avail.io.NybbleArray
import avail.io.NybbleInputStream
import avail.io.NybbleOutputStream
import avail.persistence.cache.record.PhrasePathRecord
import avail.persistence.cache.record.PhrasePathRecord.PhraseNode
import avail.persistence.cache.record.StyleRun
import avail.persistence.cache.record.StylingRecord
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast
import avail.utility.drain
import avail.utility.mapToSet
import avail.utility.structures.RunTree
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.Palette
import org.availlang.artifact.environment.project.StyleAttributes
import org.availlang.cache.LRUCache
import java.awt.Color
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import javax.swing.text.AttributeSet
import javax.swing.text.Position
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleConstants.Background
import javax.swing.text.StyleConstants.Bold
import javax.swing.text.StyleConstants.FontFamily
import javax.swing.text.StyleConstants.Foreground
import javax.swing.text.StyleConstants.Italic
import javax.swing.text.StyleConstants.StrikeThrough
import javax.swing.text.StyleConstants.Subscript
import javax.swing.text.StyleConstants.Superscript
import javax.swing.text.StyleConstants.Underline
import javax.swing.text.StyleContext
import javax.swing.text.StyleContext.getDefaultStyleContext
import javax.swing.text.StyledDocument

////////////////////////////////////////////////////////////////////////////////
//                                Stylesheets.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * An [AvailProject] contains a [stylesheet][Stylesheet] that dictates how an
 * Avail source viewer/editor should render a source region tagged with one or
 * more style classifiers. A stylesheet comprises one or more
 * [patterns][StylePattern], each of which encodes _(1)_ _whether_ a region of
 * text should be rendered and _(2)_ _how_ it should be rendered. Patterns are
 * written using a simple domain-specific language (DSL); this DSL is designed
 * to be reminiscent of Cascading Stylesheets (CSS), but, for reasons of
 * simplicity, is not compatible with it. Each pattern
 * [compiles][StylePatternCompiler] down into a [rule][StyleRule]. A rule is a
 * program, comprising [StyleRuleInstruction]s, whose input is the sequence `S`
 * of style classifiers attached to some source region `R` and whose output is a
 * partial [rendering&#32;context][RenderingContext] that should be applied to
 * `R`. The complete collection of rules is organized into a global
 * [StyleRuleTree], such that every vertex contains the [StyleRuleContext]s for
 * those rules that are still live and every edge encodes a possible next style
 * classifier, either as _(1)_ a fixed style classifier mentioned by some active
 * rule in the source vertex or _(2)_ a wildcard that matches any style
 * classifier. The [RenderingEngine] iteratively feeds `S`, one style classifier
 * at a time, through the tree, starting at the root. At each vertex, it feeds
 * the current classifier `C` to each rule therein. Each rule that accepts `C`
 * generates one or more [StyleRuleContext]s, each of which is injected into the
 * lazy successor vertex along the edge labeled `C`; each rule that rejects `C`
 * is excluded from further consideration; each rule that completes adds itself
 * to the (ordered) solution set. After consuming `S` entirely, the rules of the
 * solution set are ranked according to specificity, and every rule that ties
 * for highest specificity contributes its rendering effects to the final
 * [RenderingContext]. Rendering conflicts are resolved by insertion order, with
 * later rules prevailing over earlier ones. For computational efficiency, the
 * final result is memoized (to the sequence `S`).
 *
 * @property palette
 *   The [palette][Palette].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [Stylesheet]. Supplement the supplied [rules][StyleRule] with
 * any missing [rules][StyleRule] required to support the
 * [system&#32;style&#32;classifiers][SystemStyleClassifier].
 *
 * @param originalRules
 *   The original [style&#32;rules][StyleRule] to inject into the
 *   [stylesheet][Stylesheet], prior to adding defaults for missing
 *   [system&#32;style&#32;classifiers][SystemStyleClassifier]. Should be
 *   arranged in declaration order, as this order establishes the override order
 *   during reduction of the final [rendering&#32;context][RenderingContext].
 * @property palette
 *   The [palette][Palette].
 */
class Stylesheet constructor(
	originalRules: Set<StyleRule>,
	private val palette: Palette
)
{
	/**
	 * Construct a [Stylesheet] from (1) the specified map of source patterns
	 * and [style&#32;attributes][StyleAttributes] and (2) the
	 * [palette][Palette] for final color selection.
	 *
	 * @param map
	 *   The map of source patterns to their desired style attributes. Should be
	 *   arranged in declaration order, as this order establishes the override
	 *   order during reduction of the final
	 *   [rendering&#32;context][RenderingContext].
	 * @param palette
	 *   The palette.
	 * @param errors
	 *   The accumulator for errors, as pairs of invalid
	 *   [patterns][UnvalidatedStylePattern] and
	 *   [exceptions][StylePatternException]. When non-`null`, patterns that
	 *   fail [compilation][compile] are excised from the result, and any
	 *   relevant [StylePatternException]s are appended here; when `null`, the
	 *   constructor will terminate abnormally (by raising a
	 *   [StylePatternException]) upon encountering an invalid source pattern.
	 * @throws StylePatternException
	 *   If any of the source patterns fails [compilation][compile] for
	 *   any reason.
	 */
	constructor(
		map: Map<String, StyleAttributes>,
		palette: Palette,
		errors: MutableList<Pair<
			UnvalidatedStylePattern, StylePatternException>>? = null
	): this(compileRules(map, palette, errors), palette)
	{
		// No implementation required.
	}

	/** The [style&#32;rules][StyleRule]. */
	val rules: Set<StyleRule> = originalRules.toMutableSet()

	/**
	 * The [pattern][StylePattern] `"!"` handles renditions of classified
	 * regions that do not match any other [pattern][StylePattern].
	 */
	val noSolutionsRule: StyleRule

	init
	{
		val rules = this.rules as MutableSet
		// The stylesheet might not contain exact match rules for the system
		// classifiers, so insert them here if necessary. None of the
		// compilations here should ever fail, and it should nuke the system if
		// they do.
		SystemStyleClassifier.entries.forEach { systemStyleClassifier ->
			val source = systemStyleClassifier.exactMatchSource
			val rule = rules.firstOrNull { it.source == source }
			if (rule === null)
			{
				val newRule = compile(
					UnvalidatedStylePattern(
						source,
						systemStyleClassifier.defaultRenderingContext
					),
					systemStyleClassifier.palette
				)
				rules.add(newRule)
			}
		}
		// Ensure that a rule is present to handle unclassified source text.
		val unclassifiedRule =
			rules.firstOrNull { it.source == ExactMatchToken.lexeme }
		unclassifiedRule ?: run {
			rules.add(
				compile(
					UnvalidatedStylePattern(
						ExactMatchToken.lexeme,
						UnvalidatedRenderingContext(StyleAttributes())
					),
					palette
				)
			)
		}
		// Ensure that a rule is present to handle empty solution sets.
		val noSolutionsRule =
			rules.firstOrNull { it.source == FailedMatchToken.lexeme }
		this.noSolutionsRule =
			if (noSolutionsRule !== null)
			{
				rules.remove(noSolutionsRule)
				noSolutionsRule
			}
			else
			{
				compile(
					UnvalidatedStylePattern(
						FailedMatchToken.lexeme,
						UnvalidatedRenderingContext(StyleAttributes())
					),
					palette
				)
			}
	}

	/**
	 * The root [tree][StyleRuleTree] for rendering queries.
	 */
	private val rootTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
		StyleRuleTree(rules.mapToSet(transform = StyleRule::initialContext))
	}

	/**
	 * The cached [solutions][RenderingContext] for rendering queries. The input
	 * text is a comma-separated list of style classifiers, such as
	 * [styleToken][AvailLoader.styleToken] produces when classifying regions of
	 * source code. This mechanism provides multiple options for the memory
	 * manager to reduce memory pressure, e.g., dropping cache items, clearing
	 * [references][SoftReference] to successor [trees][StyleRuleTree], etc.
	 */
	private val solutions = LRUCache(
		softCapacity = 2000,
		strongCapacity = 200,
		transformer = ::computeRenderingContext
	)

	/**
	 * Obtain the correct [rendering&#32;context][RenderingContext] for the
	 * specified input text, using a cached solution if possible. The input text
	 * is a comma-separated list of style classifiers, such as
	 * [styleToken][AvailLoader.styleToken] produces when classifying regions of
	 * source code.
	 *
	 * @param classifiersString
	 *   The classification label of some region of source text, as a
	 *   comma-separated list of style classifiers.
	 * @return
	 *   The [rendering&#32;context][RenderingContext] to use for the region of
	 *   source text whence was abstracted the sequence of classifiers.
	 */
	operator fun get(classifiersString: String) = solutions[classifiersString]

	/**
	 * Compute the [rendering&#32;context][RenderingContext] for the specified
	 * input text, which is a comma-separated list of style classifiers, such as
	 * [styleToken][AvailLoader.styleToken] produces when classifying regions of
	 * source code.
	 *
	 * @param classifiersString
	 *   The classification label of some region of source text, as a
	 *   comma-separated list of style classifiers.
	 * @return
	 *   The [rendering&#32;context][RenderingContext] to use for the target
	 *   region of source text.
	 */
	private fun computeRenderingContext(
		classifiersString: String
	): ValidatedRenderingContext
	{
		val tree = findTree(classifiersString)
		// We have reached the tree containing the solution set.
		if (tree.solutions.isEmpty())
		{
			// There are no solutions, then apply the special rule for failed
			// matches.
			return noSolutionsRule.renderingContext
		}
		// Compute the solution frontier, i.e., the subset of maximally specific
		// solutions from the solution set.
		val solutions = mostSpecificSolutions(tree)
		// Aggregate the final rendering context by combining the solutions in
		// order, such that later solutions override earlier ones when aspects
		// are in conflict.
		return distillFinalSolution(solutions)
	}

	/**
	 * Locate the [tree][StyleRuleTree] that contains the solutions for the
	 * specified input text, which is a comma-separated list of style
	 * classifiers, such as [styleToken][AvailLoader.styleToken] produces when
	 * classifying regions of source code.
	 *
	 * @param classifiersString
	 *   The classification label of some region of source text, as a
	 *   comma-separated list of style classifiers.
	 * @return
	 *   The requested [tree][StyleRuleTree].
	 */
	fun findTree(classifiersString: String): StyleRuleTree
	{
		var tree = rootTree
		// Augment the sequence with the special end-of-sequence literal.
		val classifiers = (classifiersString.split(",")
			+ listOf(endOfSequenceLiteral))
		// Find the right subtree, expanding the tree as necessary along the
		// way.
		classifiers.forEach { classifier ->
			// If the tree represents the completion of every rule, then we can
			// stop here without processing the remaining classifiers.
			if (tree.isComplete) return@forEach
			// It's critical to intern the classifiers themselves, as the
			// executor expects to be able to compare canonical strings by
			// reference.
			tree = tree[classifier.intern()]
		}
		return tree
	}

	/**
	 * Answer the most [specific][StylePattern.compareSpecificityTo] solutions
	 * resident in the specified [tree][StyleRuleTree].
	 *
	 * @param tree
	 *   The [tree][StyleRuleTree] to query.
	 * @return
	 *   The most specific solutions available in this [tree].
	 */
	fun mostSpecificSolutions(
		tree: StyleRuleTree
	) = tree.solutions.filter { solution ->
		tree.solutions.none { other ->
			solution.compareSpecificityTo(other).isLess()
		}
	}

	/**
	 * Given the specified [solution&#32;set][solutions], compute the final
	 * [rendering&#32;solution][ValidatedRenderingContext] by
	 * [overriding][ValidatedRenderingContext.overrideWith] the solutions from
	 * left to right, i.e., later contexts override earlier context.
	 *
	 * @param solutions
	 *   The solution set to reduce to a final solution. Must be nonempty.
	 * @return
	 *   The final solution.
	 */
	fun distillFinalSolution(
		solutions: List<ValidatedStylePattern>
	) = solutions
		.map(ValidatedStylePattern::renderingContext)
		.reduce(ValidatedRenderingContext::overrideWith)

	companion object
	{
		/**
		 * Compile the specified map from source patterns to
		 * [style&#32;attributes][StyleAttributes], using the supplied
		 * [palette][Palette] for final color selection.
		 *
		 * @param map
		 *   The map of source patterns to their desired style attributes.
		 * @param palette
		 *   The [palette][Palette].
		 * @param errors
		 *   The accumulator for errors, as pairs of invalid
		 *   [patterns][UnvalidatedStylePattern] and
		 *   [exceptions][StylePatternException]. When non-`null`, patterns that
		 *   fail [compilation][compile] are excised from the result, and any
		 *   relevant [StylePatternException]s are appended here; when `null`,
		 *   the function will terminate abnormally (by raising a
		 *   [StylePatternException]) upon encountering an invalid source
		 *   pattern.
		 * @return
		 *   The compiled [rules][StyleRule].
		 * @throws StylePatternException
		 *   If [errors] is not `null` and any of the source patterns fails
		 *   [compilation][compile] for any reason.
		 */
		private fun compileRules(
			map: Map<String, StyleAttributes>,
			palette: Palette,
			errors: MutableList<Pair<
				UnvalidatedStylePattern, StylePatternException>>? = null
		): Set<StyleRule> =
			map.mapNotNullTo(mutableSetOf()) { (source, attrs) ->
				val context = UnvalidatedRenderingContext(attrs)
				val pattern = UnvalidatedStylePattern(source, context)
				try
				{
					compile(pattern, palette)
				}
				catch (e: StylePatternException)
				{
					errors?.add(pattern to e) ?: throw e
					null
				}
			}
	}
}

/**
 * A [StyleRuleTree] comprises:
 *
 * 1. The complete [set][Set] of live [contexts][StyleRuleContext] for some
 *  position `K` within a sequence `S` of style classifiers,
 * 2. A lazily populated transition table from possible next style classifiers
 *  to successor [trees][StyleRuleTree], and
 * 3. The [solutions][ValidatedStylePattern] accumulated so far during a
 *  traversal from the [root][Stylesheet.rootTree] [tree][StyleRuleTree].
 *
 * @property contexts
 *   The [paused][StyleRuleContextState.PAUSED] [contexts][StyleRuleContext] of
 *   all [rules][StyleRule] live at some position `K` within a sequence `S` of
 *   style classifiers, such that `K` corresponds to the enclosing
 *   [tree][StyleRule].
 * @property solutions
 *   The final [rendering&#32;contexts][ValidatedRenderingContext] for the
 *   classifier sequence that terminates at this [tree][StyleRuleTree].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class StyleRuleTree constructor(
	private val contexts: Set<StyleRuleContext>,
	val solutions: List<ValidatedStylePattern> = emptyList())
{
	/**
	 * The lazy transition table from possible next style classifiers to
	 * successor [trees][StyleRuleTree]. Each successor is held
	 * [softly][SoftReference], to provide an outlet for venting when memory
	 * pressure is high.
	 *
	 * The wildcard transition, denoted by the
	 * [wildcard&#32;sentinel][wildcardSentinel], applies when the incoming
	 * style classifier is not an expected [literal][StyleRuleContext.literal]
	 * for any live [context][StyleRuleContext]. This mechanism serves as a
	 * space-saving technique.
	 */
	private val successors =
		ConcurrentHashMap<String, SoftReference<StyleRuleTree>>()

	/**
	 * Determine whether the [solution&#32;set][solutions] is complete. The set
	 * is complete only when no [contexts] survive. Ordinarily, solutions are
	 * evaluated when the sequence `S` of style classifiers is exhausted, not
	 * when all contexts have been eliminated.
	 */
	val isComplete get() = contexts.isEmpty()

	/**
	 * Accept [classifier] as the next style classifier in the current input
	 * sequence `S`; this involves using the
	 * [wildcard&#32;sentinel][wildcardSentinel] iff none of the live
	 * [contexts][StyleRuleContext] are expecting the incoming [classifier]
	 * explicitly.
	 *
	 * If the [receiver][StyleRuleTree] is [complete][isComplete], then answer
	 * it immediately.
	 *
	 * If the [transition&#32;table][successors] already contains a
	 * [successor][StyleRuleTree], then answer it immediately.
	 *
	 * Otherwise, resume each live [context][StyleContext], using the supplied
	 * [classifier] as input. Update the transition table with the newly
	 * computed successor, and answer that successor.
	 *
	 * @param classifier
	 *   The next classifier from the current input sequence. **Must already be
	 *   [interned][String.intern].**
	 * @return
	 *   The [successor][StyleRuleTree].
	 */
	operator fun get(classifier: String): StyleRuleTree
	{
		if (isComplete) return this
		val literals = contexts.mapNotNull { it.literal }
		val transitionClassifier =
			if (literals.contains(classifier)) classifier
			else wildcardSentinel
		successors[transitionClassifier]?.get()?.let { return it }
		val successor = computeSuccessor(classifier)
		successors[transitionClassifier] = SoftReference(successor)
		return successor
	}

	/**
	 * Actually compute the [successor][StyleRuleTree] for the given
	 * [classifier].
	 *
	 * @param classifier
	 *   The next classifier from the current input sequence. **Must already be
	 *   [interned][String.intern].**
	 * @return
	 *   The [successor][StyleRuleTree].
	 */
	private fun computeSuccessor(classifier: String): StyleRuleTree
	{
		val paused = contexts.toMutableSet()
		val running = paused.drain().mapTo(mutableSetOf()) {
			it.copy(state = RUNNING)
		}
		var solutions = solutions
		while (running.isNotEmpty())
		{
			running.drain().forEach { context ->
				val nextContext =
					execute(context, classifier) { forked -> running.add(forked) }
				when (nextContext.state)
				{
					PAUSED -> paused.add(nextContext)
					RUNNING -> running.add(nextContext)
					ACCEPTED ->
						solutions = solutions.append(nextContext.rule.pattern)
					REJECTED -> {}
				}
			}
		}
		return StyleRuleTree(paused, solutions)
	}

	companion object
	{
		/** The wildcard sentinel for the [transition&#32;table][successors]. */
		private const val wildcardSentinel = ""
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                 Patterns.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * A persistent [stylesheet][Stylesheet] maps unvalidated
 * [style&#32;patterns][StylePattern] onto unvalidated
 * [style&#32;attributes][StyleAttributes]. A [StylePatternCompiler] compiles a
 * valid [StylePattern] with valid [StyleAttributes] into a
 * [ValidatedStylePattern].
 *
 * @property source
 *   The source text of the style pattern.
 * @property renderingContext
 *   The [RenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class StylePattern constructor(
	val source: String,
	open val renderingContext: RenderingContext)
{
	/**
	 * Determine the relative specificity of the receiver and the argument.
	 * The relative specificity of two [patterns][StylePattern] is determined
	 * according to the following criteria:
	 *
	 * * If two patterns are equal, then they are
	 *   _[equally&#32;specific][Order.EQUAL]_.
	 * * All exact matches are mutually _[incomparable][Order.INCOMPARABLE]_.
	 * * Any exact match is _[more&#32;specific][Order.MORE]_ than any inexact
	 *   match.
	 * * For inexact matches, given two [patterns][StylePattern] `P` and `Q`,
	 *   the following rules are applied in order:
	 *    * If `P` contains `Q`, then `P` is _[more&#32;specific][Order.MORE]_.
	 *    * If `|successions(P)|` = `|successions(Q)|`, where `successions`
	 *      computes the [successions][SuccessionToken] of its argument, and
	 *      every succession of `P` is more specific than the corresponding
	 *      succession of `Q`, then `P` is _[more&#32;specific][Order.MORE]_.
	 *    * If `|successions(P)| = 1` and `|successions(Q)| > 1`, then `P` and
	 *      `Q` are _[incomparable][Order.INCOMPARABLE]_.
	 *    * If every succession of `P` occurs within `Q`, preserving order and
	 *      advancing monotonically, and `|P| ≤ |Q|`, then `Q` is
	 *      _[more&#32;specific][Order.MORE]_.
	 *    * Otherwise, `P` and `Q` are _[incomparable][Order.INCOMPARABLE]_.
	 *
	 * @param other
	 *   The pattern to check specificity against.
	 * @return
	 *   The [relation][Order] of the two patterns with respect to
	 *   specificity.
	 */
	fun compareSpecificityTo(other: StylePattern): Order
	{
		// Eliminate whitespace, for simplicity.
		val p = source.replace(findWhitespace, "")
		val q = other.source.replace(findWhitespace, "")
		// If the patterns are identical, then naturally they are equivalent.
		if (p == q)
		{
			// e.g., p: #a <=> q: #a | p: #a,#b<#c,#d <=> q: #a,#b<#c,#d
			return Order.EQUAL
		}
		// Now handle exact matches, because they follow special rules (which
		// are easily/cheaply resolved).
		when (val exactMatchOperator = ExactMatchToken.lexeme.first())
		{
			p.first() ->
				return (
					// e.g., p: =#a <=> q: =#b | p: =#a <=> q: =#a,#b
					if (q.first() == exactMatchOperator) Order.INCOMPARABLE
					// e.g., p: =#a <=> q: #a | p: =#a <=> q: #b
					else Order.MORE)
			q.first() ->
				// e.g., p: #a <=> q: =#b
				return Order.LESS
		}
		// Both patterns are inexact. Now determine if one of the patterns
		// embeds the other entirely. If so, then the larger of the two is the
		// more specific.
		when
		{
			p.containsSubpattern(q) ->
				// e.g., p: #a,#b,#b <=> q: #a,#b
				return Order.MORE
			q.containsSubpattern(p) ->
				// e.g., p: #a,#b <=> q: #a,#b,#b
				return Order.LESS
		}
		// Neither pattern directly embeds the other. Decompose each pattern
		// into its successions. If the succession counts of the two patterns
		// are equal, then ascertain whether the successions are all comparable.
		val pSuccessions = p.split(SubsequenceToken.lexeme)
		val qSuccessions = q.split(SubsequenceToken.lexeme)
		if (pSuccessions.size == qSuccessions.size)
		{
			// e.g., p: #a <=> q: #b
			if (pSuccessions.size == 1) return Order.INCOMPARABLE
			val specificities = pSuccessions.zip(qSuccessions)
				.map { (a, b) -> a.compareSuccessionSpecificityTo(b) }
			return when
			{
				// Every succession of p is less than or equally specific to the
				// corresponding succession of q.
				// e.g., p: #a<#b <=> q: #a,#b<#b
				specificities.all { it.isLessOrEqual() } -> Order.LESS
				// Every succession of p is more than or equally specific to the
				// corresponding succession of q.
				// e.g., p: #a,#b<#c <=> q: #b<#c
				specificities.all { it.isMoreOrEqual() } -> Order.MORE
				// e.g., p: #a<#b <=> q: #c<#d
				// e.g., p: #a<#b <=> q: #a<#c
				// e.g., p: #a,#b<#c <=> q: #a<#c,#d
				else -> Order.INCOMPARABLE
			}
		}
		// The two patterns have unequal succession counts. If either contains
		// only a single succession, then the patterns are incomparable.
		val (shorter, longer) =
			listOf(pSuccessions, qSuccessions).sortedBy { it.size }
		if (shorter.size == 1)
		{
			// e.g., p: #a,#b <=> q: #a<#b | p: #a,#b,#c <=> #a<#b<#c
			// e.g., p: #a<#b <=> q: #a,#b | p: #a<#b<#c <=> #a,#b,#c
			return Order.INCOMPARABLE
		}
		// If every succession of one pattern is comparable to one in the other
		// pattern, preserving order, then the longer pattern is more specific.
		// Otherwise, the patterns are incomparable. Choose the shorter of the
		// two patterns for the iteration, for a minor gain in efficiency.
		val specificities = mutableSetOf<Order>()
		var index = 0
		shorter.forEach { a ->
			index = longer.subList(index, longer.size).indexOfFirst { b ->
				// Find the earliest comparable element. Remember the result of
				// the comparison, for use below.
				val order = a.compareSuccessionSpecificityTo(b)
				if (!order.isIncomparable())
				{
					specificities.add(order)
					return@indexOfFirst true
				}
				false
			}
			if (index == -1)
			{
				// One of the successions of the smaller pattern was incomparable to
				// any succession in the longer pattern. Therefore, the patterns are
				// incomparable.
				// e.g., p: #a<#b <=> q: #b<#a<#c
				return Order.INCOMPARABLE
			}
		}
		return when
		{
			pSuccessions.size < qSuccessions.size ->
			{
				assert(shorter === pSuccessions)
				when
				{
					// Every succession of p is less specific or equally
					// specific to a corresponding succession of q, so p is
					// less specific than q.
					// e.g., p: #a<#b <=> q: #a<#c<#b
					// e.g., p: #a<#b <=> q: #a<#c,#d<#b
					// e.g., p: #a<#b <=> q: #a<#c<#b<#d
					// e.g., p: #a<#b <=> q: #a<#c<#d<#b
					// e.g., p: #a<#b <=> q: #c<#a<#d<#b
					// e.g., p: #a<#b <=> q: #c<#a<#d<#b<#e
					// e.g., p: #a<#b<#c <=> q: #a<#d<#b<#c
					// e.g., p: #a<#b<#c <=> q: #a<#c<#b<#c
					// e.g., p: #a<#a<#b <=> q: #a<#b<#a<#b
					// e.g., p: #a<#b<#a<#b <=> q: #a<#a<#b<#b<#a<#b
					specificities.all { it.isLessOrEqual() } -> Order.LESS
					// e.g., p: #a,b<#c <=> q: #a<#c<#d
					else -> Order.INCOMPARABLE
				}
			}
			specificities.all { it.isLessOrEqual() } ->
			{
				assert(pSuccessions.size > qSuccessions.size)
				assert(shorter === qSuccessions)
				// Every succession of q is less specific or equally
				// specific to a corresponding succession of p, so p is
				// more specific than q.
				// e.g., p: #a<#c<#b <=> q: #a<#b
				// e.g., p: #a<#c,#d<#b <=> q: #a<#b
				// e.g., p: #a<#c<#b<#d <=> q: #a<#b
				// e.g., p: #a<#c<#d<#b <=> q: #a<#b
				// e.g., p: #c<#a<#d<#b <=> q: #a<#b
				// e.g., p: #c<#a<#d<#b<#e <=> q: #a<#b
				// e.g., p: #a<#d<#b<#c <=> q: #a<#b<#c
				// e.g., p: #a<#b<#a<#b <=> q: #a<#a<#b
				// e.g., p: #a<#a<#b<#b<#a<#b <=> q: #a<#b<#a<#b
				Order.MORE
			}
			// e.g., p: #a,#b<#b#<#c <=> q: #a<#b,#c
			else -> Order.INCOMPARABLE
		}
	}

	override fun toString() = source

	companion object
	{
		/**
		 * A [regular&#32;expression][Regex] to find whitespace in a source
		 * pattern.
		 */
		private val findWhitespace by lazy(LazyThreadSafetyMode.PUBLICATION) {
			"\\s+".toRegex()
		}

		/**
		 * Determine whether the [receiver][String] contains the argument as
		 * a subpattern.
		 *
		 * @param other
		 *   The subpattern to detect.
		 * @return
		 *   `true` if the receiver contains [other], `false` otherwise.
		 */
		private fun String.containsSubpattern(other: String): Boolean
		{
			val list = split(findOperator)
			val otherList = other.split(findOperator)
			val otherIndex = list.firstIndexOfSublist(otherList)
			if (otherIndex == -1) return false
			// The operators were elided during the split, but now we have to
			// check them exactly. Each non-last element of a list has an
			// implicit trailing operator, so that tells us how to map the list
			// index back onto a string index so that we can perform a substring
			// comparison.
			val offset =
				otherIndex + list.subList(0, otherIndex).sumOf(String::length)
			return substring(offset, offset + other.length) == other
		}

		/**
		 * A [regular&#32;expression][Regex] to find any operator of a
		 * [pattern][StylePattern].
		 */
		private val findOperator by lazy(LazyThreadSafetyMode.PUBLICATION) {
			"[${SubsequenceToken.lexeme}${SuccessionToken.lexeme}]".toRegex()
		}

		/**
		 * Locate the first occurrence of the [argument][List] as a sublist of
		 * the [receiver][List].
		 *
		 * @param other
		 *   The list to search for within the receiver.
		 * @return
		 *   The index of the first element of [other] within the receiver, or
		 *   `-1` if [other] does not occur.
		 */
		private fun <T> List<T>.firstIndexOfSublist(other: List<T>): Int
		{
			if (other.size > size) return -1
			(0 .. size - other.size).forEach {
				if (subList(it, it + other.size) == other) return it
			}
			return -1
		}

		/**
		 * Determine the relative specificity of the receiver and the argument,
		 * where each is treated as a succession within some pattern.
		 * The relative specificity of two successions is determined
		 * according to the following criteria:
		 *
		 * * If the receiver and argument are equal, then they are
		 *   _[equally&#32;specific][Order.EQUAL]_.
		 * * If the receiver contains the argument, then the receiver is
		 *   _[more&#32;specific][Order.MORE]_.
		 * * Otherwise, the receiver and argument are
		 *   _[equally&#32;specific][Order.EQUAL]_.
		 *
		 * @param other
		 *   The succession to check specificity against.
		 * @return
		 *   The [relation][Order] of the two patterns with respect to
		 *   specificity.
		 */
		private fun String.compareSuccessionSpecificityTo(other: String) =
			when
			{
				// If the patterns are identical, then they are equivalent.
				this == other ->
					// e.g., p: #a <=> q: #a
					Order.EQUAL
				// Determine if one of the successions embeds the other entirely. If
				// so, then the larger of the two is the more specific.
				this.contains(other) ->
					// e.g., p: #a,#b,#b <=> q: #a,#b
					Order.MORE
				other.contains(this) ->
					// e.g., p: #a,#b <=> q: #a,#b,#b
					Order.LESS
				// The successions are incomparable.
				else ->
					Order.INCOMPARABLE
			}
	}
}

/**
 * An unvalidated [style&#32;pattern][StylePattern].
 *
 * @property renderingContext
 *   The [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an [UnvalidatedStylePattern] from the specified source text and
 * [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 *
 * @param source
 *   The source text of the style pattern.
 * @param renderingContext
 *   The [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 */
class UnvalidatedStylePattern constructor(
	source: String,
	override val renderingContext: UnvalidatedRenderingContext
): StylePattern(source, renderingContext)
{
	/**
	 * Validate the [receiver][UnvalidatedStylePattern] against the supplied
	 * [palette][Palette]. If validation succeeds, then answer a
	 * [ValidatedStylePattern] that includes the validated source and all
	 * attributes of the [rendering&#32;context][renderingContext].
	 *
	 * @param palette
	 *   The [Palette], for interpreting the
	 *   [foreground][StyleAttributes.foreground] and
	 *   [background][StyleAttributes.background] colors for text rendition.
	 * @return
	 *   The [ValidatedStylePattern].
	 * @throws RenderingContextValidationException
	 *   If the palette is missing any referenced colors.
	 */
	fun validate(palette: Palette) = ValidatedStylePattern(
		source,
		renderingContext.validate(palette))
}

/**
 * A [style&#32;pattern][StylePattern] that has been successfully validated by
 * a [StylePatternCompiler].
 *
 * @property renderingContext
 *   The [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [ValidatedStylePattern] from the specified source text and
 * [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 *
 * @param source
 *   The source text of the style pattern.
 * @param renderingContext
 *   The [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 */
class ValidatedStylePattern internal constructor(
	source: String,
	override val renderingContext: ValidatedRenderingContext
): StylePattern(source, renderingContext)

////////////////////////////////////////////////////////////////////////////////
//                                 Compiler.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StylePatternCompiler] parses a [pattern][StylePattern], resolves a
 * [rendering&#32;context][RenderingContext] against a [palette][Palette], and
 * generates a [rule][StyleRule] if all syntactic and semantic requirements are
 * met.
 *
 * @property pattern
 *   The [unvalidated&#32;pattern][UnvalidatedStylePattern] to compile.
 * @property palette
 *   The palette for resolution of symbolic colors.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class StylePatternCompiler private constructor(
	private val pattern: UnvalidatedStylePattern,
	private val palette: Palette)
{
	/** The source text of the pattern. */
	private val source get() = pattern.source

	/**
	 * A [Token] represents one of the lexical units of a [StylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal sealed class Token
	{
		/**
		 * The one-based character position with the source pattern of the
		 * beginning of the [lexeme].
		 */
		abstract val position: Int

		/** The lexeme. */
		abstract val lexeme: String

		final override fun toString() = lexeme

		companion object
		{
			/**
			 * The code point of the style classifier prefix.
			 */
			const val classifierPrefixCode = '#'.code
		}
	}

	/**
	 * A [EndOfPatternToken] represents the end of the source pattern.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class EndOfPatternToken constructor(
		override val position: Int
	): Token()
	{
		override val lexeme get() = EndOfPatternToken.lexeme

		companion object
		{
			/** The fake lexeme to use for describing end-of-pattern. */
			const val lexeme = "end of pattern"
		}
	}

	/**
	 * A [StyleClassifierToken] represents a literal style classifier.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class StyleClassifierToken constructor(
		override val position: Int,
		override val lexeme: String
	): Token()

	/**
	 * A [ExactMatchToken] serves as a leading pragma to force exact-match
	 * semantics on the [pattern][StylePattern]. This disables wildcard matching
	 * and the [subsequence][SubsequenceExpression]
	 * [operator][SubsequenceToken].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class ExactMatchToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = ExactMatchToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = "="

			/** The code point of the lexeme. */
			val code = lexeme.first().code
		}
	}

	/**
	 * A [SuccessionToken] represents immediate succession of two subpatterns of
	 * a [StylePattern]. Succession has higher precedence than subsequence.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class SuccessionToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = SuccessionToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = ","

			/** The code point of the lexeme. */
			val code = lexeme.first().code
		}
	}

	/**
	 * A [SubsequenceToken] represents eventual subsequence of the right-hand
	 * subpattern (after the left-hand subpattern). Subsequence has lower
	 * precedence than succession.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class SubsequenceToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = SubsequenceToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = "<"

			/** The code point of the lexeme. */
			val code = lexeme.first().code
		}
	}

	/**
	 * A [FailedMatchToken] serves as a pragma to denote the fall back
	 * [pattern][StylePattern] to apply when all other [patterns][StylePattern]
	 * fail to match a sequence of classifiers. This disables wildcard matching
	 * and the [subsequence][SubsequenceExpression]
	 * [operator][SubsequenceToken].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class FailedMatchToken(
		override val position: Int = 0
	): Token()
	{
		override val lexeme get() = FailedMatchToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = "!"

			/** The code point of the lexeme. */
			val code = lexeme.first().code
		}
	}

	/**
	 * An [InvalidToken] represents unexpected input in an alleged
	 * [StylePattern], and guarantees that the producing pattern is not a
	 * [ValidatedStylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class InvalidToken constructor(
		override val position: Int,
		override val lexeme: String
	): Token()

	/** The [Token]s scanned from the source text of the pattern. */
	private val tokens: List<Token> by lazy {
		val tokens = mutableListOf<Token>()
		val source = source
		var i = 0
		while (true)
		{
			if (i == source.length) break
			val start = i
			val c = source.codePointAt(i)
			i += Character.charCount(c)
			when
			{
				c == FailedMatchToken.code ->
					tokens.add(FailedMatchToken(start + 1))
				c == ExactMatchToken.code ->
					tokens.add(ExactMatchToken(start + 1))
				c == SuccessionToken.code ->
					tokens.add(SuccessionToken(start + 1))
				c == SubsequenceToken.code ->
					tokens.add(SubsequenceToken(start + 1))
				c == classifierPrefixCode ->
				{
					if (i == source.length)
					{
						tokens.add(
							InvalidToken(start + 1, c.toChar().toString()))
					}
					else
					{
						while (i < source.length)
						{
							val p = source.codePointAt(i)
							if (!p.isNonLeadingClassifierCharacter) break
							i += Character.charCount(p)
						}
						tokens.add(
							StyleClassifierToken(
								start + 1, source.substring(start, i)))
					}
				}
				Character.isWhitespace(c) ->
				{
					// No action required.
				}
				else -> tokens.add(InvalidToken(
					start + 1, buildString { appendCodePoint(c) }))
			}
		}
		tokens.add(EndOfPatternToken(i + 1))
		tokens
	}

	/**
	 * A [ParseContext] tracks a parsing theory for a [StylePattern]. A
	 * [StylePatternCompiler] manages a small number of contexts in pursuit of
	 * an unambiguous interpretation of a [StylePattern].
	 *
	 * Note that the parsing algorithm is fully deterministic, so [ParseContext]
	 * is a convenient abstraction for bookkeeping, not an essential one for,
	 * e.g., ambiguity resolution, parallelism, etc.
	 *
	 * @property tokenIndex
	 *   The zero-based position of the next [Token] to consider.
	 * @property operands
	 *   The operand stack, containing fully parsed
	 *   [subexpressions][Expression].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private inner class ParseContext(
		val tokenIndex: Int = 0,
		val operands: List<Expression> = listOf())
	{
		/** The [token] under consideration. */
		val token get() = tokens[tokenIndex]

		/**
		 * The one-based position of the leading character of the [token]
		 * within the source pattern, for error reporting.
		 */
		val position get() = token.position

		/**
		 * Derive a successor [ParseContext] that focuses on the next
		 * [token][Token].
		 */
		val nextContext get() = ParseContext(tokenIndex + 1, operands)

		/**
		 * Derive a successor [ParseContext] that focuses on the next
		 * [token][Token] and includes the specified [subexpression][Expression]
		 * at the top of its [operand&#32;stack][operands].
		 *
		 * @param operand
		 *   The subexpression to push onto the operand stack.
		 * @return
		 *   The requested context.
		 */
		fun with(operand: Expression) = ParseContext(
			tokenIndex + 1,
			operands.append(operand))

		/**
		 * Derive a successor [ParseContext] with an appropriate
		 * [MatchExpression] or [ForkExpression] based on the specified
		 * [classifier]. A [ForkExpression] will be pushed only if the
		 * [classifier] matches the [leading&#32;classifier][leadingClassifier]
		 * in the current [succession][SuccessionExpression].
		 *
		 * @param classifier
		 *   The style classifier.
		 * @param leadingClassifier
		 *   The leading classifier of the current succession, or `null` if this
		 *   call is creating the first subexpression of a new succession.
		 * @return
		 *   The requested context.
		 */
		fun makeMatchExpression(
			classifier: String,
			leadingClassifier: String?
		): ParseContext
		{
			val match = MatchExpression(classifier)
			if (classifier == leadingClassifier)
			{
				return with(ForkExpression(match))
			}
			return with(match)
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top of the
		 * [operand&#32;stack][operands] to an [ExactMatchExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeExactMatchExpression(): ParseContext
		{
			val operand = operands.last()
			return ParseContext(
				tokenIndex,
				operands.withoutLast().append(ExactMatchExpression(operand)))
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top two operands
		 * of the [operand&#32;stack][operands] to a [SuccessionExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeSuccessionExpression(): ParseContext
		{
			val (left, right) =
				operands.subList(operands.size - 2, operands.size)
			val operands = operands.subList(0, operands.size - 2)
			return ParseContext(
				tokenIndex,
				operands.append(SuccessionExpression(left, right)))
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top two operands
		 * of the [operand&#32;stack][operands] to a [SubsequenceExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeSubsequenceExpression(): ParseContext
		{
			val (left, right) =
				operands.subList(operands.size - 2, operands.size)
			val operands = operands.subList(0, operands.size - 2)
			return ParseContext(
				tokenIndex,
				operands.append(SubsequenceExpression(left, right)))
		}

		override fun toString() = buildString {
			append(try { token } catch (e: Exception) { "«bad index»" })
			append('@')
			append(tokenIndex)
			operands.forEach {
				append(" :: ")
				append(it)
			}
		}
	}

	/**
	 * An [Expression] represents an expression within the [StylePattern]
	 * grammar. It serves as the unifying node type for the abstract syntax
	 * tree (AST) of a [StylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private sealed class Expression
	{
		/**
		 * Accept the specified [visitor], dispatching to its appropriate
		 * entry point based on the receiver's own type. Do not automatically
		 * visit any subexpressions; it is the visitor's responsibility to
		 * visit any subexpressions that it cares about.
		 *
		 * @param visitor
		 *   The [visitor][ExpressionVisitor].
		 */
		abstract fun accept(visitor: ExpressionVisitor)

		abstract override fun toString(): String
	}

	/**
	 * A [MatchExpression] represents the intent to match a literal style
	 * classifier.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class MatchExpression(
		val classifier: String
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = classifier
	}

	/**
	 * A [ExactMatchExpression] constrains its [subexpression][Expression] to
	 * exactly match the entire style classifier stream.
	 *
	 * @property child
	 *   The constrained [subexpression][Expression].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class ExactMatchExpression(val child: Expression): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "${ExactMatchToken.lexeme}$child"
	}

	/**
	 * A [SuccessionExpression] constrains its subexpressions to match only if
	 * they are strictly adjacent in the style classifier stream.
	 *
	 * @property left
	 *   The left-hand [subexpression][Expression], which must match first.
	 * @property right
	 *   The right-hand [subexpression][Expression], which must match second,
	 *   without any interleaving positive-width matches.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class SuccessionExpression(
		val left: Expression,
		val right: Expression
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "$left${SuccessionToken.lexeme}$right"
	}

	/**
	 * A [SubsequenceExpression] constrains its right-hand
	 * [subexpression][Expression] to match eventually, irrespective of the
	 * matches that occur after the left-hand subexpression.
	 *
	 * @property left
	 *   The left-hand [subexpression][Expression], which must match first.
	 * @property right
	 *   The right-hand [subexpression][Expression], which must match second,
	 *   after zero or more interleaving positive-width matches.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class SubsequenceExpression(
		val left: Expression,
		val right: Expression
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "$left ${SubsequenceToken.lexeme} $right"
	}

	/**
	 * A [ForkExpression] mandates that the [rule][StyleRule] needs to fork
	 * another [context][StyleRuleContext] in order to correctly match a
	 * self-similar pattern. For this purpose, a pattern is _self-similar_ if it
	 * begins with a repeated prefix.
	 *
	 * @property expression
	 *   The [Expression] that represents the remainder of the transitively
	 *   enclosing [Expression] that occurs after the leading occurrence of the
	 *   repeated prefix.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class ForkExpression(val expression: Expression): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "fork ($expression)"
	}

	/**
	 * A [FailedMatchExpression] only applies when every other [rule][StyleRule]
	 * has failed to match a sequence of classifiers.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private object FailedMatchExpression: Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = FailedMatchToken.lexeme
	}

	/**
	 * An [EmptyClassifierSequenceExpression] matches only an empty stream of
	 * classifiers. It can only serve as an outermost [expression][Expression].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private object EmptyClassifierSequenceExpression: Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = ExactMatchToken.lexeme
	}

	/**
	 * An [ExpressionVisitor] visits the desired [subexpressions][Expression] of
	 * some [expression][Expression], in an order of its own choosing. Because
	 * [Expression.accept] does not automatically visit subexpressions,
	 * implementors of [ExpressionVisitor] must choose which subexpressions to
	 * visit, in what order, and what action to take upon visitation.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private interface ExpressionVisitor
	{
		/**
		 * Visit a [MatchExpression].
		 *
		 * @param expression
		 *   The [MatchExpression].
		 */
		fun visit(expression: MatchExpression)

		/**
		 * Visit a [ExactMatchExpression].
		 *
		 * @param expression
		 *   The [ExactMatchExpression].
		 */
		fun visit(expression: ExactMatchExpression)

		/**
		 * Visit a [SuccessionExpression].
		 *
		 * @param expression
		 *   The [SuccessionExpression].
		 */
		fun visit(expression: SuccessionExpression)

		/**
		 * Visit a [SubsequenceExpression].
		 *
		 * @param expression
		 *   The [SubsequenceExpression].
		 */
		fun visit(expression: SubsequenceExpression)

		/**
		 * Visit a [ForkExpression].
		 *
		 * @param expression
		 *   The [ForkExpression].
		 */
		fun visit(expression: ForkExpression)

		/**
		 * Visit a [EmptyClassifierSequenceExpression].
		 *
		 * @param expression
		 *   The [EmptyClassifierSequenceExpression].
		 */
		fun visit(expression: EmptyClassifierSequenceExpression)

		/**
		 * Visit a [FailedMatchExpression].
		 *
		 * @param expression
		 *   The [FailedMatchExpression].
		 */
		fun visit(expression: FailedMatchExpression)
	}

	/** The outermost [expression][Expression] parsed from the [source]. */
	private val expression: Expression by lazy {
		val context = parseOutermost()
		assert(context.tokenIndex == tokens.size) {
			"parsing did not reach the end of the token stream"
		}
		val operands = context.operands
		assert(operands.size == 1) { "expected stack depth = 1" }
		operands.first()
	}

	/**
	 * Parse an outermost [expression][Expression].
	 *
	 * @return
	 *   The final [ParseContext].
	 */
	private fun parseOutermost(): ParseContext
	{
		val context = ParseContext()
		val next = when (val token = context.token)
		{
			is FailedMatchToken -> context.with(FailedMatchExpression)
			is ExactMatchToken ->
			{
				val next = context.nextContext
				when (next.token)
				{
					is EndOfPatternToken -> context.with(
						EmptyClassifierSequenceExpression)
					else -> parseSuccession(next, allowSubsequence = false)
						.makeExactMatchExpression()
				}
			}
			is StyleClassifierToken -> parseSubsequence(context)
			else -> throw StylePatternException(
				context.position,
				"expected failed match pragma (${FailedMatchToken.lexeme}), "
					+ "exact match pragma (${ExactMatchToken.lexeme}), or "
					+ "classifier (#…), but found $token")
		}
		if (next.token !is EndOfPatternToken)
		{
			throw StylePatternException(
				next.position,
				"expected end of pattern")
		}
		// Consume the end-of-pattern token. The caller expects the token stream
		// to be fully exhausted.
		return next.nextContext
	}

	/**
	 * Parse a [SubsequenceExpression], [SuccessionExpression], or
	 * [MatchExpression] at the specified [ParseContext].
	 *
	 * @param context
	 *   The initial context for the parse.
	 */
	private fun parseSubsequence(context: ParseContext): ParseContext
	{
		val next = when (val token = context.token)
		{
			is StyleClassifierToken -> parseSuccession(context)
			else -> throw StylePatternException(
				context.position,
				"expected classifier (#…), but found $token")
		}
		return when (val token = next.token)
		{
			is EndOfPatternToken -> next
			is SubsequenceToken -> parseSubsequence(next.nextContext)
				.makeSubsequenceExpression()
			else -> throw StylePatternException(
				next.position,
				"expected subsequence operator (<) or end of pattern, "
					+ "but found $token")
		}
	}

	/**
	 * Parse a [SuccessionExpression] or a [MatchExpression] at the specified
	 * [ParseContext].
	 *
	 * @param context
	 *   The initial context for the parse.
	 * @param leadingClassifier
	 *   The leading classifier of the succession, or `null` if this is the
	 *   opening parse of a new succession.
	 * @param allowSubsequence
	 *   Whether to permit the appearance of a [SubsequenceToken] as an
	 *   expression delimiter. When `false`, no [ForkExpression]s will be
	 *   generated.
	 */
	private fun parseSuccession(
		context: ParseContext,
		leadingClassifier: String? = null,
		allowSubsequence: Boolean = true
	): ParseContext
	{
		val next = when (val token = context.token)
		{
			is StyleClassifierToken -> context.makeMatchExpression(
				token.lexeme,
				if (allowSubsequence) leadingClassifier else null)
			else -> throw StylePatternException(
				context.position,
				"expected classifier (#…), but found $token")
		}
		val leading = leadingClassifier ?: context.token.lexeme
		return when (val token = next.token)
		{
			is EndOfPatternToken -> next
			is SuccessionToken ->
				parseSuccession(next.nextContext, leading, allowSubsequence)
					.makeSuccessionExpression()
			is SubsequenceToken ->
			{
				if (allowSubsequence) next
				else throw StylePatternException(
					next.position,
					"expected succession operator (,) or end of pattern, "
						+ "but found $token")
			}
			else -> throw StylePatternException(
				next.position,
				"expected succession operator (,), subsequence operator (<), "
					+ "or end of pattern, but found $token")
		}
	}

	/**
	 * A [CodeGenerator] produces a [rule][StyleRule] from an outermost
	 * [expression][Expression].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private inner class CodeGenerator: ExpressionVisitor
	{
		/**
		 * Whether or not to generate code performs exact matching of complete
		 * style classifier streams.
		 */
		var matchExactly = false

		/**
		 * The zero-based indices of the literal style classifiers, keyed by
		 * the classifiers themselves. The key set is required to preserve
		 * insertion order.
		 */
		val literals = mutableMapOf<String, Int>()

		/**
		 * Obtain the index of specified literal style classifier, first
		 * allocating a new index if necessary.
		 *
		 * @param literal
		 *   The literal.
		 * @return
		 *   The index of the specified literal.
		 */
		private fun literalIndex(literal: String) =
			literals.computeIfAbsent(literal) {
				literals.size
			}

		/**
		 * The program counter at the start of the next
		 * [instruction][StyleRuleInstruction] to [accumulate][accumulator].
		 */
		private val programCounter get() = accumulator.size

		/**
		 * The dynamically scoped branch-back target to use for failed
		 * [MatchLiteralClassifierOrJumpN]s generated by the function passed to
		 * [withTarget].
		 */
		private var target: Int? = null

		/**
		 * Set up the branch-back [target] for failed
		 * [MatchLiteralClassifierOrJumpN]s, but only if currently unset. Then
		 * perform the specified [action]. Clear the branch-back target if it
		 * was clear when the call started.
		 *
		 * @param action
		 *   The action to perform, using the stored target.
		 */
		private fun withTarget(action: ()->Unit)
		{
			val shouldClearTarget = target === null
			if (shouldClearTarget) target = programCounter
			action()
			if (shouldClearTarget) target = null
		}

		/**
		 * The accumulator for coded [instructions][StyleRuleInstruction],
		 * populated by the various implementations of [visit].
		 */
		private val accumulator = NybbleOutputStream(16)

		/** The complete coded [instruction][StyleRuleInstruction] stream. */
		val instructions: NybbleArray by lazy {
			expression.accept(this)
			accumulator.toNybbleArray()
		}

		override fun visit(expression: MatchExpression)
		{
			if (matchExactly)
			{
				when (val literalIndex = literalIndex(expression.classifier))
				{
					0 -> MatchLiteralClassifier0.emitOn(accumulator)
					1 -> MatchLiteralClassifier1.emitOn(accumulator)
					2 -> MatchLiteralClassifier2.emitOn(accumulator)
					3 -> MatchLiteralClassifier3.emitOn(accumulator)
					else -> MatchLiteralClassifierN.emitOn(
						accumulator,
						literalIndex)
				}
			}
			else
			{
				withTarget {
					when (val literalIndex =
						literalIndex(expression.classifier))
					{
						0 -> MatchLiteralClassifierOrJump0.emitOn(
							accumulator,
							target!!)
						1 -> MatchLiteralClassifierOrJump1.emitOn(
							accumulator,
							target!!)
						2 -> MatchLiteralClassifierOrJump2.emitOn(
							accumulator,
							target!!)
						3 -> MatchLiteralClassifierOrJump3.emitOn(
							accumulator,
							target!!)
						else -> MatchLiteralClassifierOrJumpN.emitOn(
							accumulator,
							literalIndex,
							target!!)
					}
				}
			}
		}

		override fun visit(expression: ExactMatchExpression)
		{
			matchExactly = true
			expression.child.accept(this)
			// For correct operation, we need to explicitly match the
			// end-of-sequence classifier; otherwise, we will accept sequences
			// with acceptable prefixes that do not match in their totality.
			MatchEndOfSequence.emitOn(accumulator)
		}

		override fun visit(expression: SuccessionExpression)
		{
			withTarget {
				expression.left.accept(this)
				expression.right.accept(this)
			}
		}

		override fun visit(expression: SubsequenceExpression)
		{
			expression.left.accept(this)
			expression.right.accept(this)
		}

		override fun visit(expression: ForkExpression)
		{
			when (val target = target!!)
			{
				0 -> Fork.emitOn(accumulator)
				else -> ForkN.emitOn(accumulator, target)
			}
			expression.expression.accept(this)
		}

		override fun visit(expression: EmptyClassifierSequenceExpression)
		{
			matchExactly = true
			MatchEndOfSequence.emitOn(accumulator)
		}

		override fun visit(expression: FailedMatchExpression)
		{
			// Don't emit any instructions. Only one rule can implement this
			// expression within an entire StyleRuleTree, and that rule is
			// stored and handled specially.
		}
	}

	/** The [rule][StyleRule] compiled from the [source]. */
	val rule: StyleRule by lazy {
		val codeGenerator = CodeGenerator()
		StyleRule(
			pattern.validate(palette),
			codeGenerator.instructions,
			codeGenerator.literals.keys.toList())
	}

	companion object
	{
		/**
		 * `true` iff the receiving code point is a valid style classifier
		 * character.
		 */
		private val Int.isNonLeadingClassifierCharacter: Boolean get() =
			when (this)
			{
				'-'.code -> true
				in '0'.code .. '9'.code -> true
				in 'A'.code .. 'Z'.code -> true
				in 'a'.code .. 'z'.code -> true
				else -> false
			}

		/**
		 * Compile the specified [pattern] into a [rule][StyleRule], using the
		 * supplied [palette][Palette] to resolve any symbolic colors to actual
		 * [colors][Color].
		 *
		 * @param pattern
		 *   The [unvalidated&#32;pattern][UnvalidatedStylePattern] to compile.
		 * @param palette
		 *   The palette for resolution of symbolic colors.
		 * @return
		 *   The compiled rule.
		 * @throws StylePatternException
		 *   If [pattern] could not be compiled for any reason.
		 * @throws RenderingContextValidationException
		 *   If [validation][UnvalidatedRenderingContext.validate] of
		 *   [pattern]'s [rendering&#32;context][RenderingContext] failed for
		 *   any reason.
		 */
		@Throws(RenderingContextValidationException::class)
		fun compile(pattern: UnvalidatedStylePattern, palette: Palette) =
			StylePatternCompiler(pattern, palette).rule
	}
}

/**
 * Raised when [compilation][StylePatternCompiler] of a [StylePattern] fails
 * for any reason.
 *
 * @property position
 *   The one-based character position at which the error was detected.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [StylePatternException].
 *
 * @param position
 *   The one-based character position at which the error was detected.
 * @param problem
 *   A brief message about the error that occurred.
 * @param cause
 *   The causal exception, if any.
 */
class StylePatternException(
	val position: Int,
	problem: String,
	cause: Exception? = null
): Exception("pattern error @ character #$position: $problem", cause)

////////////////////////////////////////////////////////////////////////////////
//                                   Rules.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StyleRule] is a [pattern][ValidatedStylePattern]-matching program produced
 * by a [StylePatternCompiler]. A runtime [stylesheet][Stylesheet] aggregates
 * all rules that should be considered when determining how Avail source text
 * should be rendered.
 *
 * @property pattern
 *   The [validated&#32;pattern][ValidatedStylePattern].
 * @property instructions
 *   The [instructions][StyleRuleInstruction] that implement the
 *   [pattern][ValidatedStylePattern]-matching program.
 * @property literals
 *   The literal values recorded by the [compiler][StylePatternCompiler],
 *   corresponding to the fixed style classifiers embedded in the
 *   [pattern][ValidatedStylePattern]. Will be [interned][intern] prior to
 *   internal storage, to accelerate matching during
 *   [execution][StyleRuleExecutor.execute].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [StyleRule].
 *
 * @param pattern
 *   The [validated&#32;pattern][ValidatedStylePattern].
 * @param instructions
 *   The [instructions][StyleRuleInstruction] that implement the
 *   [pattern][ValidatedStylePattern]-matching program.
 * @param literals
 *   The literal values recorded by the [compiler][StylePatternCompiler],
 *   corresponding to the fixed style classifiers embedded in the
 *   [pattern][ValidatedStylePattern]. Will be [interned][intern] prior to
 *   internal storage, to accelerate matching during
 *   [execution][StyleRuleExecutor.execute].
 */
class StyleRule constructor(
	val pattern: ValidatedStylePattern,
	val instructions: NybbleArray,
	literals: List<String>)
{
	/** The source text whence the rule was compiled. */
	val source get() = pattern.source

	/**
	 * The [validated&#32;rendering&#32;context][ValidatedRenderingContext] to
	 * apply when this rule is not obsoleted by a more specific rule during
	 * aggregate matching.
	 */
	val renderingContext get() = pattern.renderingContext

	/** The [interned][String.intern] literals. */
	private val literals = literals.map  { it.intern() }

	/**
	 * Answer the literal value at the requested index. Should generally only be
	 * invoked by a [StyleRuleInstruction] during its
	 * [execution][StyleRuleExecutor.execute].
	 *
	 * @param index
	 *   The index of the desired literal value.
	 * @return
	 *   The requested literal value.
	 * @throws IndexOutOfBoundsException
	 *   If [index] is out of bounds.
	 */
	fun literalAt(index: Int) = literals[index]

	/** The initial [StyleRuleContext] for running the receiver. */
	val initialContext get() = StyleRuleContext(this, 0)

	override fun toString() = buildString {
		append(source)
		append("\nnybblecodes:\n\t")
		if (instructions.size == 0)
		{
			append("[no instructions]")
		}
		else
		{
			append(instructions)
		}
		append("\ninstructions:")
		val reader = instructions.inputStream()
		if (reader.atEnd)
		{
			append("\n\t[no instructions]")
		}
		while (!reader.atEnd)
		{
			val programCounter = reader.position
			val instruction = decodeInstruction(reader)
			val assembly = instruction.toString()
			val annotated = assembly.replace(findLiteralIndex) {
				val literalIndex = it.groupValues[1].toInt()
				"#$literalIndex <${literalAt(literalIndex)}>"
			}
			append("\n\t@$programCounter: ")
			append(annotated)
		}
		append("\nrenderingContext:")
		val ugly = renderingContext.toString()
		val pretty = ugly
			.replace("RenderingContext(", "\n\t")
			.replace("=", " = ")
			.replace(", ", "\n\t")
			.replace(")", "")
			.trimEnd()
		if (pretty.isEmpty())
		{
			append("\n\t[no overrides]")
		}
		else
		{
			append(pretty)
		}
	}

	companion object
	{
		/**
		 * A [regular&#32;expression][Regex] to find a literal index in
		 * disassembly text.
		 */
		private val findLiteralIndex by lazy(LazyThreadSafetyMode.PUBLICATION) {
			"#(\\d+)".toRegex()
		}
	}
}

/**
 * A [StyleRuleInstructionCoder] implements a strategy for correlating
 * [instructions][StyleRuleInstruction] and their decoders. Subclasses
 * self-register simply by calling the superconstructor.
 *
 * @property opcode
 *   The [opcode][StyleRuleInstructionOpcode] of the associated
 *   [instruction][StyleRuleInstruction].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("LeakingThis")
sealed class StyleRuleInstructionCoder constructor(
	val opcode: StyleRuleInstructionOpcode
)
{
	init
	{
		val ordinal = opcode.ordinal
		assert(!opcodes.contains(ordinal)) {
			"opcode $ordinal is already bound to ${opcodes[ordinal]}"
		}
		opcodes[opcode.ordinal] = this
	}

	/**
	 * Encode the associated [instruction][StyleRuleInstruction] onto the
	 * specified [NybbleOutputStream]. Subclasses should call the
	 * superimplementation to ensure that the [opcode] is correctly encoded.
	 * This protocol exists to permit the
	 * [code&#32;generator][StylePatternCompiler.CodeGenerator] to emit
	 * instructions without instantiating them.
	 *
	 * @param nybbles
	 *   The destination for the coded instruction.
	 * @param operands
	 *   The operands to emit.
	 */
	open fun emitOn(
		nybbles: NybbleOutputStream,
		vararg operands: Int)
	{
		nybbles.opcode(opcode.ordinal)
		operands.forEach { nybbles.vlq(it) }
	}

	/**
	 * Decode the operands of the associated [instruction][StyleRuleInstruction]
	 * from the specified [NybbleInputStream]. Note that the [opcode] was just
	 * decoded from this same stream. This protocol exists primarily to support
	 * debugging, as [execution][StyleRuleExecutor] does not require reification
	 * of the instructions themselves.
	 *
	 * @param nybbles
	 *   The encoded instruction stream.
	 * @return
	 *   The decoded instruction.
	 */
	protected abstract fun decodeOperands(
		nybbles: NybbleInputStream
	): StyleRuleInstruction

	companion object
	{
		/**
		 * The registry of [opcodes][StyleRuleInstructionCoder], keyed by the
		 * opcode value.
		 */
		private val opcodes = mutableMapOf<Int, StyleRuleInstructionCoder>()

		/**
		 * Decode an [instruction][StyleRuleInstruction] from the specified
		 * [NybbleInputStream]. This protocol exists primarily to support
		 * debugging, as [execution][StyleRuleExecutor] does not require
		 * reification of the instructions themselves.
		 *
		 * @param nybbles
		 *   The encoded instruction stream.
		 * @return
		 *   The decoded instruction.
		 */
		fun decodeInstruction(nybbles: NybbleInputStream) =
			opcodes[nybbles.opcode()]!!.decodeOperands(nybbles)
	}
}

/**
 * A [StyleRuleInstruction] is an indivisible unit of behavior within a
 * [style&#32;rule][StyleRule]. Instructions are neither generated nor executed
 * in reified form; the class hierarchy exists only to support disassembly for
 * debugging and provide loci for documentation of behavior.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed interface StyleRuleInstruction
{
	abstract override fun toString(): String

	/**
	 * A [StyleRuleInstructionOpcode] uses its [ordinal] to represent the opcode
	 * for a [StyleRuleInstruction]. The variants must **not** be reordered.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class StyleRuleInstructionOpcode
	{
		/** Opcode for [MatchLiteralClassifier0]. */
		MATCH_LITERAL_CLASSIFIER_0 // 0x0
		{
			override fun literalIndex(reader: NybbleInputStream) = 0

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifier1]. */
		MATCH_LITERAL_CLASSIFIER_1 // 0x1
		{
			override fun literalIndex(reader: NybbleInputStream) = 1

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifier2]. */
		MATCH_LITERAL_CLASSIFIER_2 // 0x2
		{
			override fun literalIndex(reader: NybbleInputStream) = 2

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifier3]. */
		MATCH_LITERAL_CLASSIFIER_3 // 0x3
		{
			override fun literalIndex(reader: NybbleInputStream) = 3

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierN]. */
		MATCH_LITERAL_CLASSIFIER_N // 0x4
		{
			override fun literalIndex(reader: NybbleInputStream) =
				reader.unvlq() + 4

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierOrJump0]. */
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_0 // 0x5
		{
			override fun literalIndex(reader: NybbleInputStream) = 0

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifierOrJump(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.unvlq(),
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierOrJump1]. */
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_1 // 0x6
		{
			override fun literalIndex(reader: NybbleInputStream) = 1

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifierOrJump(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.unvlq(),
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierOrJump2]. */
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_2 // 0x7
		{
			override fun literalIndex(reader: NybbleInputStream) = 2

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifierOrJump(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.unvlq(),
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierOrJump3]. */
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_3 // 0x8
		{
			override fun literalIndex(reader: NybbleInputStream) = 3

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifierOrJump(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.unvlq(),
				reader.position
			)
		},

		/** Opcode for [MatchLiteralClassifierOrJumpN]. */
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_N // 0x9
		{
			override fun literalIndex(reader: NybbleInputStream) =
				reader.unvlq() + 4

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifierOrJump(
				context,
				classifier,
				literal(rule, reader)!!,
				reader.unvlq(),
				reader.position
			)
		},

		/** Opcode for [Fork]. */
		FORK // 0xA
		{
			override fun literalIndex(reader: NybbleInputStream) = -1

			override fun literal(rule: StyleRule, reader: NybbleInputStream) =
				null

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeFork(
				context,
				0,
				injector,
				reader.position
			)
		},

		/** Opcode for [ForkN]. */
		FORK_N // 0xB
		{
			override fun literalIndex(reader: NybbleInputStream) = -1

			override fun literal(
				rule: StyleRule,
				reader: NybbleInputStream
			): String?
			{
				// Consume the program counter.
				reader.unvlq()
				return null
			}

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeFork(
				context,
				reader.unvlq() + 1,
				injector,
				reader.position
			)
		},

		/** Opcode for [MatchEndOfSequence]. */
		MATCH_END_OF_SEQUENCE // 0xC
		{
			override fun literalIndex(reader: NybbleInputStream) = -1
			override fun literal(rule: StyleRule, reader: NybbleInputStream) =
				endOfSequenceLiteral

			override fun execute(
				classifier: String,
				context: StyleRuleContext,
				rule: StyleRule,
				reader: NybbleInputStream,
				injector: (StyleRuleContext)->Unit
			) = StyleRuleExecutor.executeMatchLiteralClassifier(
				context,
				classifier,
				endOfSequenceLiteral,
				reader.position
			)
		};

		/**
		 * Answer the index of the target literal within a [StyleRule],
		 * reading nybbles from the [reader] only if necessary.
		 *
		 * @param reader
		 *   A [NybbleInputStream] for decoding additional operands, as
		 *   necessary. Not all implementations will consume data from the
		 *   reader.
		 * @return
		 *   The requested index, or `-1` if no literal index is associated
		 *   with the specified instruction.
		 */
		protected abstract fun literalIndex(reader: NybbleInputStream): Int

		/**
		 * Answer the literal associated with the
		 * [instruction][StyleRuleInstruction] associated with the receiver.
		 *
		 * @param rule
		 *   The [rule][StyleRule] whose literal is desired.
		 * @param reader
		 *   A [NybbleInputStream] for decoding additional operands, as
		 *   necessary. Not all invocations will consume data from the reader.
		 *   After the call, the reader is positioned after the
		 *   [instruction][StyleRuleInstruction].
		 * @return
		 *   The requested literal value, or `null` if no literal is
		 *   associated with [instructions][StyleRuleInstruction] of this kind.
		 */
		open fun literal(rule: StyleRule, reader: NybbleInputStream): String? =
			rule.literalAt(literalIndex(reader))

		/**
		 * Execute the [instruction][StyleRuleInstruction] associated with the
		 * receiver.
		 *
		 * @param classifier
		 *   The style classifier.
		 * @param context
		 *   The [context][StyleRuleContext] just prior to execution of the
		 *   instruction.
		 * @param rule
		 *   The [rule][StyleRule] of which the
		 *   [instruction][StyleRuleInstruction] is a part.
		 * @param reader
		 *   A [NybbleInputStream] for decoding additional operands, as
		 *   necessary. Not all invocations will consume data from the reader.
		 *   After the call, the reader is positioned after the
		 *   [instruction][StyleRuleInstruction].
		 * @param injector
		 *   How to inject a forked [context][StyleRuleContext] into the pool of
		 *   pending contexts.
		 * @return
		 *   The successor [context][StyleRuleContext].
		 */
		abstract fun execute(
			classifier: String,
			context: StyleRuleContext,
			rule: StyleRule,
			reader: NybbleInputStream,
			injector: (StyleRuleContext)->Unit
		): StyleRuleContext

		companion object
		{
			/** The entries. */
			val all = entries.toTypedArray()
		}
	}
}

/**
 * Match a style classifier against literal `#0`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier0:
	StyleRuleInstructionCoder(MATCH_LITERAL_CLASSIFIER_0),
	StyleRuleInstruction
{
	override fun toString() = "match literal #0"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier0
}

/**
 * Match a style classifier against literal `#1`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier1:
	StyleRuleInstructionCoder(MATCH_LITERAL_CLASSIFIER_1),
	StyleRuleInstruction
{
	override fun toString() = "match literal #1"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier1
}

/**
 * Match a style classifier against literal `#2`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier2:
	StyleRuleInstructionCoder(MATCH_LITERAL_CLASSIFIER_2),
	StyleRuleInstruction
{
	override fun toString() = "match literal #2"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier2
}

/**
 * Match a style classifier against literal `#3`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier3:
	StyleRuleInstructionCoder(MATCH_LITERAL_CLASSIFIER_3),
	StyleRuleInstruction
{
	override fun toString() = "match literal #3"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier3
}

/**
 * Match a style classifier against literal `#N`, where `N` is supplied as an
 * immediate operand. On success, fall through to the next
 * [instruction][StyleRuleInstruction] of the enclosing [rule][StyleRule] and
 * [pause][PAUSED]; on failure, [fail][REJECTED] the enclosing
 * [rule][StyleRule]. Note that the encoding offsets the literal index by `-4`,
 * so this cannot be used to encode indices ≤ `4`.
 *
 * @property literalIndex
 *   The index of the literal style classifier to match.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierN constructor(
	private val literalIndex: Int
): StyleRuleInstruction
{
	init
	{
		assert(literalIndex >= 4) { "literal index must be ≥ 4" }
	}

	override fun toString() = "match literal #$literalIndex"

	companion object : StyleRuleInstructionCoder(MATCH_LITERAL_CLASSIFIER_N)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 4) { "literal index must be ≥ 4" }
			nybbles.opcode(opcode.ordinal)
			nybbles.vlq(operands[0] - 4)
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierN(nybbles.unvlq() + 4)
	}
}

/**
 * Match a style classifier against literal `#0`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump0 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #0 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_0
	)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump0(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#1`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump1 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #1 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_1
	)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump1(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#2`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump2 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #2 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_2
	)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump2(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#3`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump3 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #3 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_3
	)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump3(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#N`, where `N` is supplied as an
 * immediate operand. On success, fall through to the next
 * [instruction][StyleRuleInstruction] of the enclosing [rule][StyleRule] and
 * [pause][PAUSED]; on failure, jump to the target instruction. Note that the
 * encoding offsets the literal index by `-4`, so this cannot be used to encode
 * indices ≤ `4`.
 *
 * @property literalIndex
 *   The index of the literal style classifier to match.
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJumpN constructor(
	private val literalIndex: Int,
	private val jumpTarget: Int
): StyleRuleInstruction
{
	init
	{
		assert(literalIndex >= 4) { "literal index must be ≥ 4" }
	}

	override fun toString() =
		"match literal #$literalIndex or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(
		MATCH_LITERAL_CLASSIFIER_OR_JUMP_N
	)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 4) { "literal index must be ≥ 4" }
			nybbles.opcode(opcode.ordinal)
			nybbles.vlq(operands[0] - 4)
			nybbles.vlq(operands[1])
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJumpN(nybbles.unvlq() + 4, nybbles.unvlq())
	}
}

/**
 * Unconditionally fork the current [context][StyleRuleContext], setting its
 * program counter to `0`. This supports matching of [rules][StyleRule] compiled
 * from [patterns][StylePattern] with repeated prefixes, and optimizes for the
 * case where the repetition occurs in the first succession. Always succeeds.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object Fork: StyleRuleInstructionCoder(FORK), StyleRuleInstruction
{
	override fun toString() = "fork to @0"
	override fun decodeOperands(nybbles: NybbleInputStream) = Fork
}

/**
 * Unconditionally fork the current [context][StyleRuleContext], setting its
 * program counter to `N`, where `N` is supplied as an immediate operand.. This
 * supports matching of [rules][StyleRule] compiled from
 * [patterns][StylePattern] with repeated prefixes, and optimizes for the case
 * where the repetition occurs in the first succession.
 *
 * @property forkTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction] for resumption by the new
 *   [context][StyleRuleContext]. The offset is relative to the start of the
 *   instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class ForkN constructor(
	private val forkTarget: Int
): StyleRuleInstruction
{
	init
	{
		assert(forkTarget >= 1) { "fork target must be ≥ 1" }
	}

	override fun toString() = "fork to @$forkTarget"

	companion object : StyleRuleInstructionCoder(FORK_N)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 1) { "fork target must be ≥ 1" }
			nybbles.opcode(opcode.ordinal)
			nybbles.vlq(operands[0] - 1)
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			ForkN(nybbles.unvlq() + 1)
	}
}

/**
 * Match a style classifier against the special end-of-sequence classifier,
 * represented by the empty string. On success, [succeed][ACCEPTED] the
 * enclosing rule; on failure, [fail][REJECTED] the enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchEndOfSequence:
	StyleRuleInstructionCoder(MATCH_END_OF_SEQUENCE),
	StyleRuleInstruction
{
	override fun toString() = "match end of sequence"
	override fun decodeOperands(nybbles: NybbleInputStream) = MatchEndOfSequence
}

////////////////////////////////////////////////////////////////////////////////
//                                 Execution.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StyleRuleContext] represents the complete machine state of a running
 * [StyleRule].
 *
 * @property rule
 *   The [rule][StyleRule] that generated this context.
 * @property programCounter
 *   The zero-based nybble index of the next [instruction][StyleRuleInstruction]
 *   to execute from the [rule][StyleRule]. This is relative to the based of the
 *   coded instruction stream.
 * @property state
 *   The [execution&#32;state][StyleRuleContextState].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
data class StyleRuleContext constructor(
	val rule: StyleRule,
	val programCounter: Int,
	val state: StyleRuleContextState = PAUSED)
{
	/**
	 * The literal style classifier that the [rule] will attempt to match given
	 * this [context][StyleRuleContext], or `null` if the
	 * [program&#32;counter][programCounter] is at the end of the
	 * [instruction][StyleRuleInstruction] [stream][StyleRule.instructions].
	 */
	val literal get(): String?
	{
		// Find the first instruction that matches a style classifier.
		val reader = rule.instructions.inputStream(programCounter)
		while (true)
		{
			val ordinal = reader.read()
			if (ordinal == -1) return null
			StyleRuleInstructionOpcode.all[ordinal].let { opcode ->
				opcode.literal(rule, reader).let { literal ->
					return literal
				}
			}
		}
	}

	/**
	 * A transform of the receiver without any representational singularities.
	 * This simplifies detection of [successful][StyleRuleContextState.ACCEPTED]
	 * contexts.
	 */
	val normalized get() =
		when
		{
			state == REJECTED -> this
			programCounter == rule.instructions.size -> copy(state = ACCEPTED)
			else -> this
		}

	override fun toString() = "@{${rule.source} :: @$programCounter, $state}"
}

/**
 * The execution state of a [style&#32;rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class StyleRuleContextState
{
	/**
	 * The enclosing [context][StyleRuleContext] is paused, waiting for another
	 * style classifier to become available.
	 */
	PAUSED,

	/**
	 * The enclosing [context][StyleRuleContext] is currently running.
	 */
	RUNNING,

	/**
	 * The enclosing [context][StyleRuleContext] has accepted a style classifier
	 * sequence.
	 */
	ACCEPTED,

	/**
	 * The enclosing [context][StyleRuleContext] has rejected a style classifier
	 * sequence.
	 */
	REJECTED
}

/**
 * The [StyleRuleExecutor] is a stateless virtual machine that accepts a
 * [context][StyleRuleContext] and [executes][execute] one or more instructions of
 * its [rule][StyleRule], returning control immediately upon detecting that a
 * derivative of the initial context has left the
 * [RUNNING]&nbsp;[state][StyleRuleContextState]. The executor uses a supplied
 * injector to feed [forked][ForkN] contexts back into the pool of pending
 * contexts. It should be run iteratively against a lineage of contexts and a
 * sequence of classifiers. The special [end-of-sequence][endOfSequenceLiteral]
 * classifier should terminate a sequence of classifiers (unless the rule
 * [rejects][REJECTED] the sequence preemptively).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object StyleRuleExecutor
{
	/**
	 * Using the machine state recorded in the
	 * [initial&#32;context][initialContext], run the associated
	 * [rule][StyleRule] until leaves the [RUNNING]&nbsp;
	 * [state][StyleRuleContextState].
	 *
	 * @param initialContext
	 *   The initial [context][StyleRuleContext]. Must be in the [RUNNING]
	 *   state.
	 * @param classifier
	 *   The _[interned][String.intern]_ style classifier, which is possibly the
	 *   special [end-of-sequence&#32;classifier][endOfSequenceLiteral].
	 *   **Non-interned classifiers will not match literals correctly, because
	 *   identity checks, not value checks, are used for efficiency!**
	 * @param injector
	 *   How to inject a forked [context][StyleRuleContext] into the pool of
	 *   pending contexts.
	 * @return
	 *   The context after leaving the [RUNNING] state.
	 */
	fun execute(
		initialContext: StyleRuleContext,
		classifier: String,
		injector: (StyleRuleContext) -> Unit
	): StyleRuleContext
	{
		assert(initialContext.state == RUNNING)
		val rule = initialContext.rule
		val reader = rule.instructions.inputStream(0)
		var context = initialContext
		while (context.state == RUNNING)
		{
			reader.goTo(context.programCounter)
			val ordinal = reader.opcode()
			val opcode = StyleRuleInstructionOpcode.all.getOrElse(
				ordinal
			) {
				throw IllegalStateException("invalid opcode: $ordinal")
			}
			context =
				opcode.execute(classifier, context, rule, reader, injector)
		}
		return context
	}

	/**
	 * The special end-of-sequence literal, represented by the
	 * [interned][String.intern] empty string.
	 */
	val endOfSequenceLiteral = "".intern()

	/**
	 * Execute one of the [MatchLiteralClassifierN] family of instructions.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param classifier
	 *   The style classifier.
	 * @param literal
	 *   The literal style classifier to match against [classifier].
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	internal fun executeMatchLiteralClassifier(
		context: StyleRuleContext,
		classifier: String,
		literal: String,
		programCounter: Int
	) = context
		.copy(
			programCounter = programCounter,
			state = when (literal === classifier)
			{
				true -> PAUSED
				false -> REJECTED
			})
		.normalized

	/**
	 * Execute one of the [MatchLiteralClassifierOrJumpN] family of
	 * instructions. Note that failure leaves the returned context in the
	 * [RUNNING]&nbsp;[state][StyleRuleContextState], which is imperative for
	 * correctly matching certain rules and inputs, specifically when the
	 * rejected classifier happens to coincide with the rule's starting
	 * classifier.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param classifier
	 *   The style classifier.
	 * @param literal
	 *   The literal style classifier to match against [classifier].
	 * @param jumpTarget
	 *   The program counter of the jump target. The jump occurs only if the
	 *   match fails.
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	internal fun executeMatchLiteralClassifierOrJump(
		context: StyleRuleContext,
		classifier: String,
		literal: String,
		jumpTarget: Int,
		programCounter: Int
	) =
		when (literal === classifier)
		{
			true -> context.copy(
				programCounter = programCounter,
				state = PAUSED
			).normalized
			false -> context.copy(
				programCounter = jumpTarget,
				state = when (jumpTarget)
				{
					context.programCounter -> PAUSED
					else -> RUNNING
				}
			).normalized
		}

	/**
	 * Execute one of the [ForkN] family of instructions.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param forkTarget
	 *   The zero-based nybble offset of the target
	 *   [instruction][StyleRuleInstruction] for resumption by the forked
	 *   [context][StyleRuleContext]. The offset is relative to the start of the
	 *   instruction stream.
	 * @param injector
	 *   How to inject a forked [context][StyleRuleContext] into the pool of
	 *   pending contexts.
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	internal fun executeFork(
		context : StyleRuleContext,
		forkTarget: Int,
		injector: (StyleRuleContext) -> Unit,
		programCounter: Int
	): StyleRuleContext
	{
		injector(context.copy(programCounter = forkTarget).normalized)
		return context.copy(programCounter = programCounter).normalized
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                 Rendering.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [RenderingContext] expresses how to render a complete set of character
 * attributes to a contiguous region of text within an arbitrary
 * [StyledDocument].
 *
 * @property attributes
 *   The partial [StyleAttributes]. Any missing aspects will be defaulted by
 *   Swing.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class RenderingContext constructor(val attributes: StyleAttributes)
{
	override fun equals(other: Any?): Boolean
	{
		// Note that this implementation suffices for all subclasses, since they
		// do not introduce additional state.
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as RenderingContext
		return attributes == other.attributes
	}

	override fun hashCode() = combine2(attributes.hashCode(), 0x56B40BC3)

	override fun toString() = attributes.toString()
		.replace("StyleAttributes(", "RenderingContext(")
		.replace(findNullField, "")
		.replace(findTrailingComma, "")

	companion object
	{
		/**
		 * A [regular&#32;expression][Regex] to find a `null`-valued field in
		 * the stringification of a [context][RenderingContext].
		 */
		private val findNullField by lazy(LazyThreadSafetyMode.PUBLICATION) {
			"\\b\\w+=null(?:, )?".toRegex()
		}

		/**
		 * A [regular&#32;expression][Regex] to find a final field with a
		 * trailing comma and space in the stringification of a
		 * [context][RenderingContext].
		 */
		private val findTrailingComma
		by lazy(LazyThreadSafetyMode.PUBLICATION) {
			", (?=\\))".toRegex()
		}
	}
}

/**
 * An [UnvalidatedRenderingContext] not has yet been [validated][validate].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an [UnvalidatedRenderingContext] from the specified
 * [StyleAttributes].
 *
 * @param attrs
 *   The complete [StyleAttributes].
 */
@Suppress("EqualsOrHashCode")
class UnvalidatedRenderingContext constructor(
	attrs: StyleAttributes
): RenderingContext(attrs)
{
	/**
	 * Validate the [receiver][UnvalidatedRenderingContext] against the supplied
	 * [palette][Palette]. If validation succeeds, then answer a
	 * [ValidatedRenderingContext] that includes all attributes of the receiver.
	 *
	 * @param palette
	 *   The [Palette], for interpreting the
	 *   [foreground][StyleAttributes.foreground] and
	 *   [background][StyleAttributes.background] colors for text rendition.
	 * @return
	 *   The [ValidatedRenderingContext].
	 * @throws RenderingContextValidationException
	 *   If the palette is missing any referenced colors.
	 */
	fun validate(palette: Palette): ValidatedRenderingContext
	{
		attributes.foreground?.let {
			palette.colors[it] ?: throw RenderingContextValidationException(
				"palette missing foreground color: $it")
		}
		attributes.background?.let {
			palette.colors[it] ?: throw RenderingContextValidationException(
				"palette missing background color: $it")
		}
		return ValidatedRenderingContext(attributes, palette)
	}

	override fun hashCode() = combine2(super.hashCode(), 0x25E819A5)
}

/**
 * A [ValidatedRenderingContext] has complete [StyleAttributes] and has been
 * successfully validated against the [palette][Palette] used to construct it.
 * It is therefore ready to [render][renderTo] itself onto [StyledDocument]s.
 *
 * @property palette
 *   The [Palette], for interpreting the
 *   [foreground][StyleAttributes.foreground] and
 *   [background][StyleAttributes.background] colors for text rendition.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [ValidatedRenderingContext] from the specified [StyleAttributes]
 * and [Palette]. The context remains valid so long as the system
 * [color&#32;mode][AvailWorkbench.darkMode] does not change.
 *
 * @param attrs
 *   The complete [StyleAttributes].
 * @param palette
 *   The [Palette], for interpreting the
 *   [foreground][StyleAttributes.foreground] and
 *   [background][StyleAttributes.background] colors for text rendition.
 */
@Suppress("EqualsOrHashCode")
class ValidatedRenderingContext constructor(
	attrs: StyleAttributes,
	private val palette: Palette
): RenderingContext(attrs)
{
	/**
	 * The [document&#32;attributes][AttributeSet] to use when compute a
	 * [style][Style] for a [StyledDocument]. Its attributes are sourced from
	 * [attributes].
	 */
	val documentAttributes: AttributeSet
	by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
		// Note: This was using getDefaultStyleContext().NamedStyle with nicely
		// named helpers before, but had to be rewritten to use
		// SimpleAttributeSet because NamedStyle was incapable of correctly
		// setting the background. This took about a person week to identify
		// and fix, so never use NamedStyle anywhere ever.
		SimpleAttributeSet().apply {
			attributes.run {
				fontFamily?.let { addAttribute(FontFamily, it) }
				foreground?.let {
					palette.colors[it]?.let { color ->
						addAttribute(Foreground, color)
					}
				}
				background?.let {
					palette.colors[it]?.let { color ->
						addAttribute(Background, color)
					}
				}
				bold?.let { addAttribute(Bold, it) }
				italic?.let { addAttribute(Italic, it) }
				underline?.let { addAttribute(Underline, it) }
				superscript?.let { addAttribute(Superscript, it) }
				subscript?.let { addAttribute(Subscript, it) }
				strikethrough?.let { addAttribute(StrikeThrough, it) }
			}
		}
	}

	/**
	 * A lazily computed [Pair] of HTML3.2 tag strings that can be written,
	 * before and after html text to make it look as close to the document style
	 * as possible.
	 */
	private val tagPairsForHtml: Pair<String, String> by lazy(
		LazyThreadSafetyMode.SYNCHRONIZED)
	{
		val tags = attributes.run {
			listOfNotNull(
				fontFamily?.let { "span style='font-family: $it'" },
				foreground?.let { fg ->
					palette.colors[fg]?.run { "font color=$hex'" }
				},
				background?.let { bg ->
					palette.colors[bg]?.run {
						"span style='background-color:$hex'"
					}
				},
				bold?.let { "b" },
				italic?.let { "i" },
				underline?.let { "u" },
				superscript?.let { "sup" },
				subscript?.let { "sub" },
				strikethrough?.let { "strike" }
			)
		}
		Pair(
			tags.joinToString("") { "<$it>" },
			tags.reversed().joinToString("") {
				"</${it.substringBefore(' ')}>"
			})
	}

	/**
	 * Write the open tags, run the action on the builder, then write the close
	 * tags.  Do not write the outer "html" open/close tags.  The resulting HTML
	 * should present a reasonable facsimile of the corresponding document
	 * styling.
	 *
	 * @param builder
	 *   Where to write the HTML.
	 * @param action
	 *   The function that produces the text inside the HTML tags.
	 */
	fun encloseHtml(builder: StringBuilder, action: StringBuilder.()->Unit)
	{
		val (before, after) = tagPairsForHtml
		builder.append(before)
		builder.action()
		builder.append(after)
	}

	/**
	 * Combine the [receiver][ValidatedRenderingContext] with the argument to
	 * produce a new [context][ValidatedRenderingContext] in which
	 * [attributes][StyleAttributes] of the receiver are overridden by
	 * corresponding non-`null` attributes of the argument.
	 *
	 * @param other
	 *   The overriding [context][ValidatedRenderingContext].
	 * @return
	 *   The combined [context][ValidatedRenderingContext].
	 */
	fun overrideWith(
		other: ValidatedRenderingContext
	): ValidatedRenderingContext
	{
		assert(palette === other.palette)
		val a = attributes
		val o = other.attributes
		return ValidatedRenderingContext(
			StyleAttributes(
				fontFamily = o.fontFamily ?: a.fontFamily,
				foreground = o.foreground ?: a.foreground,
				background = o.background ?: a.background,
				bold = o.bold ?: a.bold,
				italic = o.italic ?: a.italic,
				underline = o.underline ?: a.underline,
				superscript = o.superscript ?: a.superscript,
				subscript = o.subscript ?: a.subscript,
				strikethrough = o.strikethrough ?: a.strikethrough
			),
			palette
		)
	}

	/**
	 * Apply the [receiver][RenderingContext] to the specified [range] of the
	 * target [StyledDocument].
	 *
	 * @param document
	 *   The target [StyledDocument].
	 * @param classifiers
	 *   The classifier sequence associated with the [range]. A
	 *   [NameAttribute][StyleConstants.NameAttribute] will be attached to the
	 *   [range], to support the style classifier introspection feature.
	 * @param start
	 *   The zero-based start of the characters to style in the UTF-16 [String].
	 * @param pastEnd
	 *   The zero-based pastEnd of the characters to style in the UTF-16
	 *   [String].
	 * @param replace
	 *   Indicates whether or not the previous attributes should be cleared
	 *   before the new attributes are set. If `true`, the operation will
	 *   replace the previous attributes entirely. If `false`, the new
	 *   attributes will be merged with the previous attributes.
	 */
	fun renderTo(
		document: StyledDocument,
		classifiers: String,
		start: Int,
		pastEnd: Int,
		replace: Boolean = true
	)
	{
		assert(SwingUtilities.isEventDispatchThread())
		// This line is subtle, because it binds `classifiers` to a
		// NameAttribute, which in turn makes the classifiers available for
		// display in gutter of the AvailEditor.
		val style = document.addStyle(classifiers, defaultDocumentStyle)
		style.addAttributes(documentAttributes)
		document.setCharacterAttributes(
			start,
			pastEnd - start,
			style,
			replace)
	}

	override fun hashCode() = combine2(super.hashCode(), 0x41AC8F15)

	companion object
	{
		/**
		 * The default [document&#32;style][Style], to serve as the parent for
		 * new document styles. **Must only be called on the Swing UI thread.**
		 */
		val defaultDocumentStyle: Style by lazy(
			LazyThreadSafetyMode.SYNCHRONIZED)
		{
			assert(SwingUtilities.isEventDispatchThread())
			getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
		}
	}
}

/**
 * The appropriate colors to select from the [receiver][Palette], depending on
 * whether the application is using [dark&#32;mode][AvailWorkbench.darkMode].
 */
val Palette.colors get() =
	if (AvailWorkbench.darkMode) darkColors else lightColors

/**
 * Raised when [rendering&#32;context][RenderingContext]
 * [validation][UnvalidatedRenderingContext.validate] fails for any reason.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class RenderingContextValidationException(message: String): Exception(message)

/**
 * A class that maintains the position of the declaration of a local variable
 * and its uses within the same [StyledDocument].  Within the document, this
 * value is stored as an invisible style under the key
 * [LocalDefinitionAttributeKey] for the span corresponding to the definition,
 * and the same object is stored under [LocalUseAttributeKey] for the spans that
 * are uses of the local.
 */
data class DefinitionAndUsesInDocument constructor(
	val definitionSpanInDocument: Pair<Position, Position>,
	val useSpansInDocument: List<Pair<Position, Position>>)

/**
 * A function that applies some style to the given range of the given
 * [StyledDocument].  The range is expressed as the zero-based start in the
 * UTF-16 [String], and the zero-based just-past-end in the same string.
 */
typealias RenderingFunction = (StyledDocument, Int, Int)->Unit

/**
 * Given a receiver and argument that are optional [RenderingFunction]s, but not
 * both null, produce a corresponding [RenderingFunction] that executes the
 * non-null values, in arbitrary order if both are present.
 *
 * @receiver
 *   An optional [RenderingFunction].
 * @param otherFunction
 *   Another optional [RenderingFunction].
 * @return
 *   The resulting [RenderingFunction] that evaluates both the receiver and the
 *   parameter, omitting nulls.
 */
infix fun RenderingFunction?.compose(
	otherFunction: RenderingFunction?
): RenderingFunction = when
{
	this == null -> otherFunction!!
	otherFunction == null -> this
	else -> { document, start, pastEnd ->
		this(document, start, pastEnd)
		otherFunction(document, start, pastEnd)
	}
}


/**
 * The [RenderingEngine] renders [runs][StyleRun] of source text. To achieve
 * this for some run `R`, it queries the active [stylesheet][Stylesheet] with
 * the run's style classifiers and then
 * [uses][ValidatedRenderingContext.renderTo] the obtained
 * [rendering&#32;context][RenderingContext] to set the
 * [character&#32;attributes][StyledDocument.setCharacterAttributes] of `R`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object RenderingEngine
{
	/**
	 * Apply all style runs from the [StylingRecord], and all [PhraseNode]
	 * information from the [PhrasePathRecord], to the
	 * given [StyledDocument], using the supplied [stylesheet] to obtain an
	 * appropriate [rendering&#32;context][ValidatedRenderingContext] for each
	 * style run.
	 *
	 * It always starts by resetting the entire document to the default style.
	 *
	 * **Must be invoked on the Swing UI thread.**
	 *
	 * @param document
	 *   The [StyledDocument] to update.
	 * @param stylesheet
	 *   The [stylesheet][Stylesheet].
	 * @param styleRecord
	 *   The [StylingRecord] containing runs to apply to the document.
	 * @param phrasePathRecord
	 *   The [PhrasePathRecord] containing [PhraseNode] runs to apply to the
	 *   document.
	 */
	fun applyStylesAndPhrasePaths(
		document: StyledDocument,
		stylesheet: Stylesheet,
		styleRecord: StylingRecord?,
		phrasePathRecord: PhrasePathRecord?)
	{
		assert(SwingUtilities.isEventDispatchThread())
		// First remove all existing styling information.
		document.setCharacterAttributes(
			0,
			document.length,
			document.getStyle(StyleContext.DEFAULT_STYLE),
			true)
		// Collect functions that apply a style to a region of the document.
		// Use RunTree's ability to split overlapping ranges to avoid Swing's
		// buggy implementation of overlapping style ranges.
		val renderingFunctions = RunTree<RenderingFunction>()
		// Start with the style classifiers.
		styleRecord?.styleRuns?.forEach { (range, classifiers) ->
			renderingFunctions.edit(range.first, range.last) { _ ->
				// Incoming value here is always null.
				{ document, start, pastEnd ->
					stylesheet[classifiers].renderTo(
						document, classifiers, start, pastEnd, false)
				}
			}
		}
		// Add in the invisible phrase/token structure information.
		phrasePathRecord?.phraseNodesDo { phraseNode ->
			phraseNode.tokenSpans.forEach { (start, pastEnd, _, indexInName) ->
				renderingFunctions.edit(start, pastEnd) { old ->
					old compose { document, start, pastEnd ->
						document.setCharacterAttributes(
							start,
							pastEnd - start,
							SimpleAttributeSet().apply {
								addAttribute(
									PhraseNodeAttributeKey,
									TokenStyle(phraseNode, indexInName))
							},
							false)
					}
				}
			}
		}
		// Add the information about local declarations and uses.
		styleRecord?.declarationsWithUses()?.forEach { (definition, uses) ->
			val definitionSpan = Pair(
				document.createPosition(definition.first),
				document.createPosition(definition.last))
			val useSpans = uses.map {
				Pair(
					document.createPosition(it.first),
					document.createPosition(it.last))
			}
			val entry = DefinitionAndUsesInDocument(definitionSpan, useSpans)
			// Add the definition's style.
			renderingFunctions.edit(definition.first, definition.last)
			{ old ->
				old compose { document, start, pastEnd ->
					document.setCharacterAttributes(
						start,
						pastEnd - start,
						SimpleAttributeSet().apply {
							addAttribute(
								LocalDefinitionAttributeKey,
								entry)
						},
						false)
				}
			}
			// Add a style for each use.
			uses.forEach { useRange ->
				renderingFunctions.edit(useRange.first, useRange.last)
				{ old ->
					old compose { document, start, pastEnd ->
						document.setCharacterAttributes(
							start,
							pastEnd - start,
							SimpleAttributeSet().apply {
								addAttribute(
									LocalUseAttributeKey,
									entry)
							},
							false)
					}
				}
			}
		}
		// Now that all the ranges have been split appropriately to avoid any
		// overlapping ranges (i.e., start A, start B, end A, end B), tell the
		// document itself about them.
		renderingFunctions.forEach { (start, pastEnd, renderingFunction) ->
			renderingFunction(
				document,
				start.toInt(),
				pastEnd.toInt())
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                             HTML abstraction.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * Copy the specified range from the [receiver][StyledDocument] as a fragment of
 * HTML5 text. Embed the style attributes directly into the HTML, using `style`
 * rather than `class` attributes to keep the fragment portable and
 * document-independent.
 *
 * The primary use case for this method is embedding authentic styled Avail code
 * into larger HTML5 documents, e.g., the official Avail documentation,
 * promotional material for Avail, blog posts, etc.
 *
 * @receiver
 *   The [StyledDocument] from which the desired HTML5 should be abstracted.
 * @param start
 *   The start of the range to abstract.
 * @param pastEnd
 *   The position just past the end of the range to abstract.
 * @param codeBackground
 *   The background color to use for the whole fragment.
 * @return
 *   The requested HTML5 fragment.
 */
fun StyledDocument.copyAsHtml5(
	start: Int,
	pastEnd: Int,
	codeBackground: Color = SystemColors.active.codeBackground
) = buildString {
	appendLine("<html>")
	appendLine("""<body style="background-color: ${codeBackground.hex}">""")
	appendLine("""<div style="white-space: pre;">""")
	var previous = styleAttributesAt(start)
	var current = previous
	append(previous.startSpanHtml5())
	append(getText(start, 1))
	for (position in start + 1 until pastEnd)
	{
		current = styleAttributesAt(position)
		if (current.appendSpanTransitionIfChangedFrom(previous, this))
		{
			previous = current
		}
		append(getText(position, 1))
	}
	//
	if (current.flags != 0) appendLine("</span>")
	appendLine("</div>\n</body>\n</html>")
}

/**
 * Extract the [StyleAttributes] at the specified position in the
 * [receiver][StyledDocument].
 *
 * @receiver
 *   The [StyledDocument] to interrogate.
 * @param position
 *   The position at which to extract the [StyleAttributes].
 * @return
 *   The extracted [StyleAttributes].
 */
private fun StyledDocument.styleAttributesAt(position: Int) =
	getCharacterElement(position).attributes.run {
		StyleAttributes(
			foreground = (getAttribute(Foreground) as? Color)?.hex,
			background = (getAttribute(Background) as? Color)?.hex,
			fontFamily = getAttribute(FontFamily) as? String,
			bold = getAttribute(Bold) as? Boolean,
			italic = getAttribute(Italic) as? Boolean,
			underline = getAttribute(Underline) as? Boolean,
			superscript = getAttribute(Superscript) as? Boolean,
			subscript = getAttribute(Subscript) as? Boolean,
			strikethrough = getAttribute(StrikeThrough) as? Boolean
		)
	}

/**
 * Answer the start of an HTML5 span with the specified [StyleAttributes]. The
 * span will be completed self-contained, using `style` rather than `class`
 * attributes to keep the fragment portable and document-independent.
 */
private fun StyleAttributes.startSpanHtml5() = buildString {
	val style = buildString {
		foreground?.let { append("color: $it; ") }
		background?.let { append("background-color: $it; ") }
		fontFamily?.let {
			// Translate Java's special monospaced font constant for CSS.
			val font = if (it == "Monospaced") "monospace" else it
			append("font-family: $font; ")
		}
		if (bold == true) append("font-weight: bold; ")
		if (italic == true) append("font-style: italic; ")
		if (underline == true) append("text-decoration: underline; ")
		if (superscript == true) append("vertical-align: super; ")
		if (subscript == true) append("vertical-align: sub; ")
		if (strikethrough == true) append("text-decoration: line-through;")
	}.trim()
	if (style.isNotEmpty())
	{
		append("""<span style="""")
		append(style)
		append("""">""")
	}
}

/**
 * The flags that indicate which [StyleAttributes] are present.
 */
private val StyleAttributes.flags get() = run {
	var flags = 0
	foreground?.let { flags = flags or StyleAttributeFlags.foreground }
	background?.let { flags = flags or StyleAttributeFlags.background }
	fontFamily?.let { flags = flags or StyleAttributeFlags.fontFamily }
	if (bold == true) flags = flags or StyleAttributeFlags.bold
	if (italic == true) flags = flags or StyleAttributeFlags.italic
	if (underline == true) flags = flags or StyleAttributeFlags.underline
	if (superscript == true) flags = flags or StyleAttributeFlags.superscript
	if (subscript == true) flags = flags or StyleAttributeFlags.subscript
	if (strikethrough == true) flags = flags or StyleAttributeFlags.strikethrough
	flags
}

/**
 * Append the appropriate HTML5 close tag to the [receiver][StringBuilder] if
 * the [StyleAttributes] differ from the specified [startAttribute].
 *
 * @param startAttribute
 *   The [StyleAttributes] at the start of the span.
 * @param builder
 *   The target [StringBuilder] to which to append the close tag.
 * @return
 *   Whether the close tag was appended.
 */
private fun StyleAttributes.appendSpanTransitionIfChangedFrom(
	startAttribute: StyleAttributes,
	builder: StringBuilder
): Boolean
{
	if (this != startAttribute)
	{
		// Be careful not to close the span if we're not actually inside a span.
		if (flags != 0) builder.append("</span>")
		startSpanHtml5().let { if (it.isNotEmpty()) builder.append(it) }
		return true
	}
	return false
}

/**
 * Flags that indicate whether a [StyleAttributes] possesses particular aspects.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
private object StyleAttributeFlags
{
	/** Has [foreground][StyleAttributes.foreground] color. */
	val foreground = 0x00000001

	/** Has [background][StyleAttributes.background] color. */
	val background = 0x00000002

	/** Has [font&#32;family][StyleAttributes.fontFamily]. */
	val fontFamily = 0x00000004

	/** Has [bold][StyleAttributes.bold] font weight. */
	val bold = 0x00000008

	/** Has [italic][StyleAttributes.italic] font style. */
	val italic = 0x00000010

	/** Has [underline][StyleAttributes.underline] text decoration. */
	val underline = 0x00000020

	/** Has [superscript][StyleAttributes.superscript] vertical alignment. */
	val superscript = 0x00000040

	/** Has [subscript][StyleAttributes.subscript] vertical alignment. */
	val subscript = 0x00000080

	/** Has [strikethrough][StyleAttributes.strikethrough] text decoration. */
	val strikethrough = 0x00000100
}

////////////////////////////////////////////////////////////////////////////////
//                                  Coding.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver. The coding is not efficient for large
 * [instruction&#32;sets][StyleRuleInstruction], but is quite efficient for a
 * very small instruction set, i.e., fewer than 32 instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode. Must be ≥0.
 */
fun NybbleOutputStream.opcode(value: Int)
{
	assert(value >= 0)
	// This encoding is not efficient at all if the instruction set ever
	// grows large, but is quite efficient for small instruction sets.
	var residue = value
	while (residue >= 15)
	{
		write(15)
		residue -= 15
	}
	write(residue)
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`opcode`][NybbleOutputStream.opcode] to decode a nonnegative integer from
 * the receiver. If the stored encoding does not denote a valid value, the
 * result is undefined, and the number of bytes consumed is also undefined.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 */
fun NybbleInputStream.opcode(): Int
{
	var value = 0
	while (true)
	{
		val nybble = read()
		if (nybble == 15)
		{
			value += 15
		}
		else
		{
			value += nybble
			return value
		}
	}
}

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using a nybble-based variant of MIDI VLQ. The
 * value must be non-negative.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode. Must be ≥ 0.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
fun NybbleOutputStream.vlq(value: Int)
{
	assert (value >= 0)
	var residue = value
	while (residue >= 8)
	{
		val nybble = (residue and 0x07) or 0x08
		write(nybble)
		residue = residue ushr 3
	}
	write(residue)
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][NybbleOutputStream.vlq] to decode a nonnegative integer from the
 * receiver. If the stored encoding does not denote a valid value, the result is
 * undefined, and the number of bytes consumed is also undefined.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
fun NybbleInputStream.unvlq(): Int
{
	var n = 0
	var k = 0
	while (true)
	{
		val nybble = read()
		when
		{
			nybble and 0x08 == 0x08 ->
			{
				n = n or ((nybble and 0x07) shl k)
				k += 3
			}
			else ->
			{
				// The MSB is clear, so we're done decoding.
				return n or (nybble shl k)
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                               Constants.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * [SystemStyleClassifier] enumerates the style classifiers that are well-known
 * to Anvil.
 *
 * @property classifier
 *   The style classifier.
 */
enum class SystemStyleClassifier(val classifier: String)
{
	/** The background color for the input field of an active console. */
	INPUT_BACKGROUND("#input-background")
	{
		override val systemColor = SystemColors::inputBackground
		override val colorName = systemColor.name

		override val defaultRenderingContext get() =
			UnvalidatedRenderingContext(
				StyleAttributes(background = colorName)
			)
	},

	/** The foreground color for the input field of an active console. */
	INPUT_TEXT("#input-text")
	{
		override val systemColor = SystemColors::inputText
		override val colorName = systemColor.name
	},

	/** The background color for source text. */
	CODE_BACKGROUND("#code-background")
	{
		override val systemColor = SystemColors::codeBackground
		override val colorName = systemColor.name

		override val defaultRenderingContext get() =
			UnvalidatedRenderingContext(
				StyleAttributes(background = colorName)
			)
	},

	/** The foreground color for source text. */
	CODE_TEXT("#code-text")
	{
		override val systemColor = SystemColors::codeText
		override val colorName = systemColor.name
	},

	/** The color of a [code&#32;guide][CodeOverlay]. */
	CODE_GUIDE("#code-guide")
	{
		override val systemColor = SystemColors::codeGuide
		override val colorName = systemColor.name
	},

	/** The [stream&#32;style][StreamStyle] used to echo user input. */
	STREAM_INPUT("#stream-input")
	{
		override val systemColor = SystemColors::streamInput
		override val colorName = systemColor.name
	},

	/** The [stream&#32;style][StreamStyle] used to display normal output. */
	STREAM_OUTPUT("#stream-output")
	{
		override val systemColor = SystemColors::streamOutput
		override val colorName = systemColor.name
	},

	/** The [stream&#32;style][StreamStyle] used to display error output. */
	STREAM_ERROR("#stream-error")
	{
		override val systemColor = SystemColors::streamError
		override val colorName = systemColor.name
	},

	/**
	 * The [stream&#32;style][StreamStyle] used to display informational text.
	 */
	STREAM_INFO("#stream-info")
	{
		override val systemColor = SystemColors::streamInfo
		override val colorName = systemColor.name
	},

	/** The [stream&#32;style][StreamStyle] used to echo commands. */
	STREAM_COMMAND("#stream-command")
	{
		override val systemColor = SystemColors::streamCommand
		override val colorName = systemColor.name
	},

	/**
	 * The [stream&#32;style][StreamStyle] used to provide build progress
	 * updates.
	 */
	STREAM_BUILD_PROGRESS("#stream-build-progress")
	{
		override val systemColor = SystemColors::streamBuildProgress
		override val colorName = systemColor.name
	},

	/**
	 * The [stream&#32;style][StreamStyle] used to display diagnostics and
	 * reports.
	 */
	STREAM_REPORT("#stream-report")
	{
		override val systemColor = SystemColors::streamReport
		override val colorName = systemColor.name
	},

	/**
	 * The style used to emphasize a particular token that has been selected or
	 * used in a navigation.  Styler functions for actual code should not
	 * generally use this style, as it's intended for transiently emphasizing a
	 * particular token that the user is working with dynamically.
	 */
	TOKEN_HIGHLIGHT("#token-highlight")
	{
		override val systemColor = SystemColors::tokenHighlightBackground
		override val colorName = systemColor.name

		override val defaultRenderingContext get() =
			UnvalidatedRenderingContext(
				StyleAttributes(background = colorName)
			)
	};

	/**
	 * The source text for an exact match pattern for the
	 * [receiver][SystemStyleClassifier].
	 */
	val exactMatchSource get() = "${ExactMatchToken.lexeme}$classifier"

	/**
	 * The method reference to the desired [system&#32;color][SystemColors].
	 */
	protected abstract val systemColor: SystemColors.()->Color

	/** The name of the default [system&#32;color][SystemColors]. */
	protected abstract val colorName: String

	/**
	 * The default [palette][Palette] to use if the [palette][Palette] provided
	 * to the [stylesheet][Stylesheet] does not have the right aliases defined.
	 */
	val palette get() = Palette(
		lightColors = mapOf(colorName to LightColors.systemColor()),
		darkColors = mapOf(colorName to DarkColors.systemColor())
	)

	/**
	 * The default [rendering&#32;context][RenderingContext] to use when none
	 * was supplied for an exact match of the [receiver][SystemStyleClassifier]
	 * in the [stylesheet][Stylesheet].
	 */
	open val defaultRenderingContext get() = UnvalidatedRenderingContext(
		StyleAttributes(foreground = colorName)
	)
}

////////////////////////////////////////////////////////////////////////////////
//                                Phrases.                                    //
////////////////////////////////////////////////////////////////////////////////

/**
 * Utility for applying [PhrasePathRecord]'s information to token ranges in a
 * [StyledDocument].
 */
object PhrasePathStyleApplicator
{
	/**
	 * A [TokenStyle] contains information about which [PhraseNode] is
	 * applicable for a span of source having this invisible style (under the
	 * [PhraseNodeAttributeKey]), as well as which of the phrase's atom's
	 * [MessageSplitter] parts occurred in this span of the source.
	 *
	 * @property phraseNode
	 *   The [PhraseNode] that this invisible style represents.
	 * @property tokenIndexInName
	 *   The one-based index of a message part within the split message name
	 *   being sent.  When this style is applied to a span of source code, this
	 *   field indicates the corresponding static token of the message name.
	 */
	data class TokenStyle(
		val phraseNode: PhraseNode,
		val tokenIndexInName: Int
	)

	/**
	 * An object to use as a key in an [AttributeSet], where the value is a
	 * [PhraseNode].  This is applied to the [StyledDocument] for the span of
	 * each token that is part of that [PhraseNode].
	 */
	object PhraseNodeAttributeKey

	/**
	 * An object to use as a key in an [AttributeSet], where the value is a
	 * [DefinitionAndUsesInDocument].  This is applied to the [StyledDocument]
	 * for the span of a token that is a declaration of some local.  The
	 * [DefinitionAndUsesInDocument] contains navigation information for both
	 * this definition and all uses.
	 */
	object LocalDefinitionAttributeKey

	/**
	 * An object to use as a key in an [AttributeSet], where the value is a
	 * [DefinitionAndUsesInDocument].  This is applied to the [StyledDocument]
	 * for the span of a token that is a use of some local.  The
	 * [DefinitionAndUsesInDocument] contains navigation information for both
	 * the definition and all such uses.
	 */
	object LocalUseAttributeKey
}
