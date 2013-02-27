/**
 * MessageBundleTreeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.exceptions.SignatureException;
import com.avail.utility.Mutable;

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
		PARSING_PC,

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing. In particular, any
		 * {@linkplain MessageBundleTreeDescriptor message bundle tree}
		 * represents the current state of collective parsing of an invocation
		 * of one or more thus far equivalent methods. This is the collection
		 * of such methods.
		 */
		ALL_BUNDLES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing. In particular,
		 * these are methods that have not yet been categorized as complete,
		 * incomplete, action, or prefilter. They are categorized if and when
		 * this message bundle tree is reached during parsing.
		 */
		UNCLASSIFIED,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing. In particular,
		 * these are methods for which an invocation has just been completely
		 * parsed.
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
		 * effort parsing unnecessary expressions. For example, the "_*_"
		 * operation might not allow a "_+_" call for its left or right
		 * arguments, so parsing "1+2*…" as "(1+2)*…" is wasted effort.
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
		 * {@linkplain AtomDescriptor atoms} naming methods that might occur as
		 * an argument to an appropriately reduced {@linkplain
		 * MessageBundleTreeDescriptor message bundle tree} (i.e., a message
		 * bundle tree containing precisely those method bundles that allow that
		 * argument. The only keys that occur are ones for which at least one
		 * restriction exists in at least one of the still possible {@linkplain
		 * MessageBundleDescriptor message bundles}. When {@link #UNCLASSIFIED}
		 * is empty, <em>all</em> such restricted argument message names occur
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
		LAZY_PREFILTER_MAP
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == HASH_OR_ZERO
			|| e == LAZY_COMPLETE
			|| e == LAZY_INCOMPLETE
			|| e == LAZY_INCOMPLETE_CASE_INSENSITIVE
			|| e == LAZY_ACTIONS
			|| e == LAZY_PREFILTER_MAP
			|| e == UNCLASSIFIED
			|| e == ALL_BUNDLES;
	}

	@Override @AvailMethod
	A_Map o_AllBundles (final AvailObject object)
	{
		return object.slot(ALL_BUNDLES);
	}

	@Override @AvailMethod
	A_Map o_LazyComplete (final AvailObject object)
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
	A_Map o_LazyActions (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(LAZY_ACTIONS);
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

	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (final AvailObject object)
	{
		object.expand();
		return super.o_DescribeForDebugger(object);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("BundleTree[pc=");
		builder.append(object.slot(PARSING_PC));
		builder.append("](");
		final A_Map allBundles = object.slot(ALL_BUNDLES);
		final int bundleCount = allBundles.mapSize();
		if (bundleCount <= 10)
		{
			boolean first = true;
			for (final MapDescriptor.Entry entry : allBundles.mapIterable())
			{
				if (!first)
				{
					builder.append(", ");
				}
				first = false;
				builder.append(entry.key().name());
			}
		}
		else
		{
			builder.append(bundleCount);
			builder.append(" entries");
		}
		builder.append(")");
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

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			int hash = object.slot(HASH_OR_ZERO);
			if (hash == 0)
			{
				do
				{
					hash = AvailRuntime.nextHash();
				}
				while (hash == 0);
				object.setSlot(HASH_OR_ZERO, hash);
			}
			return hash;
		}
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MESSAGE_BUNDLE_TREE.o();
	}

	@Override @AvailMethod
	A_Map o_Complete (final AvailObject object)
	{
		synchronized (object)
		{
			object.expand();
			return object.slot(LAZY_COMPLETE);
		}
	}

	@Override @AvailMethod
	A_Map o_Incomplete (final AvailObject object)
	{
		synchronized (object)
		{
			object.expand();
			return object.slot(LAZY_INCOMPLETE);
		}
	}

	/**
	 * Add the given message/bundle pair.
	 */
	@Override @AvailMethod
	void o_AddBundle (
		final AvailObject object,
		final A_BasicObject bundle)
	{
		synchronized (object)
		{
			A_Map allBundles = object.slot(ALL_BUNDLES);
			final A_Atom message = bundle.message();
			if (!allBundles.hasKey(message))
			{
				allBundles = allBundles.mapAtPuttingCanDestroy(
					message,
					bundle,
					true);
				object.setSlot(ALL_BUNDLES, allBundles.makeShared());
				A_Map unclassified = object.slot(UNCLASSIFIED);
				assert !unclassified.hasKey(message);
				unclassified = unclassified.mapAtPuttingCanDestroy(
					message,
					bundle,
					true);
				object.setSlot(UNCLASSIFIED, unclassified.makeShared());
			}
		}
	}

	/**
	 * If there isn't one already, create a bundle with the specified name.
	 * Answer the new or existing bundle.
	 */
	@Override @AvailMethod
	AvailObject o_IncludeBundleNamed (
		final AvailObject object,
		final A_Atom messageBundleName,
		final A_BasicObject method)
	{
		synchronized (object)
		{
			A_Map allBundles = object.slot(ALL_BUNDLES);
			if (allBundles.hasKey(messageBundleName))
			{
				return allBundles.mapAt(messageBundleName);
			}
			final AvailObject newBundle;
			try
			{
				newBundle = MessageBundleDescriptor.newBundle(
					messageBundleName,
					method);
			}
			catch (final SignatureException e)
			{
				// Signature should have been pre-checked already.
				throw new RuntimeException(e);
			}
			allBundles = allBundles.mapAtPuttingCanDestroy(
				messageBundleName,
				newBundle,
				true);
			object.setSlot(ALL_BUNDLES, allBundles.makeShared());
			A_Map unclassified = object.slot(UNCLASSIFIED);
			unclassified = unclassified.mapAtPuttingCanDestroy(
				messageBundleName,
				newBundle,
				true);
			object.setSlot(UNCLASSIFIED, unclassified.makeShared());
			return newBundle;
		}
	}

	/**
	 * Remove the bundle with the given message name (expanded as parts).
	 * Answer true if this tree is now empty and should be removed.
	 */
	@Override @AvailMethod
	boolean o_RemoveBundleNamed (
		final AvailObject object,
		final A_Atom message)
	{
		assert message.isAtom();
		synchronized (object)
		{
			AvailObject allBundles = object.slot(ALL_BUNDLES);
			if (allBundles.hasKey(message))
			{
				allBundles = allBundles.mapWithoutKeyCanDestroy(
					message,
					true).traversed().makeShared();
				object.setSlot(ALL_BUNDLES, allBundles);
				AvailObject unclassified = object.slot(UNCLASSIFIED);
				if (unclassified.hasKey(message))
				{
					// Easy to do.
					unclassified = unclassified.mapWithoutKeyCanDestroy(
						message,
						true).traversed().makeShared();
				}
				else
				{
					// Not so easy -- just clear everything.
					final A_Map emptyMap = MapDescriptor.empty();
					object.setSlot(LAZY_COMPLETE, emptyMap);
					object.setSlot(LAZY_INCOMPLETE, emptyMap);
					object.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap);
					object.setSlot(LAZY_ACTIONS, emptyMap);
					object.setSlot(LAZY_PREFILTER_MAP, emptyMap);
					allBundles = allBundles.traversed().makeShared();
					unclassified = allBundles;
				}
				object.setSlot(UNCLASSIFIED, unclassified);
			}
			return allBundles.mapSize() == 0;
		}
	}


	/**
	 * Expand the bundle tree if there's anything unclassified in it.
	 */
	@Override @AvailMethod
	void o_Expand (final AvailObject object)
	{
		synchronized (object)
		{
			final A_Map unclassified = object.slot(UNCLASSIFIED);
			if (unclassified.mapSize() == 0)
			{
				return;
			}
			final Mutable<A_Map> complete = new Mutable<A_Map>(
				object.slot(LAZY_COMPLETE));
			final Mutable<A_Map> incomplete = new Mutable<A_Map>(
				object.slot(LAZY_INCOMPLETE));
			final Mutable<A_Map> caseInsensitive = new Mutable<A_Map>(
				object.slot(LAZY_INCOMPLETE_CASE_INSENSITIVE));
			final Mutable<A_Map> actionMap = new Mutable<A_Map>(
				object.slot(LAZY_ACTIONS));
			final Mutable<A_Map> prefilterMap = new Mutable<A_Map>(
				object.slot(LAZY_PREFILTER_MAP));
			final int pc = object.slot(PARSING_PC);
			// Fail fast if someone messes with this during iteration.
			object.setSlot(UNCLASSIFIED, NilDescriptor.nil());
			for (final MapDescriptor.Entry entry : unclassified.mapIterable())
			{
				final AvailObject message = entry.key();
				final AvailObject bundle = entry.value();
				updateForMessageAndBundle(
					message,
					bundle,
					complete,
					incomplete,
					caseInsensitive,
					actionMap,
					prefilterMap,
					pc);
			}
			object.setSlot(UNCLASSIFIED, MapDescriptor.empty());
			object.setSlot(LAZY_COMPLETE, complete.value.makeShared());
			object.setSlot(LAZY_INCOMPLETE, incomplete.value.makeShared());
			object.setSlot(
				LAZY_INCOMPLETE_CASE_INSENSITIVE,
				caseInsensitive.value.makeShared());
			object.setSlot(LAZY_ACTIONS, actionMap.value.makeShared());
			object.setSlot(LAZY_PREFILTER_MAP, prefilterMap.value.makeShared());
		}
	}

	/**
	 * Categorize a single message/bundle pair.
	 *
	 * @param message
	 * @param bundle
	 * @param complete
	 * @param incomplete
	 * @param caseInsensitive
	 * @param actionMap
	 * @param prefilterMap
	 * @param pc
	 */
	private static void updateForMessageAndBundle (
		final AvailObject message,
		final AvailObject bundle,
		final Mutable<A_Map> complete,
		final Mutable<A_Map> incomplete,
		final Mutable<A_Map> caseInsensitive,
		final Mutable<A_Map> actionMap,
		final Mutable<A_Map> prefilterMap,
		final int pc)
	{
		//TODO[MvG] Let the bundles directly hold methods in addition to the
		//name by which they're referring to it.
		if (bundle.bundleMethod().definitionsTuple().tupleSize() == 0)
		{
			// There are no definitions of this method, so there's no reason
			// to try to parse it.  This can happen when grammatical
			// restrictions are defined before a method is implemented, a
			// reasonably common situation.
			return;
		}
		final A_Tuple instructions = bundle.parsingInstructions();
		if (pc == instructions.tupleSize() + 1)
		{
			// It's past the end of the parsing instructions.
			complete.value = complete.value.mapAtPuttingCanDestroy(
				message,
				bundle,
				true);
			return;
		}

		final int instruction = instructions.tupleIntAt(pc);
		final ParsingOperation op = ParsingOperation.decode(instruction);
		final int keywordIndex = op.keywordIndex(instruction);
		if (keywordIndex != 0)
		{
			// It's a parsing instruction that matches some specific keyword.
			assert op == PARSE_PART
				|| op == PARSE_PART_CASE_INSENSITIVELY;
			AvailObject subtree;
			final AvailObject part = bundle.messageParts().tupleAt(
				keywordIndex);
			final Mutable<A_Map> map = (op == PARSE_PART)
				? incomplete
				: caseInsensitive;
			if (map.value.hasKey(part))
			{
				subtree = map.value.mapAt(part);
			}
			else
			{
				subtree = newPc(pc + 1);
				map.value = map.value.mapAtPuttingCanDestroy(
					part,
					subtree,
					true);
			}
			subtree.addBundle(bundle);
			return;
		}

		// It's not a keyword parsing instruction.
		final A_Number instructionObject =
			IntegerDescriptor.fromInt(instruction);
		final List<Integer> nextPcs = op.successorPcs(instruction, pc);
		final int checkArgumentIndex = op.checkArgumentIndex(instruction);
		if (checkArgumentIndex > 0)
		{
			// It's a checkArgument instruction.
			assert nextPcs.size() == 1;
			assert nextPcs.get(0).intValue() == pc + 1;
			// Add it to the action map.
			final AvailObject successor;
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
			final A_Set restrictionSet =
				bundle.grammaticalRestrictions().tupleAt(checkArgumentIndex);
			// Add it to every existing branch where it's permitted.
			for (final MapDescriptor.Entry prefilterEntry
				: prefilterMap.value.mapIterable())
			{
				if (!restrictionSet.hasElement(prefilterEntry.key()))
				{
					prefilterEntry.value().addBundle(bundle);
				}
			}
			// Add branches for any new restrictions.  Pre-populate
			// with every bundle present thus far, since none of
			// them had this restriction.
			for (final AvailObject restriction : restrictionSet)
			{
				if (!prefilterMap.value.hasKey(restriction))
				{
					final AvailObject newTarget = newPc(pc + 1);
					// Be careful.  We can't add ALL_BUNDLES, since
					// it may contain some bundles that are still
					// UNCLASSIFIED.  Instead, use ALL_BUNDLES of
					// the successor found under this instruction,
					// since it *has* been kept up to date as the
					// bundles have gotten classified.
					for (final MapDescriptor.Entry existingEntry
						: successor.allBundles().mapIterable())
					{
						newTarget.addBundle(existingEntry.value());
					}
					prefilterMap.value =
						prefilterMap.value.mapAtPuttingCanDestroy(
							restriction,
							newTarget,
							true);
				}
			}
			// Finally, add it to the action map.  This had to be
			// postponed, since we didn't want to add it under any
			// new restrictions, and the actionMap is what gets
			// visited to populate new restrictions.
			successor.addBundle(bundle);

			// Note:  DO NOT return here, since the action has to also be added
			// to the actionMap (to deal with the case that a subexpression is
			// a non-send, or a send that is not forbidden in this position by
			// any potential parent sends.
		}

		// It's an ordinary parsing instruction.
		final A_Tuple successors;
		if (actionMap.value.hasKey(instructionObject))
		{
			successors = actionMap.value.mapAt(instructionObject);
		}
		else
		{
			final List<AvailObject> successorsList =
				new ArrayList<AvailObject>(nextPcs.size());
			for (final int nextPc : nextPcs)
			{
				successorsList.add(newPc(nextPc));
			}
			successors = TupleDescriptor.fromList(successorsList);
			actionMap.value = actionMap.value.mapAtPuttingCanDestroy(
				instructionObject,
				successors,
				true);
		}
		assert successors.tupleSize() == nextPcs.size();
		for (final A_BasicObject successor : successors)
		{
			successor.addBundle(bundle);
		}
	}


	/**
	 * Update information in this {@linkplain MessageBundleTreeDescriptor
	 * message bundle tree} to reflect the latest information about the
	 * {@linkplain MessageBundleDescriptor message bundle} with the given name.
	 *
	 * <p>The bundle may have been added, or updated (but not removed), so if
	 * the bundle has already been classified in this tree (via {@link
	 * #o_Expand(AvailObject)}, we may need to invalidate some of the tree's
	 * lazy structures.</p>
	 */
	@Override
	void o_FlushForNewOrChangedBundleNamed (
		final AvailObject object,
		final A_Atom message)
	{
		final AvailObject allBundles = object.slot(ALL_BUNDLES);
		assert allBundles.hasKey(message);
		if (!object.slot(UNCLASSIFIED).hasKey(message))
		{
			// It has been classified already, so flush the lazy structures
			// for safety, moving everything back to unclassified.
			final A_Map emptyMap = MapDescriptor.empty();
			object.setSlot(LAZY_COMPLETE, emptyMap);
			object.setSlot(LAZY_INCOMPLETE, emptyMap);
			object.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, emptyMap);
			object.setSlot(LAZY_ACTIONS, emptyMap);
			object.setSlot(LAZY_PREFILTER_MAP, emptyMap);
			allBundles.makeImmutable();
			object.setSlot(UNCLASSIFIED, allBundles);
		}
	}

	/**
	 * Create a new empty message bundle tree.
	 *
	 * @param pc A common index into each eligible message's instructions.
	 * @return The new unexpanded, empty message bundle tree.
	 */
	public static AvailObject newPc (final int pc)
	{
		final AvailObject result = mutable.create();
		result.setSlot(PARSING_PC, pc);
		result.setSlot(HASH_OR_ZERO, 0);
		result.setSlot(ALL_BUNDLES, MapDescriptor.empty());
		result.setSlot(UNCLASSIFIED, MapDescriptor.empty());
		result.setSlot(LAZY_COMPLETE, MapDescriptor.empty());
		result.setSlot(LAZY_INCOMPLETE, MapDescriptor.empty());
		result.setSlot(LAZY_INCOMPLETE_CASE_INSENSITIVE, MapDescriptor.empty());
		result.setSlot(LAZY_ACTIONS, MapDescriptor.empty());
		result.setSlot(LAZY_PREFILTER_MAP, MapDescriptor.empty());
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
		super(mutability);
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
