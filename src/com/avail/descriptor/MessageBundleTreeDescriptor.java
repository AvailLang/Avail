/**
 * MessageBundleTreeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.descriptor.MessageBundleTreeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.MessageBundleTreeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE_TREE;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeAdaptor;
import com.avail.dispatch.TypeComparison;
import com.avail.utility.*;

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
public class MessageBundleTreeDescriptor
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
		@HideFieldInDebugger
		HASH_AND_PARSING_PC;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		static final BitField HASH_OR_ZERO = bitField(
			HASH_AND_PARSING_PC, 0, 32);

		/**
		 * The subscript into the {@linkplain TupleDescriptor tuple} of encoded
		 * parsing instructions. These instructions are produced by the {@link
		 * MessageSplitter} as a way to interpret the tokens, underscores, and
		 * guillemet expressions of a method name. There may be multiple
		 * potential method invocations being parsed <em>together</em> at this
		 * position in the message bundle tree, but the parsing instructions
		 * that have been encountered so far along this history must be the same
		 * for each message – otherwise the message bundle tree would have
		 * diverged into multiple subtrees.
		 */
		static final BitField PARSING_PC = bitField(
			HASH_AND_PARSING_PC, 32, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain MapDescriptor map} from {@link A_Bundle}s to maps,
		 * which are themselves from {@link A_Definition definitions} to the
		 * {@link A_DefinitionParsingPlan parsing plans} for that definition and
		 * bundle.  Note that the inner maps may be empty in the case that a
		 * grammatical restriction has been defined before any visible
		 * definitions.  This doesn't affect parsing, but makes the logic
		 * easier about deciding which grammatical restrictions are visible when
		 * adding a definition later.
		 */
		ALL_PLANS,

		/**
		 * A {@linkplain MapDescriptor map} from visible {@linkplain A_Bundle
		 * bundles} to maps from {@link A_Definition definitions} to the
		 * {@link A_DefinitionParsingPlan parsing plans} for that definition
		 * and bundle.  It has the same content as ALL_PLANS until these items
		 * have been categorized as complete, incomplete, action, or prefilter.
		 * They are categorized if and when this message bundle tree is reached
		 * during parsing.
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
		 * strings} to successor {@linkplain MessageBundleTreeDescriptor message
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
		 * This is a map from instruction (an {@linkplain IntegerDescriptor
		 * integer}) to the (non-empty) tuple of {@linkplain
		 * MessageBundleTreeDescriptor message bundle trees} to attempt if the
		 * instruction succeeds.  Attempt each possible instruction, proceeding
		 * to each of the successor trees if the instruction succeeds.
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
		 * instruction is looked up normally in the {@link #LAZY_ACTIONS}
		 * map.</p>
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
		 * {@link #LAZY_PREFILTER_MAP} is populated. It maps from interesting
		 * {@linkplain MessageBundleDescriptor message bundles} that might occur
		 * as an argument to an appropriately reduced {@linkplain
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
		 * A_DefinitionParsingPlan definition parsing plan}.  These should stay
		 * synchronized with the {@link #LAZY_TYPE_FILTER_TREE_POJO} field.
		 */
		LAZY_TYPE_FILTER_PAIRS_TUPLE,

		/**
		 * A {@link RawPojoDescriptor raw pojo} containing a type-dispatch tree
		 * for handling the case that at least one {@link
		 * A_DefinitionParsingPlan parsing plan} in this message bundle tree is
		 * at a {@link IntegerSlots#PARSING_PC} pointing to a {@link
		 * ParsingOperation#TYPE_CHECK_ARGUMENT} operation.  This allows
		 * relatively efficient elimination of inappropriately typed arguments
		 */
		LAZY_TYPE_FILTER_TREE_POJO;
	}

	/**
	 * This is the {@link LookupTreeAdaptor} for building and navigating the
	 * {@link ObjectSlots#LAZY_TYPE_FILTER_TREE_POJO}.  It gets built from
	 * 2-tuples containing a {@link ParseNodeTypeDescriptor phrase type} and a
	 * corresponding {@link A_DefinitionParsingPlan}.  The type is used to
	 * perform type filtering after parsing each leaf argument, and the phrase
	 * type is the expected type of that latest argument.
	 */
	public final static LookupTreeAdaptor<A_Tuple, A_BundleTree, Integer>
		parserTypeChecker =
			new LookupTreeAdaptor<A_Tuple, A_BundleTree, Integer>()
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
			final Integer parsingPc)
		{
			final A_BundleTree newBundleTree =
				MessageBundleTreeDescriptor.newPc(parsingPc);
			for (A_Tuple pair : elements)
			{
				final A_DefinitionParsingPlan plan = pair.tupleAt(2);
				newBundleTree.addPlan(plan);
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
		return e == HASH_AND_PARSING_PC
			|| e == ALL_PLANS
			|| e == UNCLASSIFIED
			|| e == LAZY_COMPLETE
			|| e == LAZY_INCOMPLETE
			|| e == LAZY_INCOMPLETE_CASE_INSENSITIVE
			|| e == LAZY_ACTIONS
			|| e == LAZY_PREFILTER_MAP
			|| e == LAZY_TYPE_FILTER_PAIRS_TUPLE
			|| e == LAZY_TYPE_FILTER_TREE_POJO;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("BundleTree[pc=");
		builder.append(object.slot(PARSING_PC));
		builder.append("](");
		final A_Map allPlans = object.slot(ALL_PLANS);
		final int bundleCount = allPlans.mapSize();
		if (bundleCount <= 15)
		{
			boolean first = true;
			for (final MapDescriptor.Entry entry : allPlans.mapIterable())
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
				final A_Bundle bundle = entry.key();
				builder.append(bundle.message().atomName());
				builder.append(" (");
				builder.append(entry.value().mapSize());
				builder.append(" definitions)");
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
	 * Add the plan to this bundle tree.  The corresponding bundle must already
	 * be present.
	 */
	@Override
	void o_AddPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		synchronized (object)
		{
			final A_Bundle bundle = plan.bundle();
			final A_Definition definition = plan.definition();
			A_Map allPlans = object.slot(ALL_PLANS);
			A_Map submap = allPlans.hasKey(bundle)
				? allPlans.mapAt(bundle)
				: MapDescriptor.empty();
			submap = submap.mapAtPuttingCanDestroy(definition, plan, true);
			allPlans = allPlans.mapAtPuttingCanDestroy(bundle, submap, true);
			object.setSlot(ALL_PLANS, allPlans.makeShared());
			// And add it to unclassified.
			A_Map unclassified = object.slot(UNCLASSIFIED);
			A_Map unclassifiedSubmap = unclassified.hasKey(bundle)
				? unclassified.mapAt(bundle)
				: MapDescriptor.empty();
			unclassifiedSubmap = unclassifiedSubmap.mapAtPuttingCanDestroy(
				definition, plan, true);
			unclassified = unclassified.mapAtPuttingCanDestroy(
				bundle, unclassifiedSubmap, true);
			object.setSlot(UNCLASSIFIED, unclassified.makeShared());
		}
	}

	@Override
	A_Map o_AllParsingPlans (final AvailObject object)
	{
		return object.slot(ALL_PLANS);
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
		synchronized (object)
		{
			final A_Map unclassified = object.slot(UNCLASSIFIED);
			if (unclassified.mapSize() == 0)
			{
				return;
			}
			final Mutable<A_Set> complete = new Mutable<A_Set>(
				object.slot(LAZY_COMPLETE));
			final Mutable<A_Map> incomplete = new Mutable<A_Map>(
				object.slot(LAZY_INCOMPLETE));
			final Mutable<A_Map> caseInsensitive = new Mutable<A_Map>(
				object.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE));
			final Mutable<A_Map> actionMap = new Mutable<A_Map>(
				object.slot(LAZY_ACTIONS));
			final Mutable<A_Map> prefilterMap = new Mutable<A_Map>(
				object.slot(LAZY_PREFILTER_MAP));
			final Mutable<A_Tuple> typeFilterPairs = new Mutable<A_Tuple>(
				object.slot(LAZY_TYPE_FILTER_PAIRS_TUPLE));
			final int oldTypeFilterSize = typeFilterPairs.value.tupleSize();
			final int pc = object.slot(PARSING_PC);
			// Fail fast if someone messes with this during iteration.
			object.setSlot(UNCLASSIFIED, NilDescriptor.nil());
			final A_Set allAncestorModules = module.allAncestors();
			for (final MapDescriptor.Entry entry : unclassified.mapIterable())
			{
				for (final MapDescriptor.Entry entry2
					: entry.value().mapIterable())
				{
					updateForPlan(
						entry2.value(),
						allAncestorModules,
						complete,
						incomplete,
						caseInsensitive,
						actionMap,
						prefilterMap,
						typeFilterPairs,
						pc);
				}
			}
			object.setSlot(UNCLASSIFIED, MapDescriptor.empty());
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
				LookupTree<A_Tuple, A_BundleTree, Integer> tree =
					MessageBundleTreeDescriptor.parserTypeChecker.createRoot(
						TupleDescriptor.<A_Tuple>toList(typeFilterPairs.value),
						Collections.singletonList(
							ParseNodeKind.PARSE_NODE.mostGeneralType()),
						pc + 1);
				final A_BasicObject pojo = RawPojoDescriptor.identityWrap(tree);
				object.setSlot(LAZY_TYPE_FILTER_TREE_POJO, pojo.makeShared());
			}
		}
	}

	/**
	 * Update information in this {@linkplain MessageBundleTreeDescriptor
	 * message bundle tree} to reflect the latest information about the
	 * {@linkplain MessageBundleDescriptor message bundle} with the given name.
	 *
	 * <p>The bundle may have been added, or updated (but not removed), so if
	 * the bundle has already been classified in this tree (via {@link
	 * #o_Expand(AvailObject, A_Module)}), we may need to invalidate some
	 * of the tree's lazy structures.</p>
	 */
	@Override
	void o_FlushForNewOrChangedBundle (
		final AvailObject object,
		final A_Bundle bundle)
	{
		final A_Map allPlans = object.slot(ALL_PLANS);
		if (object.slot(UNCLASSIFIED).hasKey(bundle))
		{
			// It hasn't been classified yet, so there's nothing to clean up.
			return;
		}
		// It has been classified already, so flush all lazy structures
		// for safety, moving everything back to unclassified.
		final A_Map emptyMap = MapDescriptor.empty();
		object.setSlot(LAZY_COMPLETE, SetDescriptor.empty());
		object.setSlot(LAZY_INCOMPLETE, emptyMap);
		object.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap);
		object.setSlot(LAZY_ACTIONS, emptyMap);
		object.setSlot(LAZY_PREFILTER_MAP, emptyMap);
		object.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, TupleDescriptor.empty());
		object.setSlot(LAZY_TYPE_FILTER_TREE_POJO, NilDescriptor.nil());
		object.setSlot(UNCLASSIFIED, allPlans.makeShared());
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

	@Override
	int o_ParsingPc (final AvailObject object)
	{
		return object.slot(PARSING_PC);
	}

	/**
	 * Remove the plan from this bundle tree.  ALso remove the bundle if this
	 * was the last plan.
	 */
	@Override
	void o_RemovePlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		synchronized (object)
		{
			final A_Bundle bundle = plan.bundle();
			final A_Definition definition = plan.definition();
			A_Map allPlans = object.slot(ALL_PLANS);
			assert allPlans.hasKey(bundle);
			A_Map submap = allPlans.mapAt(bundle);
			submap = submap.mapWithoutKeyCanDestroy(definition, true);
			if (submap.mapSize() == 0)
			{
				allPlans = allPlans.mapWithoutKeyCanDestroy(bundle, true);
			}
			else
			{
				allPlans = allPlans.mapAtPuttingCanDestroy(
					bundle, submap, true);
			}
			object.setSlot(ALL_PLANS, allPlans.makeShared());
			// And remove it from unclassified.
			A_Map unclassified = object.slot(UNCLASSIFIED);
			if (unclassified.hasKey(bundle))
			{
				A_Map unclassifiedSubmap = unclassified.mapAt(bundle);
				unclassifiedSubmap = unclassifiedSubmap.mapWithoutKeyCanDestroy(
					definition, true);
				if (unclassifiedSubmap.mapSize() == 0)
				{
					unclassified = unclassified.mapWithoutKeyCanDestroy(
						bundle, true);
				}
				else
				{
					unclassified = unclassified.mapAtPuttingCanDestroy(
						bundle, unclassifiedSubmap, true);
				}
				object.setSlot(UNCLASSIFIED, unclassified.makeShared());
			}
		}
	}

	/**
	 * Categorize a single message/bundle pair.
	 *
	 * @param plan
	 *        The {@link A_DefinitionParsingPlan} to categorize.
	 * @param allAncestorModules
	 *        The {@link A_Set} of modules that are ancestors of (or equal to)
	 *        the current module being parsed.  This is used to restrict the
	 *        visibility of semantic and grammatical restrictions, as well as
	 *        which method and macro {@link A_Definition}s can be parsed.
	 * @param complete
	 *        A {@link Mutable} {@link A_Set} of method sends that have been
	 *        completely parsed at this point in the {@link A_BundleTree}.
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
	 * @param pc
	 *        The one-based program counter that indexes each applicable
	 *        {@link A_DefinitionParsingPlan}'s {@link A_DefinitionParsingPlan
	 *        #parsingInstructions()}.
	 */
	private static void updateForPlan (
		final A_DefinitionParsingPlan plan,
		final A_Set allAncestorModules,
		final Mutable<A_Set> complete,
		final Mutable<A_Map> incomplete,
		final Mutable<A_Map> caseInsensitive,
		final Mutable<A_Map> actionMap,
		final Mutable<A_Map> prefilterMap,
		final Mutable<A_Tuple> typeFilterTuples,
		final int pc)
	{
		final A_Bundle bundle = plan.bundle();
		final A_Method method = bundle.bundleMethod();
		if (method.definitionsTuple().tupleSize() == 0
			&& method.macroDefinitionsTuple().tupleSize() == 0)
		{
			// There are no method or macro definitions, so there's no reason
			// to try to parse it.  This can happen when grammatical
			// restrictions are defined before a method is implemented, a
			// reasonably common situation.
			return;
		}
		final A_Tuple instructions = plan.parsingInstructions();
		if (pc == instructions.tupleSize() + 1)
		{
			// It's past the end of the parsing instructions.
			complete.value = complete.value.setWithElementCanDestroy(
				bundle, true);
			return;
		}
		final int instruction = instructions.tupleIntAt(pc);
		final ParsingOperation op = ParsingOperation.decode(instruction);
		switch (op)
		{
			case PARSE_PART:
			case PARSE_PART_CASE_INSENSITIVELY:
			{
				// Parse a specific keyword, or case-insensitive keyword.
				final int keywordIndex = op.keywordIndex(instruction);
				A_BundleTree subtree;
				final A_String part =
					bundle.messageParts().tupleAt(keywordIndex);
				final Mutable<A_Map> map =
					op == PARSE_PART ? incomplete : caseInsensitive;
				if (map.value.hasKey(part))
				{
					subtree = map.value.mapAt(part);
				}
				else
				{
					subtree = newPc(pc + 1);
					map.value = map.value.mapAtPuttingCanDestroy(
						part, subtree, true);
				}
				subtree.addPlan(plan);
				return;
			}
			case PREPARE_TO_RUN_PREFIX_FUNCTION:
			{
				// Each macro definition has its own prefix functions, so for
				// each plan create a separate successor message bundle tree.
				final A_BundleTree newTarget = newPc(pc + 1);
				newTarget.addPlan(plan);
				final A_Number instructionObject =
					IntegerDescriptor.fromInt(instruction);
				A_Tuple successors = actionMap.value.hasKey(instructionObject)
					? actionMap.value.mapAt(instructionObject)
					: TupleDescriptor.empty();
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
					MessageSplitter.typeToCheck(typeIndex);
				final A_Tuple pair = TupleDescriptor.from(phraseType, plan);
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
				final A_Number instructionObject =
					IntegerDescriptor.fromInt(instruction);
				if (actionMap.value.hasKey(instructionObject))
				{
					final A_Tuple successors =
						actionMap.value.mapAt(instructionObject);
					assert successors.tupleSize() == 1;
					successor = successors.tupleAt(1);
				}
				else
				{
					successor = newPc(pc + 1);
					actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
						instructionObject,
						TupleDescriptor.from(successor),
						true);
				}
				A_Set forbiddenBundles = SetDescriptor.empty();
				for (final A_GrammaticalRestriction restriction
					: bundle.grammaticalRestrictions())
				{
					// Exclude grammatical restrictions that aren't defined in
					// an ancestor module.
					if (allAncestorModules.hasElement(
						restriction.definitionModule()))
					{
						final A_Set bundles =
							restriction.argumentRestrictionSets().tupleAt(
								checkArgumentIndex);
						forbiddenBundles = forbiddenBundles.setUnionCanDestroy(
							bundles, true);
					}
				}
				// Add it to every existing branch where it's permitted.
				for (final MapDescriptor.Entry prefilterEntry
					: prefilterMap.value.mapIterable())
				{
					if (!forbiddenBundles.hasElement(prefilterEntry.key()))
					{
						final A_BundleTree nextTree = prefilterEntry.value();
						nextTree.addPlan(plan);
					}
				}
				// Add branches for any new restrictions.  Pre-populate
				// with every bundle present thus far, since none of
				// them had this restriction.
				for (final A_Bundle restrictedBundle : forbiddenBundles)
				{
					if (!prefilterMap.value.hasKey(restrictedBundle))
					{
						final AvailObject newTarget = newPc(pc + 1);
						// Be careful.  We can't add ALL_BUNDLES, since
						// it may contain some bundles that are still
						// UNCLASSIFIED.  Instead, use ALL_BUNDLES of
						// the successor found under this instruction,
						// since it *has* been kept up to date as the
						// bundles have gotten classified.
						for (final MapDescriptor.Entry existingEntry
							: successor.allParsingPlans().mapIterable())
						{
							for (final MapDescriptor.Entry planEntry
								: existingEntry.value().mapIterable())
							{
								newTarget.addPlan(planEntry.value());
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
				successor.addPlan(plan);

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
				final A_Tuple successors;
				final A_Number instructionObject =
					IntegerDescriptor.fromInt(instruction);
				if (actionMap.value.hasKey(instructionObject))
				{
					successors = actionMap.value.mapAt(instructionObject);
				}
				else
				{
					final List<Integer> nextPcs = op.successorPcs(
						instruction,
						pc);
					final List<A_BundleTree> successorsList =
						new ArrayList<>(nextPcs.size());
					for (final int nextPc : nextPcs)
					{
						successorsList.add(newPc(nextPc));
					}
					successors = TupleDescriptor.fromList(successorsList);
					actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
						instructionObject, successors, true);
				}
				for (final A_BundleTree successor : successors)
				{
					successor.addPlan(plan);
				}
			}
		}
	}

	/**
	 * Create a new empty message bundle tree.
	 *
	 * @param pc A common index into each eligible message's instructions.
	 * @return The new unexpanded, empty message bundle tree.
	 */
	static AvailObject newPc (final int pc)
	{
		final AvailObject result = mutable.create();
		final A_Map emptyMap = MapDescriptor.empty();
		final A_Set emptySet = SetDescriptor.empty();
		result.setSlot(PARSING_PC, pc);
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(ALL_PLANS, emptyMap);
		result.setSlot(UNCLASSIFIED, emptyMap);
		result.setSlot(LAZY_COMPLETE, emptySet);
		result.setSlot(LAZY_INCOMPLETE, emptyMap);
		result.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap);
		result.setSlot(LAZY_ACTIONS, emptyMap);
		result.setSlot(LAZY_PREFILTER_MAP, emptyMap);
		result.setSlot(LAZY_TYPE_FILTER_PAIRS_TUPLE, TupleDescriptor.empty());
		result.setSlot(LAZY_TYPE_FILTER_TREE_POJO, NilDescriptor.nil());
		result.makeShared();
		return result;
	}

	/**
	 * Construct a new {@link MessageBundleTreeDescriptor}.
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
