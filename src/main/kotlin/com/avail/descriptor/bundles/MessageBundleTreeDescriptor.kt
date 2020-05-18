/*
 * MessageBundleTreeDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.avail.descriptor.bundles

import com.avail.AvailRuntimeSupport
import com.avail.annotations.AvailMethod
import com.avail.annotations.HideFieldInDebugger
import com.avail.compiler.ParsingOperation
import com.avail.compiler.ParsingOperation.*
import com.avail.compiler.ParsingOperation.Companion.decode
import com.avail.compiler.splitter.MessageSplitter
import com.avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.Descriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.bundles.A_Bundle.Companion.grammaticalRestrictions
import com.avail.descriptor.bundles.A_Bundle.Companion.messageParts
import com.avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import com.avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
import com.avail.descriptor.bundles.A_BundleTree.Companion.latestBackwardJump
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.IntegerSlots.Companion.HAS_BACKWARD_JUMP_INSTRUCTION
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.IntegerSlots.Companion.IS_SOURCE_OF_CYCLE
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.ObjectSlots.*
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.bundle
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.isBackwardJump
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.nameHighlightingPc
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPc
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPlan
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo
import com.avail.descriptor.representation.*
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PhraseTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.dispatch.LookupTree
import com.avail.dispatch.LookupTreeAdaptor
import com.avail.dispatch.TypeComparison.Companion.compareForParsing
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport
import com.avail.utility.Mutable
import com.avail.utility.Pair
import com.avail.utility.Strings.newlineTab
import java.util.*
import java.util.Collections.sort
import java.util.function.BiConsumer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * A [message&#32; bundle&#32; tree][MessageBundleTreeDescriptor] is used by the
 * Avail parser.  Since the Avail syntax is so flexible, we make up for that
 * simplicity with a complementary complexity in the parsing mechanism.  A
 * message bundle tree is used to keep track of how far along the parser has
 * gotten in the parsing of a method invocation.  More powerfully, it does this
 * for multiple methods simultaneously, at least up to the point that the method
 * names diverge.
 *
 * For example, assume the methods "_foo_bar" and "_foo_baz" are both visible in
 * the current module.  After parsing an argument, the "foo" keyword, and
 * another argument, the next thing to look for is either the "bar" keyword or
 * the "baz" keyword.  Depending which keyword comes next, we will have parsed
 * an invocation of either the first or the second method.  Both possibilities
 * have been parsed together (i.e., only once) up to this point, and the next
 * keyword encountered decides which (if either) method call is being invoked.
 *
 * [MessageSplitter] is used to generate a sequence of parsing instructions for
 * a method name.  These parsing instructions determine how long multiple
 * potential method invocations can be parsed together and when they must
 * diverge.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MessageBundleTreeDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.BUNDLE_TREE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * [BitField]s for the hash and the parsing pc.  See below.
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * The hash, or zero (`0`) if the hash has not yet been computed.
			 */
			@HideFieldInDebugger
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			/**
			 * This flag is set when this bundle tree contains at least one
			 * parsing-plan-in-progress at a [ParsingOperation.JUMP_BACKWARD]
			 * instruction.
			 */
			@JvmField
			val HAS_BACKWARD_JUMP_INSTRUCTION = BitField(HASH_AND_MORE, 32, 1)

			/**
			 * This flag is set when a bundle tree is redirected to an
			 * equivalent ancestor bundle tree.  The current bundle tree's
			 * [LATEST_BACKWARD_JUMP] is set directly to the target of the cycle
			 * when this flag is set.
			 */
			@JvmField
			val IS_SOURCE_OF_CYCLE = BitField(HASH_AND_MORE, 33, 1)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * A [map][MapDescriptor] from [A_Bundle]s to maps, which are themselves
		 * from [definitions][A_Definition] to [sets][A_Set] of
		 * [plans-in-progress][A_ParsingPlanInProgress] for that
		 * definition/bundle.  Note that the inner maps may be empty in the case
		 * that a grammatical restriction has been defined before any visible
		 * definitions.  This doesn't affect parsing, but makes the logic easier
		 * about deciding which grammatical restrictions are visible when adding
		 * a definition later.
		 */
		ALL_PLANS_IN_PROGRESS,

		/**
		 * A [map][MapDescriptor] from visible [bundles][A_Bundle] to maps from
		 * [definitions][A_Definition] to the [A_Set]s of
		 * [plans-in-progress][A_ParsingPlanInProgress] for that
		 * definition/bundle.  It has the same content as
		 * [ALL_PLANS_IN_PROGRESS] until these items have been categorized as
		 * complete, incomplete, action, or prefilter. They are categorized if
		 * and when this message bundle tree is reached during parsing.
		 */
		UNCLASSIFIED,

		/**
		 * A [set][A_Set] of [bundles][A_Bundle] that indicate which methods
		 * have just had a complete invocation parsed at this point in the tree.
		 */
		LAZY_COMPLETE,

		/**
		 * A [map][MapDescriptor] from [string][StringDescriptor] to successor
		 * [message][MessageBundleTreeDescriptor]. During parsing, if the next
		 * token is a key of this map then consume that token, look it up in
		 * this map, and continue parsing with the corresponding message bundle
		 * tree. Otherwise record a suitable parsing failure message for this
		 * position in the source stream, in case this ends up being the
		 * rightmost parse position to be reached.
		 *
		 * [Message&#32; bundles][MessageBundleDescriptor] only get added to
		 * this map if their current instruction is the
		 * [ParsingOperation.PARSE_PART] instruction. There may be other
		 * instructions current for other message bundles, but they will be
		 * represented in the [LAZY_ACTIONS] map, or the [LAZY_PREFILTER_MAP] if
		 * their instruction is a [ParsingOperation.CHECK_ARGUMENT].
		 */
		LAZY_INCOMPLETE,

		/**
		 * A [map][MapDescriptor] from lower-case [strings][A_String] to
		 * successor [message&#32; bundle&#32;
		 * trees][MessageBundleTreeDescriptor]. During parsing, if the next
		 * token, following conversion to lower case, is a key of this map then
		 * consume that token, look it up in this map, and continue parsing with
		 * the corresponding message bundle tree. Otherwise record a suitable
		 * parsing failure message for this position in the source stream, in
		 * case this ends up being the rightmost parse position to be reached.
		 *
		 * [Message&#32; bundles][MessageBundleDescriptor] only get added to
		 * this map if their current instruction is the
		 * [ParsingOperation.PARSE_PART_CASE_INSENSITIVELY] instruction. There
		 * may be other instructions current for other message bundles, but they
		 * will be represented in the [LAZY_ACTIONS] map, or the
		 * [LAZY_PREFILTER_MAP] if their instruction is a
		 * [ParsingOperation.CHECK_ARGUMENT].
		 */
		LAZY_INCOMPLETE_CASE_INSENSITIVE,

		/**
		 * This is a map from an encoded [ParsingOperation] (an [Integer]) to a
		 * [A_Tuple] of [A_BundleTree]s to attempt if the instruction succeeds.
		 *
		 * Note that the [ParsingOperation#PARSE_PART] and
		 * [ParsingOperation#PARSE_PART_CASE_INSENSITIVELY] instructions are
		 * treated specially, as only one keyword can be next in the source
		 * stream (so there's no value in checking whether it's an X, whether
		 * it's a Y, whether it's a Z, etc. Instead, the [LAZY_INCOMPLETE] and
		 * [LAZY_INCOMPLETE_CASE_INSENSITIVE]&#32;[map][A_Map] takes care of
		 * dealing with this efficiently with a single lookup.
		 *
		 * Similarly, the [ParsingOperation#CHECK_ARGUMENT] instruction is
		 * treated specially. When it is encountered and the argument that was
		 * just parsed is a send phrase, that send phrase is looked up in the
		 * [LAZY_PREFILTER_MAP], yielding the next message bundle tree. If it's
		 * not present as a key (or the argument isn't a send), then the
		 * instruction is looked up normally in the lazy actions map.
		 */
		LAZY_ACTIONS,

		/**
		 * If we wait until all tokens and arguments of a potential method send
		 * have been parsed before checking that all the arguments have the
		 * right types and precedence then we may spend a *lot* of extra effort
		 * parsing unnecessary expressions. For example, the "_×_" operation
		 * might not allow a "_+_" call for its left or right arguments, so
		 * parsing "1+2×…" as "(1+2)×…" is wasted effort.
		 *
		 * This is especially expensive for operations with many arguments that
		 * could otherwise be culled by the shapes and types of early arguments,
		 * such as for "«_‡++»", which forbids arguments being invocations of
		 * the same message, keeping a call with many arguments flat. I haven't
		 * worked out the complete recurrence relations for this, but it's
		 * probably exponential (it certainly grows *much* faster than linearly
		 * without this optimization).
		 *
		 * To accomplish this culling we have to filter out any inconsistent
		 * [message&#32; bundles][MessageBundleDescriptor] as we parse. Since we
		 * already do this in general while parsing expressions, all that
		 * remains is to check right after an argument has been parsed (or
		 * replayed due to memoization). The check for now is simple and doesn't
		 * consider argument types, simply excluding methods based on the
		 * grammatical restrictions.
		 *
		 * When a message bundle's next instruction is
		 * [ParsingOperation.CHECK_ARGUMENT] (which must be all or nothing
		 * within a [message&#32; bundle&#32;
		 * tree][MessageBundleTreeDescriptor]), this lazy prefilter map is
		 * populated. It maps from interesting [message&#32;
		 * bundles][MessageBundleDescriptor] that might occur as an argument to
		 * an appropriately reduced message bundle tree (i.e., a message bundle
		 * tree containing precisely those method bundles that allow that
		 * argument. The only keys that occur are ones for which at least one
		 * restriction exists in at least one of the still possible
		 * [message&#32; bundles][MessageBundleDescriptor]. When [UNCLASSIFIED]
		 * is empty, *all* such restricted argument message bundles occur in
		 * this map. Note that some of the resulting message bundle trees may be
		 * completely empty. Also note that some of the trees may be shared, so
		 * be careful to discard them rather than maintaining them when new
		 * method bundles or grammatical restrictions are added.
		 *
		 * When an argument is a message that is not restricted for any of the
		 * message bundles in this message bundle tree (i.e., it does not occur
		 * as a key in this map), then the sole entry in [LAZY_INCOMPLETE] is
		 * used. The key is always the `checkArgument` instruction that all
		 * message bundles in this message bundle tree must have.
		 */
		LAZY_PREFILTER_MAP,

		/**
		 * A [tuple][A_Tuple] of pairs (2-tuples) where the first element is a
		 * [phrase&#32; type][PhraseTypeDescriptor] and the second element is an
		 * [A_ParsingPlanInProgress]. These should stay synchronized with the
		 * [LAZY_TYPE_FILTER_TREE_POJO] field.
		 */
		LAZY_TYPE_FILTER_PAIRS_TUPLE,

		/**
		 * A [raw&#32; pojo][RawPojoDescriptor] containing a [type-testing&#32;
		 * tree][LookupTree] for handling the case that at least one
		 * [A_ParsingPlanInProgress] in this message bundle tree is at a
		 * [parsingPc][A_ParsingPlanInProgress.parsingPc] pointing to a
		 * [ParsingOperation.TYPE_CHECK_ARGUMENT] operation. This allows
		 * relatively efficient elimination of inappropriately typed arguments.
		 *
		 * Since navigating this tree while performing the indicated type tests
		 * is slower than other parsing operations, the [MessageSplitter]
		 * produces an [A_DefinitionParsingPlan] that usually postpones the type
		 * check of the latest parsed argument until after any successive fixed
		 * tokens have been consumed.
		 */
		LAZY_TYPE_FILTER_TREE_POJO,

		/**
		 * This is the most recently encountered backward jump in the ancestry
		 * of this bundle tree, or nil if none were encountered in the ancestry.
		 * There could be multiple competing parsing plans in that target bundle
		 * tree, some of which had a backward jump and some of which didn't, but
		 * we only require that at least one had a backward jump.
		 *
		 * Since every loop has a backward jump, and since the target has a
		 * pointer to its own preceding backward jump, we can trace this back
		 * through every backward jump in the ancestry (some of which will
		 * contain the same parsing-plans-in-progress).  When we expand a node
		 * that's a backward jump, we chase these pointers to determine if
		 * there's an equivalent node in the ancestry – one with the same set of
		 * parsing-plans-in-progress.  If so, we use its expansion rather than
		 * creating yet another duplicate copy.  This saves space and time in
		 * the case that there are repeated arguments to a method, and at least
		 * one encountered invocation of that method has a large number of
		 * repetitions.  An example is the literal set notation "{«_‡,»}", which
		 * can be used to specify a set with thousands of elements.  Without
		 * this optimization, that would be tens of thousands of additional
		 * bundle trees to maintain.
		 */
		LATEST_BACKWARD_JUMP
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === IntegerSlots.HASH_AND_MORE
		|| e === ALL_PLANS_IN_PROGRESS
		|| e === UNCLASSIFIED
		|| e === LAZY_COMPLETE
		|| e === LAZY_INCOMPLETE
		|| e === LAZY_INCOMPLETE_CASE_INSENSITIVE
		|| e === LAZY_ACTIONS
		|| e === LAZY_PREFILTER_MAP
		|| e === LAZY_TYPE_FILTER_PAIRS_TUPLE
		|| e === LAZY_TYPE_FILTER_TREE_POJO
		|| e === LATEST_BACKWARD_JUMP

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	): Unit = with(builder) {
		append("BundleTree(")
		val allPlansInProgress: A_Map = self.slot(ALL_PLANS_IN_PROGRESS)
		val bundleCount = allPlansInProgress.mapSize()
		if (bundleCount <= 15) {
			val strings: MutableMap<String, Int> = HashMap(bundleCount)
			allPlansInProgress.forEach(BiConsumer { _, value: A_Map ->
				value.forEach(BiConsumer { _, plansInProgress: A_Set ->
					plansInProgress.forEach { planInProgress ->
						val string = planInProgress.nameHighlightingPc()
						strings[string] = strings.getOrDefault(string, 0) + 1
					}
				})
			})
			val sorted: MutableList<String> = ArrayList()
			for ((key, count) in strings) {
				sorted.add(
					if (count == 1) key else "$key(×$count)")
			}
			sort(sorted)
			if (bundleCount <= 3) {
				sorted.joinTo(builder, ", ")
			}
			else
			{
				val pre = buildString { newlineTab(this, indent) }
				sorted.joinTo(builder, pre, pre)
			}
		} else {
			append("$bundleCount entries")
		}
		append(")")
	}

	/**
	 * Add the plan to this bundle tree.  Use `self` as a monitor for mutual
	 * exclusion to ensure *changes* from multiple fibers won't interfere, *not*
	 * to ensure the mutual safety of [A_BundleTree.expand].
	 */
	override fun o_AddPlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = synchronized(self) {
		self.setSlot(
			ALL_PLANS_IN_PROGRESS,
			layeredMapWithPlan(
				self.slot(ALL_PLANS_IN_PROGRESS), planInProgress))
		self.setSlot(
			UNCLASSIFIED,
			layeredMapWithPlan(self.slot(UNCLASSIFIED), planInProgress))
		if (planInProgress.isBackwardJump()) {
			self.setSlot(HAS_BACKWARD_JUMP_INSTRUCTION, 1)
		}
	}

	override fun o_AllParsingPlansInProgress(self: AvailObject) =
		self.slot(ALL_PLANS_IN_PROGRESS)

	@AvailMethod
	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.traversed().sameAddressAs(self)

	/**
	 * Expand the bundle tree if there's anything unclassified in it.
	 */
	@AvailMethod
	override fun o_Expand(
		self: AvailObject,
		module: A_Module
	) {
		var unclassified: A_Map = self.volatileSlot(UNCLASSIFIED)
		if (unclassified.mapSize() == 0) {
			return
		}
		synchronized(self) {
			unclassified = self.volatileSlot(UNCLASSIFIED)
			if (unclassified.mapSize() == 0) {
				// Someone else expanded it since we checked outside the
				// monitor, above.
				return
			}
			val complete = Mutable<A_Set>(self.slot(LAZY_COMPLETE))
			val incomplete = Mutable<A_Map>(self.slot(LAZY_INCOMPLETE))
			val caseInsensitive = Mutable<A_Map>(
				self.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE))
			val actionMap = Mutable<A_Map>(self.slot(LAZY_ACTIONS))
			val prefilterMap = Mutable<A_Map>(self.slot(LAZY_PREFILTER_MAP))
			val typeFilterPairs = Mutable<A_Tuple>(
				self.slot(LAZY_TYPE_FILTER_PAIRS_TUPLE))
			val oldTypeFilterSize = typeFilterPairs.value.tupleSize()
			val allAncestorModules = module.allAncestors()
			val allPlansInProgress: A_Map = self.slot(ALL_PLANS_IN_PROGRESS)

			// Figure out what the latestBackwardJump will be for any successor
			// bundle trees that need to be created.
			val latestBackwardJump: A_BundleTree
			if (self.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0) {
				// New descendants will point to me as a potential target.
				if (self.slot(IS_SOURCE_OF_CYCLE) != 0) {
					// It was already the source of a backward link.  We don't
					// need to create any more descendants here.
					return
				}
				// It's not already the source of a cycle.  See if we can
				// find an equivalent ancestor to cycle back to.
				var ancestor: A_BundleTree = self.slot(LATEST_BACKWARD_JUMP)
				while (!ancestor.equalsNil()) {
					if (ancestor.allParsingPlansInProgress().equals(
							allPlansInProgress)) {
						// This ancestor is equivalent to me, so mark me as a
						// backward cyclic link and plug that exact ancestor
						// into the LATEST_BACKWARD_JUMP slot.
						self.setSlot(IS_SOURCE_OF_CYCLE, 1)
						self.setSlot(LATEST_BACKWARD_JUMP, ancestor)
						// The caller will deal with fully expanding the
						// ancestor.
						return
					}
					ancestor = ancestor.latestBackwardJump()
				}
				// We didn't find a usable ancestor to cycle back to. New
				// successors should link back to me.
				latestBackwardJump = self
			} else {
				// This bundle tree doesn't have a backward jump, so any new
				// descendants should use the same LATEST_BACKWARD_JUMP as me.
				latestBackwardJump = self.slot(LATEST_BACKWARD_JUMP)
			}

			// Update my components.
			unclassified.mapIterable().forEach {
				(bundle, defToPlansInProgress: A_Map) ->
				defToPlansInProgress.mapIterable().forEach {
					(_, plansInProgress: A_Set) ->
					plansInProgress.forEach { planInProgress ->
						val pc = planInProgress.parsingPc()
						val plan = planInProgress.parsingPlan()
						val instructions = plan.parsingInstructions()
						if (pc == instructions.tupleSize() + 1) {
							// Just reached the end of these instructions.
							// It's past the end of the parsing instructions.
							complete.value =
								complete.value.setWithElementCanDestroy(
									bundle, true)
						} else {
							val timeBefore = AvailRuntimeSupport.captureNanos()
							val instruction = instructions.tupleIntAt(pc)
							val op = decode(instruction)
							updateForPlan(
								self,
								plan,
								pc,
								allAncestorModules,
								complete,
								incomplete,
								caseInsensitive,
								actionMap,
								prefilterMap,
								typeFilterPairs)
							val timeAfter = AvailRuntimeSupport.captureNanos()
							op.expandingStatisticInNanoseconds.record(
								timeAfter - timeBefore,
								Interpreter.currentIndexOrZero())
						}
					}
				}
			}
			// Write back the updates.
			self.setSlot(LAZY_COMPLETE, complete.value.makeShared())
			self.setSlot(LAZY_INCOMPLETE, incomplete.value.makeShared())
			self.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE,
				caseInsensitive.value.makeShared())
			self.setSlot(LAZY_ACTIONS, actionMap.value.makeShared())
			self.setSlot(LAZY_PREFILTER_MAP, prefilterMap.value.makeShared())
			self.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE,
				typeFilterPairs.value.makeShared())
			if (typeFilterPairs.value.tupleSize() != oldTypeFilterSize) {
				// Rebuild the type-checking lookup tree.
				val tree = parserTypeChecker.createRoot(
					toList(typeFilterPairs.value),
					listOf(
						restrictionForType(
							PARSE_PHRASE.mostGeneralType(),
							RestrictionFlagEncoding.BOXED)),
					latestBackwardJump)
				self.setSlot(
					LAZY_TYPE_FILTER_TREE_POJO, identityPojo(tree).makeShared())
			}
			// Do this volatile write last for correctness.
			self.setVolatileSlot(UNCLASSIFIED, emptyMap())
		}
	}

	/**
	 * An [A_GrammaticalRestriction] has been added.  Update this bundle tree
	 * and any relevant successors related to the given
	 * [A_ParsingPlanInProgress] to agree with the new restriction.
	 */
	override fun o_UpdateForNewGrammaticalRestriction(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress,
		treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
	) = synchronized(self) {
		val plan = planInProgress.parsingPlan()
		if (self.slot(UNCLASSIFIED).hasKey(plan.bundle())) {
			// The plan (or another plan with the same bundle) is still
			// unclassified, so do nothing.
			return
		}
		val instructions = plan.parsingInstructions()
		val pcsToVisit: Deque<Int> = ArrayDeque()
		pcsToVisit.add(planInProgress.parsingPc())
		while (!pcsToVisit.isEmpty()) {
			val pc = pcsToVisit.removeLast()
			if (pc == instructions.tupleSize() + 1) {
				// We've reached an end-point for parsing this plan.  The
				// grammatical restriction has no remaining effect.
				return
			}
			val instruction = instructions.tupleIntAt(pc)
			when (val op = decode(instruction)) {
				JUMP_BACKWARD, JUMP_FORWARD, BRANCH_FORWARD -> {
					// These should have bubbled out of the bundle tree.
					// Loop to get to the affected successor trees.
					pcsToVisit.addAll(op.successorPcs(instruction, pc))
				}
				CHECK_ARGUMENT, TYPE_CHECK_ARGUMENT -> {
					// Keep it simple and invalidate this entire bundle
					// tree.
					invalidate(self)
				}
				PARSE_PART -> {
					// Look it up in LAZY_INCOMPLETE.
					val keywordIndex = op.keywordIndex(instruction)
					val keyword: A_String =
						plan.bundle().messageParts().tupleAt(keywordIndex)
					val successor: A_BundleTree =
						self.slot(LAZY_INCOMPLETE).mapAt(keyword)
					treesToVisit.add(
						Pair(successor, newPlanInProgress(plan, pc + 1)))
				}
				PARSE_PART_CASE_INSENSITIVELY -> {
					// Look it up in LAZY_INCOMPLETE_CASE_INSENSITIVE.
					val keywordIndex = op.keywordIndex(instruction)
					val keyword: A_String =
						plan.bundle().messageParts().tupleAt(keywordIndex)
					val successor: A_BundleTree =
						self.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE)
							.mapAt(keyword)
					treesToVisit.add(
						Pair(successor, newPlanInProgress(plan, pc + 1)))
				}
				else -> {
					// It's an ordinary action.  Each JUMP and BRANCH was
					// already dealt with in a previous case.
					val successors: A_Tuple =
						self.slot(LAZY_ACTIONS).mapAt(instructions.tupleAt(pc))
					for (successor in successors) {
						treesToVisit.add(
							Pair(successor, newPlanInProgress(plan, pc + 1)))
					}
				}
			}
		}
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int {
		assert(isShared)
		var hash = self.slot(HASH_OR_ZERO)
		if (hash == 0) {
			synchronized(self) {
				hash = self.slot(HASH_OR_ZERO)
				if (hash == 0) {
					hash = AvailRuntimeSupport.nextNonzeroHash()
					self.setSlot(HASH_OR_ZERO, hash)
				}
			}
		}
		return hash
	}

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type =
		TypeDescriptor.Types.MESSAGE_BUNDLE_TREE.o()

	@AvailMethod
	override fun o_LazyActions(self: AvailObject): A_Map {
		assert(isShared)
		synchronized(self) { return self.slot(LAZY_ACTIONS) }
	}

	@AvailMethod
	override fun o_LazyComplete(self: AvailObject): A_Set {
		assert(isShared)
		synchronized(self) { return self.slot(LAZY_COMPLETE) }
	}

	@AvailMethod
	override fun o_LazyIncomplete(self: AvailObject): A_Map {
		assert(isShared)
		synchronized(self) { return self.slot(LAZY_INCOMPLETE) }
	}

	@AvailMethod
	override fun o_LazyIncompleteCaseInsensitive(self: AvailObject): A_Map {
		assert(isShared)
		synchronized(self) {
			return self.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE)
		}
	}

	@AvailMethod
	override fun o_LazyPrefilterMap(self: AvailObject): A_Map {
		assert(isShared)
		synchronized(self) { return self.slot(LAZY_PREFILTER_MAP) }
	}

	//	lazyTypeFilterPairs
	@AvailMethod
	override fun o_LazyTypeFilterTreePojo(self: AvailObject): A_BasicObject {
		assert(isShared)
		synchronized(self) { return self.slot(LAZY_TYPE_FILTER_TREE_POJO) }
	}

	@AvailMethod
	override fun o_MakeImmutable(self: AvailObject): AvailObject {
		return if (isMutable) {
			// Never actually make a message bundle tree immutable. They are
			// always shared.
			self.makeShared()
		} else self
	}

	/**
	 * Remove the plan from this bundle tree.  We don't need to remove the
	 * bundle itself if this is the last plan for that bundle, since this can
	 * only be called when satisfying a forward declaration – by adding another
	 * definition.
	 */
	override fun o_RemovePlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = synchronized(self) {
		self.setSlot(
			ALL_PLANS_IN_PROGRESS,
			layeredMapWithoutPlan(
				self.slot(ALL_PLANS_IN_PROGRESS),
				planInProgress))
		self.setSlot(
			UNCLASSIFIED,
			layeredMapWithoutPlan(self.slot(UNCLASSIFIED), planInProgress))
	}

	/**
	 * Answer the nearest ancestor that was known at some time to have a
	 * backward jump in at least one of its parsing-plans-in-progress.
	 *
	 * @param self
	 *   The bundle tree.
	 * @return
	 *   A predecessor bundle tree or nil.
	 */
	override fun o_LatestBackwardJump(self: AvailObject): A_BundleTree =
		self.slot(LATEST_BACKWARD_JUMP)

	override fun o_HasBackwardJump(self: AvailObject): Boolean =
		self.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0

	override fun o_IsSourceOfCycle(self: AvailObject): Boolean =
		self.slot(IS_SOURCE_OF_CYCLE) != 0

	override fun o_IsSourceOfCycle(
		self: AvailObject,
		isSourceOfCycle: Boolean
	) = self.setSlot(IS_SOURCE_OF_CYCLE, if (isSourceOfCycle) 1 else 0)

	override fun mutable(): MessageBundleTreeDescriptor = mutable

	// There is no immutable descriptor. Use the shared one.
	override fun immutable(): MessageBundleTreeDescriptor = shared

	override fun shared(): MessageBundleTreeDescriptor = shared

	companion object {
		/**
		 * This is the [LookupTreeAdaptor] for building and navigating the
		 * [ObjectSlots.LAZY_TYPE_FILTER_TREE_POJO].  It gets built from
		 * 2-tuples containing a [phrase type][PhraseTypeDescriptor] and a
		 * corresponding [A_ParsingPlanInProgress].  The type is used to perform
		 * type filtering after parsing each leaf argument, and the phrase type
		 * is the expected type of that latest argument.
		 */
		val parserTypeChecker =
			object : LookupTreeAdaptor<A_Tuple, A_BundleTree, A_BundleTree>()
		{
			// Extract the phrase type from the pair, and use it directly as
			// the signature type for the tree.
			override fun extractSignature(element: A_Tuple): A_Type =
				element.tupleAt(1)

			override fun constructResult(
				elements: List<A_Tuple>,
				memento: A_BundleTree
			) = newBundleTree(latestBackwardJump = memento).apply {
				elements.forEach { (_, planInProgress) ->
					addPlanInProgress(planInProgress)
				}
			}

			override fun compareTypes(
				argumentRestrictions: List<TypeRestriction>,
				signatureType: A_Type
			) = compareForParsing(argumentRestrictions, signatureType)

			override fun testsArgumentPositions() = false

			override fun subtypesHideSupertypes() = false
		}

		/**
		 * Add the [A_ParsingPlanInProgress] to the given [A_Map].  The map is
		 * from [A_Bundle] to a submap, which is from [A_Definition] to a set of
		 * [plans-in-progress][A_ParsingPlanInProgress] for that
		 * bundle/definition pair.
		 *
		 * @param outerMap
		 *   {bundle → {definition → {plan-in-progress |}|}|}
		 * @param planInProgress
		 *   The [A_ParsingPlanInProgress] to add.
		 * @return
		 *   The map of maps of plans-in-progress.
		 */
		private fun layeredMapWithPlan(
			outerMap: A_Map,
			planInProgress: A_ParsingPlanInProgress
		): A_Map {
			val plan = planInProgress.parsingPlan()
			val bundle = plan.bundle()
			val definition = plan.definition()
			var submap =
				if (outerMap.hasKey(bundle)) outerMap.mapAt(bundle)
				else emptyMap()
			var inProgressSet =
				if (submap.hasKey(definition)) submap.mapAt(definition)
				else emptySet()
			inProgressSet =
				inProgressSet.setWithElementCanDestroy(planInProgress, true)
			submap =
				submap.mapAtPuttingCanDestroy(definition, inProgressSet, true)
			val newOuterMap =
				outerMap.mapAtPuttingCanDestroy(bundle, submap, true)
			return newOuterMap.makeShared()
		}

		/** A [Statistic] for tracking bundle tree invalidations.  */
		private val invalidationsStat = Statistic(
			"(invalidations)", StatisticReport.EXPANDING_PARSING_INSTRUCTIONS)

		/**
		 * Invalidate the internal expansion of the given bundle tree.  Note
		 * that this should only happen when we're changing the grammar in some
		 * way, which happens mutually exclusive of parsing, so we don't really
		 * need to use a lock.
		 *
		 * @param self
		 *   Which [A_BundleTree] to invalidate.
		 */
		private fun invalidate(self: AvailObject) =
			invalidationsStat.record(Interpreter.currentIndexOrZero()) {
				synchronized(self) {
					self.setSlot(LAZY_COMPLETE, emptySet())
					self.setSlot(LAZY_INCOMPLETE, emptyMap())
					self.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap())
					self.setSlot(LAZY_ACTIONS, emptyMap())
					self.setSlot(LAZY_PREFILTER_MAP, emptyMap())
					self.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, emptyTuple())
					self.setSlot(LAZY_TYPE_FILTER_TREE_POJO, nil)
					self.setSlot(UNCLASSIFIED, self.slot(ALL_PLANS_IN_PROGRESS))
				}
			}

		/**
		 * Remove the [A_ParsingPlanInProgress] from the given map.  The map is
		 * from [A_Bundle] to a submap, which is from [A_Definition] to a set of
		 * [plans-in-progress][A_ParsingPlanInProgress] for that
		 * bundle/definition pair.
		 *
		 * @param outerMap
		 *   {bundle → {definition → {plan-in-progress |}|}|}
		 * @param planInProgress
		 *   The [A_ParsingPlanInProgress] to remove.
		 * @return
		 *   The new map of maps, with the plan-in-progress removed.
		 */
		private fun layeredMapWithoutPlan(
			outerMap: A_Map,
			planInProgress: A_ParsingPlanInProgress
		): A_Map {
			val plan = planInProgress.parsingPlan()
			val bundle = plan.bundle()
			val definition = plan.definition()
			if (!outerMap.hasKey(bundle)) {
				return outerMap
			}
			var submap: A_Map = outerMap.mapAt(bundle)
			if (!submap.hasKey(definition)) {
				return outerMap
			}
			var inProgressSet: A_Set = submap.mapAt(definition)
			if (!inProgressSet.hasElement(planInProgress)) {
				return outerMap
			}
			inProgressSet = inProgressSet.setWithoutElementCanDestroy(
				planInProgress, true)
			submap =
				if (inProgressSet.setSize() > 0) {
					submap.mapAtPuttingCanDestroy(
						definition, inProgressSet, true)
				} else {
					submap.mapWithoutKeyCanDestroy(definition, true)
				}
			val newOuterMap =
				if (submap.mapSize() > 0) {
					outerMap.mapAtPuttingCanDestroy(bundle, submap, true)
				} else outerMap.mapWithoutKeyCanDestroy(bundle, true)
			return newOuterMap.makeShared()
		}

		/**
		 * Categorize a single parsing plan in progress.
		 *
		 * @param bundleTree
		 *   The [A_BundleTree] that we're updating.  The state is passed
		 *   separately in arguments, to be written back after all the mutable
		 *   arguments have been updated for all parsing-plans-in-progress.
		 * @param plan
		 *   The [A_DefinitionParsingPlan] to categorize.
		 * @param pc
		 *   The one-based program counter that indexes each applicable
		 *   [plan][A_DefinitionParsingPlan]'s [instructions
		 *   ][A_DefinitionParsingPlan.parsingInstructions].  Note that this
		 *   value can be one past the end of the instructions, indicating
		 *   parsing is complete.
		 * @param allAncestorModules
		 *   The [A_Set] of modules that are ancestors of (or equal to) the
		 *   current module being parsed.  This is used to restrict the
		 *   visibility of semantic and grammatical restrictions, as well as
		 *   which method and macro [A_Definition]s can be parsed.
		 * @param complete
		 *   A [Mutable]&#32;[set][A_Set] of [A_Bundle]s which have had a send
		 *   phrase completely parsed at this position.
		 * @param incomplete
		 *   A [Mutable]&#32;[map][A_Map] from [A_String] to successor
		 *   [A_BundleTree]. If a token's string matches one of the keys, it
		 *   will be consumed and the successor bundle tree will be visited.
		 * @param caseInsensitive
		 *   A [Mutable]&#32;[map][A_Map] from lower-case [A_String] to
		 *   successor [A_BundleTree].  If the lower-case version of a token's
		 *   string matches on of the keys, it will be consumed and the
		 *   successor bundle tree will be visited.
		 * @param actionMap
		 *   A [Mutable]&#32;[map][A_Map] from an Avail integer encoding a
		 *   [ParsingOperation] to a [A_Tuple] of successor [A_BundleTree]s.
		 *   Typically there is only one successor bundle tree, but some parsing
		 *   operations require that the tree diverge into multiple successors.
		 * @param prefilterMap
		 *   A [Mutable]&#32[map][A_Map] from [A_Bundle] to a successor
		 *   [A_BundleTree]. If the most recently parsed argument phrase is a
		 *   send phrase and its bundle is a key in this map, the successor will
		 *   be explored, but not the sole action in the actionMap (which
		 *   contains at most one entry, a [ParsingOperation.CHECK_ARGUMENT].
		 *   If the bundle is not present, or if the latest argument is not a
		 *   send phrase, allow the associated action(s) to be followed instead.
		 *   This accomplishes grammatical restriction, assuming this method
		 *   populates the successor bundle trees correctly.
		 * @param typeFilterTuples
		 *   A [Mutable]&#32;[tuple][A_Tuple] of pairs (2-tuples) from
		 *   [phrase][A_Phrase] [type][A_Type] to [A_DefinitionParsingPlan].
		 */
		private fun updateForPlan(
			bundleTree: AvailObject,
			plan: A_DefinitionParsingPlan,
			pc: Int,
			allAncestorModules: A_Set,
			complete: Mutable<A_Set>,
			incomplete: Mutable<A_Map>,
			caseInsensitive: Mutable<A_Map>,
			actionMap: Mutable<A_Map>,
			prefilterMap: Mutable<A_Map>,
			typeFilterTuples: Mutable<A_Tuple>
		) {
			val hasBackwardJump =
				bundleTree.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0
			val latestBackwardJump: A_BundleTree =
				if (hasBackwardJump) bundleTree
				else bundleTree.slot(LATEST_BACKWARD_JUMP)
			val instructions = plan.parsingInstructions()
			if (pc == instructions.tupleSize() + 1) {
				complete.value =
					complete.value.setWithElementCanDestroy(plan.bundle(), true)
				return
			}
			val instruction = plan.parsingInstructions().tupleIntAt(pc)
			val op = decode(instruction)
			when (op) {
				JUMP_FORWARD, BRANCH_FORWARD, JUMP_BACKWARD -> {
					if (op == JUMP_BACKWARD && !hasBackwardJump) {
						// We just discovered the first backward jump in any
						// parsing-plan-in-progress at this node.
						bundleTree.setSlot(HAS_BACKWARD_JUMP_INSTRUCTION, 1)
					}
					// Bubble control flow right out of the bundle trees. There
					// should never be a JUMP or BRANCH in an actionMap.
					// Rather, the successor instructions (recursively in the
					// case of jumps to other jumps) are directly exploded into
					// the current bundle tree.  Not only does this save the
					// cost of dispatching these control flow operations, but it
					// also allows more potential matches for the next token to
					// be undertaken in a single lookup. We can safely recurse
					// here, because plans cannot have any empty loops due to
					// progress check instructions.
					for (nextPc in op.successorPcs(instruction, pc)) {
						updateForPlan(
							bundleTree,
							plan,
							nextPc,
							allAncestorModules,
							complete,
							incomplete,
							caseInsensitive,
							actionMap,
							prefilterMap,
							typeFilterTuples)
					}
					return
				}
				PARSE_PART, PARSE_PART_CASE_INSENSITIVELY -> {
					// Parse a specific keyword, or case-insensitive keyword.
					val keywordIndex = op.keywordIndex(instruction)
					val part: A_String =
						plan.bundle().messageParts().tupleAt(keywordIndex)
					val map =
						if (op === PARSE_PART) incomplete
						else caseInsensitive
					val subtree: A_BundleTree
					if (map.value.hasKey(part)) {
						subtree = map.value.mapAt(part)
					} else {
						subtree = newBundleTree(latestBackwardJump)
						map.value = map.value.mapAtPuttingCanDestroy(
							part, subtree, true)
					}
					subtree.addPlanInProgress(newPlanInProgress(plan, pc + 1))
					return
				}
				PREPARE_TO_RUN_PREFIX_FUNCTION -> {
					// Each macro definition has its own prefix functions, so
					// for each plan create a separate successor message bundle
					// tree.
					val newTarget: A_BundleTree =
						newBundleTree(latestBackwardJump)
					newTarget.addPlanInProgress(newPlanInProgress(plan, pc + 1))
					val instructionObject: A_Number = fromInt(instruction)
					var successors: A_Tuple =
						if (actionMap.value.hasKey(instructionObject)) {
							actionMap.value.mapAt(instructionObject)
						} else {
							emptyTuple()
						}
					successors = successors.appendCanDestroy(newTarget, true)
					actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
						instructionObject, successors, true)
					// We added it to the actions, so don't fall through.
					return
				}
				TYPE_CHECK_ARGUMENT -> {
					// An argument was just parsed and passed its grammatical
					// restriction check.  Now it needs to do a type check with
					// a type-dispatch tree.
					val typeIndex = op.typeCheckArgumentIndex(instruction)
					val phraseType: A_Type = constantForIndex(typeIndex)
					val planInProgress = newPlanInProgress(plan, pc + 1)
					val pair = tuple(phraseType, planInProgress)
					typeFilterTuples.value =
						typeFilterTuples.value.appendCanDestroy(pair, true)
					return
				}
				CHECK_ARGUMENT -> {
					// It's a checkArgument instruction.
					val checkArgumentIndex = op.checkArgumentIndex(instruction)
					// Add it to the action map.
					val successor: A_BundleTree
					val instructionObject: A_Number = fromInt(instruction)
					if (actionMap.value.hasKey(instructionObject)) {
						val successors: A_Tuple =
							actionMap.value.mapAt(instructionObject)
						assert(successors.tupleSize() == 1)
						successor = successors.tupleAt(1)
					} else {
						successor = newBundleTree(latestBackwardJump)
						actionMap.value =
							actionMap.value.mapAtPuttingCanDestroy(
								instructionObject, tuple(successor), true)
					}
					var forbiddenBundles = emptySet()
					plan.bundle().grammaticalRestrictions().forEach {
						restriction ->
						// Exclude grammatical restrictions that aren't defined
						// in an ancestor module.
						val definitionModule = restriction.definitionModule()
						if (definitionModule.equalsNil()
							|| allAncestorModules
								.hasElement(definitionModule)
						) {
							val bundles: A_Set =
								restriction.argumentRestrictionSets().tupleAt(
									checkArgumentIndex)
							forbiddenBundles =
								forbiddenBundles.setUnionCanDestroy(
									bundles, true)
						}
					}
					val planInProgress = newPlanInProgress(plan, pc + 1)
					// Add it to every existing branch where it's permitted.
					prefilterMap.value.mapIterable().forEach {
						(bundle, prefilterSuccessor) ->
						if (!forbiddenBundles.hasElement(bundle)) {
							prefilterSuccessor.addPlanInProgress(planInProgress)
						}
					}
					// Add branches for any new restrictions.  Pre-populate with
					// every bundle present thus far, since none of them had
					// this restriction.
					forbiddenBundles.forEach { restrictedBundle ->
						if (!prefilterMap.value.hasKey(restrictedBundle)) {
							val newTarget = newBundleTree(latestBackwardJump)
							// Be careful.  We can't add ALL_BUNDLES, since it
							// may contain some bundles that are still
							// UNCLASSIFIED.  Instead, use ALL_BUNDLES of the
							// successor found under this instruction, since it
							// *has* been kept up to date as the bundles have
							// gotten classified.
							successor.allParsingPlansInProgress().mapIterable()
								.forEach {
									(_, defMap) ->
									defMap.mapIterable().forEach { (_, plans) ->
										plans.forEach { inProgress ->
											newTarget.addPlanInProgress(inProgress)
										}
									}
								}
							prefilterMap.value =
								prefilterMap.value.mapAtPuttingCanDestroy(
									restrictedBundle, newTarget, true)
						}
					}
					// Finally, add it to the action map.  This had to be
					// postponed, since we didn't want to add it under any new
					// restrictions, and the actionMap is what gets visited to
					// populate new restrictions.
					successor.addPlanInProgress(planInProgress)
					// Note:  Fall out of the when{} here, since the action also
					// has to be added to the actionMap (to deal with the case
					// that a subexpression is a non-send, or a send that is not
					// forbidden in this position by *any* potential parent
					// sends).
				}
				else -> {
					// Fall out of when{} for all other operations.
				}
			}
			// It's not a keyword parsing instruction or a type-check or
			// preparation for a prefix function, so it's an ordinary parsing
			// instruction.  It might be a CHECK_ARGUMENT that has already
			// updated the prefilterMap and fallen out. Control flow
			// instructions should have been dealt with in a prior case.
			val nextPcs = op.successorPcs(instruction, pc)
			assert(nextPcs.size == 1 && nextPcs[0] == pc + 1)
			val successor: A_BundleTree
			val instructionObject: A_Number = fromInt(instruction)
			if (actionMap.value.hasKey(instructionObject)) {
				val successors: A_Tuple =
					actionMap.value.mapAt(instructionObject)
				assert(successors.tupleSize() == 1)
				successor = successors.tupleAt(1)
			} else {
				successor = newBundleTree(latestBackwardJump)
				val successors = tuple(successor)
				actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
					instructionObject, successors, true)
			}
			successor.addPlanInProgress(newPlanInProgress(plan, pc + 1))
		}

		/**
		 * Create a new empty [A_BundleTree].
		 *
		 * @param latestBackwardJump
		 *   The nearest ancestor bundle tree that was known at some point to
		 *   contain a backward jump instruction, or nil if there were no such
		 *   ancestors.
		 * @return
		 *   A new empty message bundle tree.
		 */
		@JvmStatic
		fun newBundleTree(
			latestBackwardJump: A_BundleTree
		): A_BundleTree = with(mutable.create()) {
			setSlot(HASH_OR_ZERO, 0)
			setSlot(ALL_PLANS_IN_PROGRESS, emptyMap())
			setSlot(UNCLASSIFIED, emptyMap())
			setSlot(LAZY_COMPLETE, emptySet())
			setSlot(LAZY_INCOMPLETE, emptyMap())
			setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap())
			setSlot(LAZY_ACTIONS, emptyMap())
			setSlot(LAZY_PREFILTER_MAP, emptyMap())
			setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, emptyTuple())
			setSlot(LAZY_TYPE_FILTER_TREE_POJO, nil)
			setSlot(LATEST_BACKWARD_JUMP, latestBackwardJump)
			makeShared()
		}

		/** The mutable [MessageBundleTreeDescriptor].  */
		private val mutable = MessageBundleTreeDescriptor(Mutability.MUTABLE)

		/** The shared [MessageBundleTreeDescriptor].  */
		private val shared = MessageBundleTreeDescriptor(Mutability.SHARED)
	}
}