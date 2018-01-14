/**
 * MessageBundleTreeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.AvailRuntime;
import com.avail.AvailThread;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeAdaptor;
import com.avail.dispatch.TypeComparison;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.Mutable;
import com.avail.utility.Pair;
import com.avail.utility.Strings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.avail.compiler.ParsingOperation.PARSE_PART;
import static com.avail.compiler.ParsingOperation.decode;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.MessageBundleTreeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.MessageBundleTreeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ParsingPlanInProgressDescriptor
	.newPlanInProgress;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE_TREE;

/**
 * A {@linkplain MessageBundleTreeDescriptor message bundle tree} is used by the
 * Avail parser.  Since the Avail syntax is so flexible, we make up for that
 * simplicity with a complementary complexity in the parsing mechanism.  A
 * message bundle tree is used to keep track of how far along the parser has
 * gotten in the parsing of a method invocation.  More powerfully, it does this
 * for multiple methods simultaneously, at least up to the point that the method
 * names diverge.
 *
 * <p>
 * For example, assume the methods "_foo_bar" and "_foo_baz" are both visible in
 * the current module.  After parsing an argument, the "foo" keyword, and
 * another argument, the next thing to look for is either the "bar" keyword or
 * the "baz" keyword.  Depending which keyword comes next, we will have parsed
 * an invocation of either the first or the second method.  Both possibilities
 * have been parsed together (i.e., only once) up to this point, and the next
 * keyword encountered decides which (if either) method call is being invoked.
 * </p>
 *
 * <p>
 * {@linkplain MessageSplitter} is used to generate a sequence of parsing
 * instructions for a method name.  These parsing instructions determine how
 * long multiple potential method invocations can be parsed together and when
 * they must diverge.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MessageBundleTreeDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the hash and the parsing pc.  See below.
		 */
		HASH_AND_MORE;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		@HideFieldInDebugger
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);

		/**
		 * This flag is set when this bundle tree contains at least one
		 * parsing-plan-in-progress at a {@link ParsingOperation#JUMP_BACKWARD}
		 * instruction.
		 */
		static final BitField HAS_BACKWARD_JUMP_INSTRUCTION =
			bitField(HASH_AND_MORE, 32, 1);

		/**
		 * This flag is set when a bundle tree is redirected to an equivalent
		 * ancestor bundle tree.  The current bundle tree's {#link
		 * #LATEST_BACKWARD_JUMP} is set directly to the target of the cycle
		 * when this flag is set.
		 */
		static final BitField IS_SOURCE_OF_CYCLE =
			bitField(HASH_AND_MORE, 33, 1);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain MapDescriptor map} from {@link A_Bundle}s to maps,
		 * which are themselves from {@link A_Definition definitions} to {@link
		 * A_Set}s of {@link A_ParsingPlanInProgress plans-in-progress} for that
		 * definition/bundle.  Note that the inner maps may be empty in the case
		 * that a grammatical restriction has been defined before any visible
		 * definitions.  This doesn't affect parsing, but makes the logic
		 * easier about deciding which grammatical restrictions are visible when
		 * adding a definition later.
		 */
		ALL_PLANS_IN_PROGRESS,

		/**
		 * A {@linkplain MapDescriptor map} from visible {@linkplain A_Bundle
		 * bundles} to maps from {@link A_Definition definitions} to the
		 * {@link A_Set}s of {@link A_ParsingPlanInProgress plans-in-progress}
		 * for that definition/bundle.  It has the same content as ALL_PLANS
		 * until these items have been categorized as complete, incomplete,
		 * action, or prefilter. They are categorized if and when this message
		 * bundle tree is reached during parsing.
		 */
		UNCLASSIFIED,

		/**
		 * A {@link A_Set set} of {@link A_Bundle bundles} that indicate which
		 * methods have just had a complete invocation parsed at this point in
		 * the tree.
		 */
		LAZY_COMPLETE,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * string} to successor {@linkplain MessageBundleTreeDescriptor message
		 * bundle trees}. During parsing, if the next token is a key of this
		 * map then consume that token, look it up in this map, and continue
		 * parsing with the corresponding message bundle tree. Otherwise record
		 * a suitable parsing failure message for this position in the source
		 * stream (in case this ends up being the rightmost parse position to be
		 * reached).
		 *
		 * <p>{@linkplain MessageBundleDescriptor Message bundles} only get
		 * added to this map if their current instruction is the {@link
		 * ParsingOperation#PARSE_PART} instruction. There may be other
		 * instructions current for other message bundles, but they will be
		 * represented in the {@link #LAZY_ACTIONS} map, or the {@link
		 * #LAZY_PREFILTER_MAP} if their instruction is a {@link
		 * ParsingOperation#CHECK_ARGUMENT}.</p>
		 */
		LAZY_INCOMPLETE,

		/**
		 * A {@linkplain MapDescriptor map} from lower-case {@linkplain
		 * StringDescriptor strings} to successor {@linkplain
		 * MessageBundleTreeDescriptor message bundle trees}. During parsing, if
		 * the next token, following conversion to lower case, is a key of this
		 * map then consume that token, look it up in this map, and continue
		 * parsing with the corresponding message bundle tree. Otherwise record
		 * a suitable parsing failure message for this position in the source
		 * stream (in case this ends up being the rightmost parse position to be
		 * reached).
		 *
		 * <p>{@linkplain MessageBundleDescriptor Message bundles} only get
		 * added to this map if their current instruction is the {@link
		 * ParsingOperation#PARSE_PART_CASE_INSENSITIVELY} instruction. There
		 * may be other instructions current for other message bundles, but they
		 * will be represented in the {@link #LAZY_ACTIONS} map, or the {@link
		 * #LAZY_PREFILTER_MAP} if their instruction is a {@link
		 * ParsingOperation#CHECK_ARGUMENT}.</p>
		 */
		LAZY_INCOMPLETE_CASE_INSENSITIVE,

		/**
		 * This is a map from an encoded {@link ParsingOperation (an {@linkplain
		 * IntegerDescriptor integer}) to a {@link A_Tuple} of {@linkplain
		 * A_BundleTree}s to attempt if the instruction succeeds.
		 *
		 * <p>Note that the {@link ParsingOperation#PARSE_PART} and {@link
		 * ParsingOperation#PARSE_PART_CASE_INSENSITIVELY} instructions are
		 * treated specially, as only one keyword can be next in the source
		 * stream (so there's no value in checking whether it's an X, whether
		 * it's a Y, whether it's a Z, etc. Instead, the {@link
		 * #LAZY_INCOMPLETE} and {@link #LAZY_INCOMPLETE_CASE_INSENSITIVE}
		 * {@linkplain MapDescriptor map} takes care of dealing with this
		 * efficiently with a single lookup.</p>
		 *
		 * <p>Similarly, the {@link ParsingOperation#CHECK_ARGUMENT} instruction
		 * is treated specially. When it is encountered and the argument that
		 * was just parsed is a send node, that send node is looked up in the
		 * {@link #LAZY_PREFILTER_MAP}, yielding the next message bundle tree.
		 * If it's not present as a key (or the argument isn't a send), then the
		 * instruction is looked up normally in the lazy actions map.</p>
		 */
		LAZY_ACTIONS,

		/**
		 * If we wait until all tokens and arguments of a potential method send
		 * have been parsed before checking that all the arguments have the
		 * right types and precedence then we may spend a <em>lot</em> of extra
		 * effort parsing unnecessary expressions. For example, the "_×_"
		 * operation might not allow a "_+_" call for its left or right
		 * arguments, so parsing "1+2×…" as "(1+2)×…" is wasted effort.
		 *
		 * <p>This is especially expensive for operations with many arguments
		 * that could otherwise be culled by the shapes and types of early
		 * arguments, such as for "«_‡++»", which forbids arguments being
		 * invocations of the same message, keeping a call with many arguments
		 * flat. I haven't worked out the complete recurrence relations for
		 * this, but it's probably exponential (it certainly grows <em>much</em>
		 * faster than linearly without this optimization).</p>
		 *
		 * <p>To accomplish this culling we have to filter out any inconsistent
		 * {@linkplain MessageBundleDescriptor message bundles} as we parse.
		 * Since we already do this in general while parsing expressions, all
		 * that remains is to check right after an argument has been parsed (or
		 * replayed due to memoization). The check for now is simple and doesn't
		 * consider argument types, simply excluding methods based on the
		 * grammatical restrictions.</p>
		 *
		 * <p>When a message bundle's next instruction is {@link
		 * ParsingOperation#CHECK_ARGUMENT} (which must be all or nothing within
		 * a {@linkplain MessageBundleTreeDescriptor message bundle tree}), this
		 * lazy prefilter map is populated. It maps from interesting {@linkplain
		 * MessageBundleDescriptor message bundles} that might occur as an
		 * argument to an appropriately reduced {@linkplain
		 * MessageBundleTreeDescriptor message bundle tree} (i.e., a message
		 * bundle tree containing precisely those method bundles that allow that
		 * argument. The only keys that occur are ones for which at least one
		 * restriction exists in at least one of the still possible {@linkplain
		 * MessageBundleDescriptor message bundles}. When {@link #UNCLASSIFIED}
		 * is empty, <em>all</em> such restricted argument message bundles occur
		 * in this map. Note that some of the resulting message bundle trees
		 * may be completely empty. Also note that some of the trees may be
		 * shared, so be careful to discard them rather than maintaining them
		 * when new method bundles or grammatical restrictions are added.</p>
		 *
		 * <p>When an argument is a message that is not restricted for any of
		 * the message bundles in this message bundle tree (i.e., it does not
		 * occur as a key in this map), then the sole entry in {@link
		 * #LAZY_INCOMPLETE} is used. The key is always the {@code
		 * checkArgument} instruction that all message bundles in this message
		 * bundle tree must have.</p>
		 */
		LAZY_PREFILTER_MAP,

		/**
		 * A {@link A_Tuple tuple} of pairs (2-tuples) where the first element
		 * is a phrase type and the second element is a {@link
		 * A_ParsingPlanInProgress parsing plan in progress}.  These should stay
		 * synchronized with the {@link #LAZY_TYPE_FILTER_TREE_POJO} field.
		 */
		LAZY_TYPE_FILTER_PAIRS_TUPLE,

		/**
		 * A {@link RawPojoDescriptor raw pojo} containing a type-dispatch tree
		 * for handling the case that at least one {@link
		 * A_ParsingPlanInProgress parsing plan in progress} in this message
		 * bundle tree is at a {@link A_ParsingPlanInProgress#parsingPc() pc}
		 * pointing to a {@link ParsingOperation#TYPE_CHECK_ARGUMENT} operation.
		 * This allows relatively efficient elimination of inappropriately typed
		 * arguments.
		 */
		LAZY_TYPE_FILTER_TREE_POJO,

		/**
		 * This is the most recently encountered backward jump in the ancestry
		 * of this bundle tree, or nil if none were encountered in the ancestry.
		 * There could be multiple competing parsing plans in that target bundle
		 * tree, some of which had a backward jump and some of which didn't, but
		 * we only require that at least one had a backward jump.
		 *
		 * <p>Since every loop has a backward jump, and since the target has a
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
		 * bundle trees to maintain.</p>
		 */
		LATEST_BACKWARD_JUMP;
	}

	/**
	 * This is the {@link LookupTreeAdaptor} for building and navigating the
	 * {@link ObjectSlots#LAZY_TYPE_FILTER_TREE_POJO}.  It gets built from
	 * 2-tuples containing a {@link ParseNodeTypeDescriptor phrase type} and a
	 * corresponding {@link A_ParsingPlanInProgress}.  The type is used to
	 * perform type filtering after parsing each leaf argument, and the phrase
	 * type is the expected type of that latest argument.
	 */
	public static final LookupTreeAdaptor<A_Tuple, A_BundleTree, A_BundleTree>
		parserTypeChecker =
			new LookupTreeAdaptor<A_Tuple, A_BundleTree, A_BundleTree>()
	{
		@Override
		public A_Type extractSignature (final A_Tuple pair)
		{
			// Extract the phrase type from the pair, and use it directly as the
			// signature type for the tree.
			return pair.tupleAt(1);
		}

		@Override
		public A_BundleTree constructResult (
			final List<? extends A_Tuple> elements,
			final A_BundleTree latestBackwardJump)
		{
			final A_BundleTree newBundleTree =
				newBundleTree(latestBackwardJump);
			for (final A_Tuple pair : elements)
			{
				final A_ParsingPlanInProgress planInProgress = pair.tupleAt(2);
				newBundleTree.addPlanInProgress(planInProgress);
			}
			return newBundleTree;
		}

		@Override
		public TypeComparison compareTypes (
			final A_Type criterionType, final A_Type someType)
		{
			return TypeComparison.compareForParsing(criterionType, someType);
		}

		@Override
		public boolean testsArgumentPositions ()
		{
			return false;
		}

		@Override
		public boolean subtypesHideSupertypes ()
		{
			return false;
		}
	};

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE
			|| e == ALL_PLANS_IN_PROGRESS
			|| e == UNCLASSIFIED
			|| e == LAZY_COMPLETE
			|| e == LAZY_INCOMPLETE
			|| e == LAZY_INCOMPLETE_CASE_INSENSITIVE
			|| e == LAZY_ACTIONS
			|| e == LAZY_PREFILTER_MAP
			|| e == LAZY_TYPE_FILTER_PAIRS_TUPLE
			|| e == LAZY_TYPE_FILTER_TREE_POJO
			|| e == LATEST_BACKWARD_JUMP;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("BundleTree(");
		final A_Map allPlansInProgress = object.slot(ALL_PLANS_IN_PROGRESS);
		final int bundleCount = allPlansInProgress.mapSize();
		if (bundleCount <= 15)
		{
			final Map<String, Integer> strings = new HashMap<>(bundleCount);
			for (final Entry entry : allPlansInProgress.mapIterable())
			{
				for (final Entry entry2
					: entry.value().mapIterable())
				{
					for (final A_ParsingPlanInProgress planInProgress
						: entry2.value())
					{
						final String string =
							planInProgress.nameHighlightingPc();
						if (strings.containsKey(string))
						{
							strings.put(string, (strings.get(string) + 1));
						}
						else
						{
							strings.put(string, 1);
						}
					}
				}
			}
			final List<String> sorted = new ArrayList<>();
			for (final Map.Entry<String, Integer> entry : strings.entrySet())
			{
				sorted.add(entry.getKey() + "(×" + entry.getValue() + ")");
			}
			Collections.sort(sorted);
			boolean first = true;
			for (final String string : sorted)
			{
				if (bundleCount <= 3)
				{
					builder.append(first ? "" : ", ");
				}
				else
				{
					Strings.newlineTab(builder, indent);
				}
				first = false;
				builder.append(string);
			}
		}
		else
		{
			builder.append(bundleCount);
			builder.append(" entries");
		}
		builder.append(")");
	}

	/**
	 * Add the plan to this bundle tree.  Use the object as a monitor for mutual
	 * exclusion to ensure changes from multiple fibers won't interfere, <em>not
	 * </em> to ensure the mutual safety of {@link #o_Expand(AvailObject,
	 * A_Module)}.
	 */
	@Override
	void o_AddPlanInProgress (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress)
	{
		synchronized (object)
		{
			object.setSlot(
				ALL_PLANS_IN_PROGRESS,
				layeredMapWithPlan(
					object.slot(ALL_PLANS_IN_PROGRESS), planInProgress));
			object.setSlot(
				UNCLASSIFIED,
				layeredMapWithPlan(object.slot(UNCLASSIFIED), planInProgress));
			if (planInProgress.isBackwardJump())
			{
				object.setSlot(HAS_BACKWARD_JUMP_INSTRUCTION, 1);
			}
		}
	}

	/**
	 * Add the {@link A_ParsingPlanInProgress} to the given map.  The map is
	 * from {@link A_Bundle} to a submap, which is from {@link A_Definition} to
	 * a set of {@link A_ParsingPlanInProgress plans-in-progress} for that
	 * bundle/definition pair.
	 *
	 * @param outerMap {bundle → {definition → {plan-in-progress |}|}|}
	 * @param planInProgress The {@link A_ParsingPlanInProgress} to add.
	 */
	private static A_Map layeredMapWithPlan (
		final A_Map outerMap,
		final A_ParsingPlanInProgress planInProgress)
	{
		final A_DefinitionParsingPlan plan = planInProgress.parsingPlan();
		final A_Bundle bundle = plan.bundle();
		final A_Definition definition = plan.definition();
		A_Map submap = outerMap.hasKey(bundle)
			? outerMap.mapAt(bundle)
			: emptyMap();
		A_Set inProgressSet = submap.hasKey(definition)
			? submap.mapAt(definition)
			: emptySet();
		inProgressSet = inProgressSet.setWithElementCanDestroy(
			planInProgress, true);
		submap = submap.mapAtPuttingCanDestroy(definition, inProgressSet, true);
		final A_Map newOuterMap = outerMap.mapAtPuttingCanDestroy(
			bundle, submap, true);
		return newOuterMap.makeShared();
	}

	@Override
	A_Map o_AllParsingPlansInProgress (final AvailObject object)
	{
		return object.slot(ALL_PLANS_IN_PROGRESS);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Expand the bundle tree if there's anything unclassified in it.
	 */
	@Override @AvailMethod
	void o_Expand (
		final AvailObject object,
		final A_Module module)
	{
		A_Map unclassified = object.volatileSlot(UNCLASSIFIED);
		if (unclassified.mapSize() == 0)
		{
			return;
		}
		synchronized (object)
		{
			unclassified = object.volatileSlot(UNCLASSIFIED);
			if (unclassified.mapSize() == 0)
			{
				// Someone else expanded it since we checked outside the
				// monitor, above.
				return;
			}
			final Mutable<A_Set> complete = new Mutable<>(
				object.slot(LAZY_COMPLETE));
			final Mutable<A_Map> incomplete = new Mutable<>(
				object.slot(LAZY_INCOMPLETE));
			final Mutable<A_Map> caseInsensitive = new Mutable<>(
				object.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE));
			final Mutable<A_Map> actionMap = new Mutable<>(
				object.slot(LAZY_ACTIONS));
			final Mutable<A_Map> prefilterMap = new Mutable<>(
				object.slot(LAZY_PREFILTER_MAP));
			final Mutable<A_Tuple> typeFilterPairs = new Mutable<>(
				object.slot(LAZY_TYPE_FILTER_PAIRS_TUPLE));
			final int oldTypeFilterSize = typeFilterPairs.value.tupleSize();
			final A_Set allAncestorModules = module.allAncestors();
			final A_Map allPlansInProgress = object.slot(ALL_PLANS_IN_PROGRESS);

			// Figure out what the latestBackwardJump will be for any successor
			// bundle trees that need to be created.
			A_BundleTree latestBackwardJump;
			if (object.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0)
			{
				// New descendants will point to me as a potential target.
				latestBackwardJump = object;
				if (object.slot(IS_SOURCE_OF_CYCLE) == 0)
				{
					// It's not already the source of a cycle.  See if we can
					// find an equivalent ancestor to cycle back to.
					A_BundleTree ancestor = object.slot(LATEST_BACKWARD_JUMP);
					while (!ancestor.equalsNil())
					{
						if (ancestor.allParsingPlansInProgress().equals(
							allPlansInProgress))
						{
							// This ancestor is equivalent to me, so mark me as
							// a backward cyclic link and plug that exact
							// ancestor into the LATEST_BACKWARD_JUMP slot.
							object.setSlot(IS_SOURCE_OF_CYCLE, 1);
							object.setSlot(LATEST_BACKWARD_JUMP, ancestor);
							// The caller will deal with fully expanding the
							// ancestor.
							return;
						}
						ancestor = ancestor.latestBackwardJump();
					}
					// We didn't find a usable ancestor to cycle back to.
					// New successors should link back to me.
					latestBackwardJump = object;
				}
				else
				{
					// It was already the source of a backward link.  We don't
					// need to create any more descendants here.
					return;
				}
			}
			else
			{
				// This bundle tree doesn't have a backward jump, so any new
				// descendants should use the same LATEST_BACKWARD_JUMP as me.
				latestBackwardJump = object.slot(LATEST_BACKWARD_JUMP);
			}

			// Update my components.
			for (final Entry entry : unclassified.mapIterable())
			{
				for (final Entry entry2 : entry.value().mapIterable())
				{
					// final A_Definition definition = entry2.key();
					for (final A_ParsingPlanInProgress planInProgress
						: entry2.value())
					{
						final int pc = planInProgress.parsingPc();
						final A_DefinitionParsingPlan plan =
							planInProgress.parsingPlan();
						final A_Tuple instructions = plan.parsingInstructions();
						if (pc == instructions.tupleSize() + 1)
						{
							// Just reached the end of these instructions.
							// It's past the end of the parsing instructions.
							complete.value =
								complete.value.setWithElementCanDestroy(
									entry.key(), true);
						}
						else
						{
							final long timeBefore = AvailRuntime.captureNanos();
							final int instruction = instructions.tupleIntAt(pc);
							final ParsingOperation op = decode(instruction);
							updateForPlan(
								object,
								plan,
								pc,
								allAncestorModules,
								complete,
								incomplete,
								caseInsensitive,
								actionMap,
								prefilterMap,
								typeFilterPairs);
							final long timeAfter = AvailRuntime.captureNanos();
							final AvailThread thread =
								(AvailThread) Thread.currentThread();
							op.expandingStatisticInNanoseconds.record(
								timeAfter - timeBefore,
								thread.interpreter.interpreterIndex);
						}
					}
				}
			}
			// Write back the updates.
			object.setSlot(LAZY_COMPLETE, complete.value.makeShared());
			object.setSlot(LAZY_INCOMPLETE, incomplete.value.makeShared());
			object.setSlot(
				LAZY_INCOMPLETE_CASE_INSENSITIVE,
				caseInsensitive.value.makeShared());
			object.setSlot(LAZY_ACTIONS, actionMap.value.makeShared());
			object.setSlot(LAZY_PREFILTER_MAP, prefilterMap.value.makeShared());
			object.setSlot(
				LAZY_TYPE_FILTER_PAIRS_TUPLE,
				typeFilterPairs.value.makeShared());
			if (typeFilterPairs.value.tupleSize() != oldTypeFilterSize)
			{
				// Rebuild the type-checking lookup tree.
				final LookupTree<A_Tuple, A_BundleTree, A_BundleTree> tree =
					MessageBundleTreeDescriptor.parserTypeChecker.createRoot(
						toList(typeFilterPairs.value),
						Collections.singletonList(
							ParseNodeKind.PARSE_NODE.mostGeneralType()),
						latestBackwardJump);
				final A_BasicObject pojo = identityPojo(tree);
				object.setSlot(LAZY_TYPE_FILTER_TREE_POJO, pojo.makeShared());
			}
			// Do this volatile write last, to minimize false double-checking in
			// other threads that are also trying to expand this node.
			object.setVolatileSlot(UNCLASSIFIED, emptyMap());
		}
	}

	/**
	 * A {@link A_GrammaticalRestriction} has been added.  Update this bundle
	 * tree and any relevant successors related to the given {@link
	 * A_DefinitionParsingPlan} to agree with the new restriction.
	 */
	@Override
	void o_UpdateForNewGrammaticalRestriction (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress,
		final Collection<Pair<A_BundleTree, A_ParsingPlanInProgress>>
			treesToVisit)
	{
		synchronized (object)
		{
			final A_DefinitionParsingPlan plan = planInProgress.parsingPlan();
			if (object.slot(UNCLASSIFIED).hasKey(plan.bundle()))
			{
				// The plan (or another plan with the same bundle) is still
				// unclassified, so do nothing.
				return;
			}
			final A_Tuple instructions = plan.parsingInstructions();
			final Deque<Integer> pcsToVisit = new ArrayDeque<>();
			pcsToVisit.add(planInProgress.parsingPc());
			while (!pcsToVisit.isEmpty())
			{
				final int pc = pcsToVisit.removeLast();
				if (pc == instructions.tupleSize() + 1)
				{
					// We've reached an end-point for parsing this plan.  The
					// grammatical restriction has no remaining effect.
					return;
				}
				final int instruction = instructions.tupleIntAt(pc);
				final ParsingOperation op = decode(instruction);
				switch (op)
				{
					case JUMP_BACKWARD:
					case JUMP_FORWARD:
					case BRANCH_FORWARD:
					{
						// These should have bubbled out of the bundle tree.
						// Loop to get to the affected successor trees.
						pcsToVisit.addAll(op.successorPcs(instruction, pc));
						break;
					}
					case CHECK_ARGUMENT:
					{
						// This is one of the instructions we're interested in.
						// Let's keep things simple and invalidate this entire
						// bundle tree.
						invalidate(object);
						break;
					}
					case TYPE_CHECK_ARGUMENT:
					{
						// It might be expensive to visit each relevant
						// successor, so just invalidate the entire bundle tree.
						invalidate(object);
						break;
					}
					case PARSE_PART:
					{
						// Look it up in LAZY_INCOMPLETE.
						final int keywordIndex = op.keywordIndex(instruction);
						final A_String keyword =
							plan.bundle().messageParts().tupleAt(keywordIndex);
						final A_BundleTree successor =
							object.slot(LAZY_INCOMPLETE).mapAt(keyword);
						treesToVisit.add(
							new Pair<>(
								successor, newPlanInProgress(plan, pc + 1)));
						break;
					}
					case PARSE_PART_CASE_INSENSITIVELY:
					{
						// Look it up in LAZY_INCOMPLETE_CASE_INSENSITIVE.
						final int keywordIndex = op.keywordIndex(instruction);
						final A_String keyword =
							plan.bundle().messageParts().tupleAt(keywordIndex);
						final A_BundleTree successor =
							object.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE).mapAt(
								keyword);
						treesToVisit.add(
							new Pair<>(
								successor, newPlanInProgress(plan, pc + 1)));
						break;
					}
					default:
					{
						// It's an ordinary action.  JUMPs and BRANCHes were
						// already dealt with in a previous case.
						final A_Tuple successors =
							object.slot(LAZY_ACTIONS).mapAt(
								instructions.tupleAt(pc));
						for (final A_BundleTree successor : successors)
						{
							treesToVisit.add(
								new Pair<>(
									successor,
									newPlanInProgress(plan, pc + 1)));
						}
					}
				}
			}
		}
	}

	private static final Statistic invalidationsStat = new Statistic(
		"(invalidations)", StatisticReport.EXPANDING_PARSING_INSTRUCTIONS);

	/**
	 * Invalidate the internal expansion of the given bundle tree.  Note that
	 * this should only happen when we're changing the grammar in some way,
	 * which happens mutually exclusive of parsing, so we don't really need to
	 * use a lock.
	 */
	private static void invalidate (final AvailObject object)
	{
		final long timeBefore = AvailRuntime.captureNanos();
		synchronized (object)
		{
			object.setSlot(LAZY_COMPLETE, emptySet());
			object.setSlot(LAZY_INCOMPLETE, emptyMap());
			object.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap());
			object.setSlot(LAZY_ACTIONS, emptyMap());
			object.setSlot(LAZY_PREFILTER_MAP, emptyMap());
			object.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, emptyTuple());
			object.setSlot(LAZY_TYPE_FILTER_TREE_POJO, nil);
			object.setSlot(UNCLASSIFIED, object.slot(ALL_PLANS_IN_PROGRESS));
			// Note:  It can't be the target of a cycle any more, since there
			// are no longer *any* successors.
		}
		final long timeAfter = AvailRuntime.captureNanos();
		final AvailThread thread = (AvailThread) Thread.currentThread();
		invalidationsStat.record(
			timeAfter - timeBefore, thread.interpreter.interpreterIndex);
	}


	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		assert isShared();
		int hash = object.slot(HASH_OR_ZERO);
		// The double-check (anti-)pattern is appropriate here, because it's
		// an integer field that transitions once from zero to a non-zero hash
		// value.  If our unprotected read sees a zero, we get the monitor and
		// re-test, setting the hash if necessary.  Otherwise, we've read the
		// non-zero hash that was set once inside some lock in the past, without
		// any synchronization cost.  The usual caveats about reordered writes
		// to non-final fields is irrelevant, since it's just an int field.
		if (hash == 0)
		{
			synchronized (object)
			{
				hash = object.slot(HASH_OR_ZERO);
				if (hash == 0)
				{
					do
					{
						hash = AvailRuntime.nextHash();
					}
					while (hash == 0);
					object.setSlot(HASH_OR_ZERO, hash);
				}
			}
		}
		return hash;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MESSAGE_BUNDLE_TREE.o();
	}

	@Override @AvailMethod
	A_Map o_LazyActions (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_ACTIONS);
		}
	}

	@Override @AvailMethod
	A_Set o_LazyComplete (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_COMPLETE);
		}
	}

	@Override @AvailMethod
	A_Map o_LazyIncomplete (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_INCOMPLETE);
		}
	}

	@Override @AvailMethod
	A_Map o_LazyIncompleteCaseInsensitive (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE);
		}
	}

	@Override @AvailMethod
	A_Map o_LazyPrefilterMap (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_PREFILTER_MAP);
		}
	}
//	lazyTypeFilterPairs
	@Override @AvailMethod
	A_BasicObject o_LazyTypeFilterTreePojo (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_TYPE_FILTER_TREE_POJO);
		}
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Never actually make a message bundle tree immutable. They are
			// always shared.
			return object.makeShared();
		}
		return object;
	}

	/**
	 * Remove the plan from this bundle tree.  We don't need to remove the
	 * bundle itself if this is the last plan for that bundle, since this can
	 * only be called when satisfying a forward declaration – by adding another
	 * definition.
	 */
	@Override
	void o_RemovePlanInProgress (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress)
	{
		synchronized (object)
		{
			object.setSlot(
				ALL_PLANS_IN_PROGRESS,
				layeredMapWithoutPlan(
					object.slot(ALL_PLANS_IN_PROGRESS), planInProgress));
			object.setSlot(
				UNCLASSIFIED,
				layeredMapWithoutPlan(
					object.slot(UNCLASSIFIED), planInProgress));
		}
	}

	/**
	 * Answer the nearest ancestor that was known at some time to have a
	 * backward jump in at least one of its parsing-plans-in-progress.
	 *
	 * @param object The bundle tree.
	 * @return A predecessor bundle tree or nil.
	 */
	@Override
	A_BundleTree o_LatestBackwardJump (final AvailObject object)
	{
		return object.slot(LATEST_BACKWARD_JUMP);
	}

	@Override
	boolean o_HasBackwardJump (final AvailObject object)
	{
		return object.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0;
	}

	@Override
	boolean o_IsSourceOfCycle (final AvailObject object)
	{
		return object.slot(IS_SOURCE_OF_CYCLE) != 0;
	}

	@Override
	void o_IsSourceOfCycle (
		final AvailObject object,
		final boolean isSourceOfCycle)
	{
		object.setSlot(IS_SOURCE_OF_CYCLE, isSourceOfCycle ? 1 : 0);
	}

	/**
	 * Remove the {@link A_ParsingPlanInProgress} from the given map.  The map
	 * is from {@link A_Bundle} to a submap, which is from {@link A_Definition}
	 * to a set of {@link A_ParsingPlanInProgress plans-in-progress} for that
	 * bundle/definition pair.
	 *
	 * @param outerMap {bundle → {definition → {plan-in-progress |}|}|}
	 * @param planInProgress The {@link A_ParsingPlanInProgress} to remove.
	 */
	private static A_Map layeredMapWithoutPlan (
		final A_Map outerMap,
		final A_ParsingPlanInProgress planInProgress)
	{
		final A_DefinitionParsingPlan plan = planInProgress.parsingPlan();
		final A_Bundle bundle = plan.bundle();
		final A_Definition definition = plan.definition();
		if (!outerMap.hasKey(bundle))
		{
			return outerMap;
		}
		A_Map submap = outerMap.mapAt(bundle);
		if (!submap.hasKey(definition))
		{
			return outerMap;
		}
		A_Set inProgressSet = submap.mapAt(definition);
		if (!inProgressSet.hasElement(planInProgress))
		{
			return outerMap;
		}
		inProgressSet = inProgressSet.setWithoutElementCanDestroy(
			planInProgress, true);
		submap = inProgressSet.setSize() > 0
			? submap.mapAtPuttingCanDestroy(definition, inProgressSet, true)
			: submap.mapWithoutKeyCanDestroy(definition, true);
		final A_Map newOuterMap = submap.mapSize() > 0
			? outerMap.mapAtPuttingCanDestroy(bundle, submap, true)
			: outerMap.mapWithoutKeyCanDestroy(bundle, true);
		return newOuterMap.makeShared();
	}

	/**
	 * Categorize a single message/bundle pair.
	 *
	 * @param bundleTree
	 *        The {@link A_BundleTree} that we're updating.  The state is passed
	 *        separately in arguments, to be written back after all the mutable
	 *        arguments have been updated for all parsing-plans-in-progress.
	 * @param plan
	 *        The {@link A_DefinitionParsingPlan} to categorize.
	 * @param pc
	 *        The one-based program counter that indexes each applicable
	 *        {@link A_DefinitionParsingPlan}'s {@link A_DefinitionParsingPlan
	 *        #parsingInstructions()}.  Note that this value can be one past the
	 *        end of the instructions, indicating parsing is complete.
	 * @param allAncestorModules
	 *        The {@link A_Set} of modules that are ancestors of (or equal to)
	 *        the current module being parsed.  This is used to restrict the
	 *        visibility of semantic and grammatical restrictions, as well as
	 *        which method and macro {@link A_Definition}s can be parsed.
	 * @param incomplete
	 *        A {@link Mutable} {@link A_Map} from {@link A_String} to successor
	 *        {@link A_BundleTree}.  If a token's string matches one of the
	 *        keys, it will be consumed and the successor bundle tree will be
	 *        visited.
	 * @param caseInsensitive
	 *        A {@link Mutable} {@link A_Map} from lower-case {@link A_String}
	 *        to successor {@link A_BundleTree}.  If the lower-case version of
	 *        a token's string matches on of the keys, it will be consumed and
	 *        the successor bundle tree will be visited.
	 * @param actionMap
	 *        A {@link Mutable} {@link A_Map} from an Avail integer encoding a
	 *        {@link ParsingOperation} to a {@link A_Tuple} of successor
	 *        {@link A_BundleTree}s.  Typically there is only one successor
	 *        bundle tree, but some parsing operations require that the tree
	 *        diverge into multiple successors.
	 * @param prefilterMap
	 *        A {@link Mutable} {@link A_Map} from {@link A_Bundle} to a
	 *        successor {@link A_BundleTree}.  If the most recently parsed
	 *        argument phrase is a send phrase and its bundle is a key in this
	 *        map, the successor will be explored, but not the sole action in
	 *        the actionMap (which contains at most one entry, a {@link
	 *        ParsingOperation#CHECK_ARGUMENT}.  If the bundle is not present,
	 *        or if the latest argument is not a send phrase, allow the
	 *        associated action(s) to be followed instead.  This accomplishes
	 *        grammatical restriction, assuming this method populates the
	 *        successor bundle trees correctly.
	 * @param typeFilterTuples
	 *        A {@link Mutable} {@link A_Tuple} of pairs (2-tuples) from {@link
	 *        A_Phrase phrase} {@link A_Type type} to {@link
	 *        A_DefinitionParsingPlan}.
	 */
	private static void updateForPlan (
		final AvailObject bundleTree,
		final A_DefinitionParsingPlan plan,
		final int pc,
		final A_Set allAncestorModules,
		final Mutable<A_Set> complete,
		final Mutable<A_Map> incomplete,
		final Mutable<A_Map> caseInsensitive,
		final Mutable<A_Map> actionMap,
		final Mutable<A_Map> prefilterMap,
		final Mutable<A_Tuple> typeFilterTuples)
	{
		final boolean hasBackwardJump =
			bundleTree.slot(HAS_BACKWARD_JUMP_INSTRUCTION) != 0;
		final A_BundleTree latestBackwardJump =
			hasBackwardJump
				? bundleTree
				: bundleTree.slot(LATEST_BACKWARD_JUMP);
		final A_Tuple instructions = plan.parsingInstructions();
		if (pc == instructions.tupleSize() + 1)
		{
			complete.value =
				complete.value.setWithElementCanDestroy(plan.bundle(), true);
			return;
		}
		final int instruction = plan.parsingInstructions().tupleIntAt(pc);
		final ParsingOperation op = decode(instruction);
		switch (op)
		{
			case JUMP_BACKWARD:
			{
				if (!hasBackwardJump)
				{
					// We just discovered the first backward jump in any
					// parsing-plan-in-progress at this node.
					bundleTree.setSlot(HAS_BACKWARD_JUMP_INSTRUCTION, 1);
				}
			}
			// Falls through.
			case JUMP_FORWARD:
			case BRANCH_FORWARD:
			{
				// Bubble control flow right out of the bundle trees.  There
				// should never be a JUMP or BRANCH in an actionMap.  Rather,
				// the successor instructions (recursively in the case of jumps
				// to other jumps) are directly exploded into the current bundle
				// tree.  Not only does this save the cost of dispatching these
				// control flow operations, but it also allows more potential
				// matches for the next token to be undertaken in a single
				// lookup.
				// We can safely recurse here, because plans cannot have any
				// empty loops due to progress check instructions.
				for (final int nextPc : op.successorPcs(instruction, pc))
				{
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
						typeFilterTuples);
				}
				return;
			}
			case PARSE_PART:
			case PARSE_PART_CASE_INSENSITIVELY:
			{
				// Parse a specific keyword, or case-insensitive keyword.
				final int keywordIndex = op.keywordIndex(instruction);
				final A_String part =
					plan.bundle().messageParts().tupleAt(keywordIndex);
				final Mutable<A_Map> map =
					op == PARSE_PART ? incomplete : caseInsensitive;
				final A_BundleTree subtree;
				if (map.value.hasKey(part))
				{
					subtree = map.value.mapAt(part);
				}
				else
				{
					subtree = newBundleTree(latestBackwardJump);
					map.value = map.value.mapAtPuttingCanDestroy(
						part, subtree, true);
				}
				subtree.addPlanInProgress(newPlanInProgress(plan, pc + 1));
				return;
			}
			case PREPARE_TO_RUN_PREFIX_FUNCTION:
			{
				// Each macro definition has its own prefix functions, so for
				// each plan create a separate successor message bundle tree.
				final A_BundleTree newTarget =
					newBundleTree(latestBackwardJump);
				newTarget.addPlanInProgress(newPlanInProgress(plan, pc + 1));
				final A_Number instructionObject = fromInt(instruction);
				A_Tuple successors = actionMap.value.hasKey(instructionObject)
					? actionMap.value.mapAt(instructionObject)
					: emptyTuple();
				successors = successors.appendCanDestroy(newTarget, true);
				actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
					instructionObject, successors, true);
				// We added it to the actions, so don't fall through.
				return;
			}
			case TYPE_CHECK_ARGUMENT:
			{
				// An argument was just parsed and passed its grammatical
				// restriction check.  Now it needs to do a type check with a
				// type-dispatch tree.
				final int typeIndex = op.typeCheckArgumentIndex(instruction);
				final A_Type phraseType =
					MessageSplitter.constantForIndex(typeIndex);
				final A_ParsingPlanInProgress planInProgress =
					newPlanInProgress(plan, pc + 1);
				final A_Tuple pair = tuple(phraseType, planInProgress);
				typeFilterTuples.value =
					typeFilterTuples.value.appendCanDestroy(pair, true);
				return;
			}
			case CHECK_ARGUMENT:
			{
				// It's a checkArgument instruction.
				final int checkArgumentIndex =
					op.checkArgumentIndex(instruction);
				// Add it to the action map.
				final A_BundleTree successor;
				final A_Number instructionObject = fromInt(instruction);
				if (actionMap.value.hasKey(instructionObject))
				{
					final A_Tuple successors =
						actionMap.value.mapAt(instructionObject);
					assert successors.tupleSize() == 1;
					successor = successors.tupleAt(1);
				}
				else
				{
					successor = newBundleTree(latestBackwardJump);
					actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
						instructionObject, tuple(successor), true);
				}
				A_Set forbiddenBundles = emptySet();
				for (final A_GrammaticalRestriction restriction
					: plan.bundle().grammaticalRestrictions())
				{
					// Exclude grammatical restrictions that aren't defined in
					// an ancestor module.
					final A_Module definitionModule =
						restriction.definitionModule();
					if (definitionModule.equalsNil()
						|| allAncestorModules.hasElement(definitionModule))
					{
						final A_Set bundles =
							restriction.argumentRestrictionSets().tupleAt(
								checkArgumentIndex);
						forbiddenBundles = forbiddenBundles.setUnionCanDestroy(
							bundles, true);
					}
				}
				final A_ParsingPlanInProgress planInProgress =
					newPlanInProgress(plan, pc + 1);
				// Add it to every existing branch where it's permitted.
				for (final Entry prefilterEntry
					: prefilterMap.value.mapIterable())
				{
					if (!forbiddenBundles.hasElement(prefilterEntry.key()))
					{
						final A_BundleTree nextTree = prefilterEntry.value();
						nextTree.addPlanInProgress(planInProgress);
					}
				}
				// Add branches for any new restrictions.  Pre-populate
				// with every bundle present thus far, since none of
				// them had this restriction.
				for (final A_Bundle restrictedBundle : forbiddenBundles)
				{
					if (!prefilterMap.value.hasKey(restrictedBundle))
					{
						final AvailObject newTarget =
							newBundleTree(latestBackwardJump);
						// Be careful.  We can't add ALL_BUNDLES, since
						// it may contain some bundles that are still
						// UNCLASSIFIED.  Instead, use ALL_BUNDLES of
						// the successor found under this instruction,
						// since it *has* been kept up to date as the
						// bundles have gotten classified.
						for (final Entry existingEntry :
							successor.allParsingPlansInProgress().mapIterable())
						{
							for (final Entry planEntry
								: existingEntry.value().mapIterable())
							{
								for (final A_ParsingPlanInProgress inProgress
									: planEntry.value())
								{
									newTarget.addPlanInProgress(inProgress);
								}
							}
						}
						prefilterMap.value =
							prefilterMap.value.mapAtPuttingCanDestroy(
								restrictedBundle, newTarget, true);
					}
				}
				// Finally, add it to the action map.  This had to be
				// postponed, since we didn't want to add it under any
				// new restrictions, and the actionMap is what gets
				// visited to populate new restrictions.
				successor.addPlanInProgress(planInProgress);

				// Note:  Fall through here, since the action also has to be
				// added to the actionMap (to deal with the case that a
				// subexpression is a non-send, or a send that is not forbidden
				// in this position by *any* potential parent sends.
			}
			// FALL-THROUGH.
			default:
			{
				// It's not a keyword parsing instruction or a type-check or
				// preparation for a prefix function, so it's an ordinary
				// parsing instruction.  It might be a CHECK_ARGUMENT that has
				// already updated the prefilterMap and fallen through.
				// Control flow instructions should have been dealt with in a
				// prior case.
				final List<Integer> nextPcs = op.successorPcs(instruction, pc);
				assert nextPcs.size() == 1 && nextPcs.get(0) == pc + 1;
				final A_BundleTree successor;
				final A_Number instructionObject = fromInt(instruction);
				if (actionMap.value.hasKey(instructionObject))
				{
					final A_Tuple successors =
						actionMap.value.mapAt(instructionObject);
					assert successors.tupleSize() == 1;
					successor = successors.tupleAt(1);
				}
				else
				{
					successor = newBundleTree(latestBackwardJump);
					final A_Tuple successors = tuple(successor);
					actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
						instructionObject, successors, true);
				}
				successor.addPlanInProgress(newPlanInProgress(plan, pc + 1));
			}
		}
	}

	/**
	 * Create a new empty {@link A_BundleTree}.
	 *
	 * @param latestBackwardJump
	 *        The nearest ancestor bundle tree that was known at some point to
	 *        contain a backward jump instruction, or nil if there were no such
	 *        ancestors.
	 * @return A new empty message bundle tree.
	 */
	public static AvailObject newBundleTree (
		final A_BundleTree latestBackwardJump)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(ALL_PLANS_IN_PROGRESS, emptyMap());
		result.setSlot(UNCLASSIFIED, emptyMap());
		result.setSlot(LAZY_COMPLETE, emptySet());
		result.setSlot(LAZY_INCOMPLETE, emptyMap());
		result.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap());
		result.setSlot(LAZY_ACTIONS, emptyMap());
		result.setSlot(LAZY_PREFILTER_MAP, emptyMap());
		result.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, emptyTuple());
		result.setSlot(LAZY_TYPE_FILTER_TREE_POJO, nil);
		result.setSlot(LATEST_BACKWARD_JUMP, latestBackwardJump);
		return result.makeShared();
	}

	/**
	 * Construct a new {@code MessageBundleTreeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MessageBundleTreeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.BUNDLE_TREE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link MessageBundleTreeDescriptor}. */
	private static final MessageBundleTreeDescriptor mutable =
		new MessageBundleTreeDescriptor(Mutability.MUTABLE);

	@Override
	MessageBundleTreeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MessageBundleTreeDescriptor}. */
	private static final MessageBundleTreeDescriptor shared =
		new MessageBundleTreeDescriptor(Mutability.SHARED);

	@Override
	MessageBundleTreeDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	MessageBundleTreeDescriptor shared ()
	{
		return shared;
	}
}
