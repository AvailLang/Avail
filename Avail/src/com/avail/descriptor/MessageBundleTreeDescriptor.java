/**
 * MessageBundleTreeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE_TREE;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.MessageSplitter;


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
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MessageBundleTreeDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The subscript into the {@linkplain TupleDescriptor tuple} of encoded
		 * parsing instructions.  These instructions are produced by the {@link
		 * MessageSplitter} as a way to interpret the tokens, underscores, and
		 * chevron expressions of a method name.  There may be multiple
		 * potential method invocations being parsed <em>together</em> at this
		 * position in the message bundle tree, but the parsing instructions
		 * that have been encountered so far along this history must be the same
		 * for each message – otherwise the message bundle tree would have
		 * diverged into multiple subtrees.
		 */
		PARSING_PC
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing.  In particular, any
		 * {@linkplain MessageBundleTreeDescriptor message bundle tree}
		 * represents the current state of collective parsing of an invocation
		 * of one or more thus far equivalent methods.  This is the collection
		 * of such methods.
		 */
		ALL_BUNDLES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing.  In particular,
		 * these are methods that have not yet been categorized as complete,
		 * incomplete, action, or prefilter.  They are categorized if and when
		 * this message bundle tree is reached during parsing.
		 */
		UNCLASSIFIED,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} that name methods to the {@linkplain MessageBundleDescriptor
		 * message bundles} that assist with their parsing.  In particular,
		 * these are methods for which an invocation has just been completely
		 * parsed.
		 */
		LAZY_COMPLETE,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * strings} to successor {@linkplain MessageBundleTreeDescriptor message
		 * bundle trees}.  During parsing, if the next token is a key of this
		 * map then consume that token, look it up in this map, and continue
		 * parsing with the corresponding message bundle tree.  Otherwise record
		 * a suitable parsing failure message for this position in the source
		 * stream (in case this ends up being the rightmost parse position to be
		 * reached).
		 *
		 * <p>
		 * {@linkplain MessageBundleDescriptor Message bundles} only get added
		 * to this map if their current instruction is the parseKeyword
		 * instruction.  There may be other instructions current for other
		 * message bundles, but they will be represented in the {@link
		 * #LAZY_ACTIONS} map, or the {@link #LAZY_PREFILTER_MAP} if
		 * their instruction is a checkArgument.
		 * </p>
		 */
		LAZY_INCOMPLETE,

		/**
		 * This is a map from instruction (an {@linkplain IntegerDescriptor
		 * integer}) to the (non-empty) tuple of {@linkplain
		 * MessageBundleTreeDescriptor message bundle trees} to attempt if the
		 * instruction succeeds.  Attempt each possible instruction, proceeding
		 * to each of the successor trees if the instruction succeeds.
		 *
		 * <p>
		 * Note that the parseKeyword instruction (8*N+2) is treated specially,
		 * as only one keyword can be next in the source stream (so there's no
		 * value in checking whether it's an X, whether it's a Y, whether it's a
		 * Z, etc.  Instead, the {@link #LAZY_INCOMPLETE} {@linkplain
		 * MapDescriptor map} takes care of dealing with this efficiently with a
		 * single lookup.
		 * </p>
		 *
		 * <p>
		 * Similarly, the checkArgument instruction (8*N+3) is treated
		 * specially.  When it is encountered and the argument that was just
		 * parsed is a send node, that send node is looked up in the {@link
		 * #LAZY_PREFILTER_MAP}, yielding the next message bundle tree.  If it's
		 * not present as a key (or the argument isn't a send), then the
		 * instruction is looked up normally in the {@link #LAZY_ACTIONS} map.
		 * </p>
		 */
		LAZY_ACTIONS,

		/**
		 * If we wait until all tokens and arguments of a potential method send
		 * have been parsed before checking that all the arguments have the
		 * right types and precedence then we may spend a <em>lot</em> of extra
		 * effort parsing unnecessary expressions.  For example, the "_*_"
		 * operation might not allow a "_+_" call for its left or right
		 * arguments, so parsing "1+2*…" as "(1+2)*…" is wasted effort.
		 *
		 * <p>
		 * This is especially expensive for operations with many arguments that
		 * could otherwise be culled by the shapes and types of early arguments,
		 * such as for "«_‡++»", which forbids arguments being invocations of
		 * the same message, keeping a call with many arguments flat.  I haven't
		 * worked out the complete recurrence relations for this, but it's
		 * probably exponential (it certainly grows <em>much</em> faster than
		 * linearly without this optimization).
		 * </p>
		 *
		 * <p>
		 * To accomplish this culling we have to filter out any inconsistent
		 * {@linkplain MessageBundleDescriptor message bundles} as we parse.
		 * Since we already do this in general while parsing expressions, all
		 * that remains is to check right after an argument has been parsed (or
		 * replayed due to the dynamic programming optimization).  The check for
		 * now is simple and doesn't consider argument types, simply excluding
		 * methods based on the grammatical restrictions.
		 * </p>
		 *
		 * <p>
		 * When a message bundle's next instruction is checkArgument (8*N+3)
		 * (which must be all or nothing within a {@linkplain
		 * MessageBundleTreeDescriptor message bundle tree}, this {@link
		 * #LAZY_PREFILTER_MAP} is populated.  It maps from interesting
		 * {@linkplain AtomDescriptor atoms} naming methods that might occur as
		 * an argument to an appropriately reduced {@linkplain
		 * MessageBundleTreeDescriptor message bundle tree} (i.e., a message
		 * bundle tree containing precisely those method bundles that allow that
		 * argument.  The only keys that occur are ones for which at least one
		 * restriction exists in at least one of the still possible {@linkplain
		 * MessageBundleDescriptor message bundles}.  When {@link #UNCLASSIFIED}
		 * is empty, <em>all</em> such restricted argument message names occur
		 * in this map.  Note that some of the resulting message bundle trees
		 * may be completely empty.  Also note that some of the trees may be
		 * shared, so be careful to discard them rather than maintaining them
		 * when new method bundles or grammatical restrictions are added.
		 * </p>
		 *
		 * <p>
		 * When an argument is a message that is not restricted for any of the
		 * message bundles in this message bundle tree (i.e., it does not occur
		 * as a key in this map), then the sole entry in {@link
		 * #LAZY_INCOMPLETE} is used.  The key is always the checkArgument
		 * instruction that all message bundles in this message bundle tree must
		 * have.
		 * </p>
		 */
		LAZY_PREFILTER_MAP
	}

	@Override @AvailMethod
	void o_AllBundles (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.ALL_BUNDLES, value);
	}

	@Override @AvailMethod
	void o_Unclassified (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.UNCLASSIFIED, value);
	}

	@Override @AvailMethod
	void o_LazyComplete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.LAZY_COMPLETE, value);
	}

	@Override @AvailMethod
	void o_LazyIncomplete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.LAZY_INCOMPLETE, value);
	}

	@Override @AvailMethod
	void o_LazyActions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.LAZY_ACTIONS, value);
	}

	@Override @AvailMethod
	void o_LazyPrefilterMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.LAZY_PREFILTER_MAP, value);
	}

	@Override @AvailMethod
	int o_ParsingPc (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.PARSING_PC);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_AllBundles (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.ALL_BUNDLES);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Unclassified (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.UNCLASSIFIED);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LazyComplete (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.LAZY_COMPLETE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LazyIncomplete (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.LAZY_INCOMPLETE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LazyActions (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.LAZY_ACTIONS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LazyPrefilterMap (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.LAZY_PREFILTER_MAP);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.LAZY_COMPLETE
			|| e == ObjectSlots.LAZY_INCOMPLETE
			|| e == ObjectSlots.LAZY_ACTIONS
			|| e == ObjectSlots.LAZY_PREFILTER_MAP
			|| e == ObjectSlots.UNCLASSIFIED
			|| e == ObjectSlots.ALL_BUNDLES;
	}

	/**
	 * Make the object immutable so it can be shared safely.  If I was mutable I
	 * have to scan my children and make them immutable as well (recursively
	 * down to immutable descendants).
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		// Don't bother scanning subobjects. They're allowed to be mutable even
		// when object is immutable.
		return object;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		// Answer a 32-bit hash value.  Do something better than this
		// eventually.
		return 0;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return MESSAGE_BUNDLE_TREE.o();
	}

	@Override @AvailMethod
	AvailObject o_Complete (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazyComplete();
	}

	@Override @AvailMethod
	AvailObject o_Incomplete (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazyIncomplete();
	}

	@Override @AvailMethod
	AvailObject o_Actions (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazyActions();
	}

	/**
	 * Add the given message/bundle pair.
	 */
	@Override @AvailMethod
	void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject bundle)
	{
		AvailObject allBundles = object.slot(ObjectSlots.ALL_BUNDLES);
		allBundles = allBundles.mapAtPuttingCanDestroy(
			message,
			bundle,
			true);
		object.setSlot(ObjectSlots.ALL_BUNDLES, allBundles);
		AvailObject unclassified = object.unclassified();
		assert !unclassified.hasKey(message);
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			bundle,
			true);
		object.setSlot(ObjectSlots.UNCLASSIFIED, unclassified);
	}

	/**
	 * Copy the visible message bundles to the filteredBundleTree.  The Avail
	 * {@linkplain SetDescriptor set} of visible names ({@linkplain
	 * AtomDescriptor atoms}) is in {@code visibleNames}.
	 */
	@Override @AvailMethod
	void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject filteredBundleTree,
		final @NotNull AvailObject visibleNames)
	{
		assert object.parsingPc() == 1;
		assert filteredBundleTree.parsingPc() == 1;

		AvailObject filteredAllBundles = filteredBundleTree.allBundles();
		AvailObject filteredUnclassified = filteredBundleTree.unclassified();
		for (final MapDescriptor.Entry entry
			: object.allBundles().mapIterable())
		{
			final AvailObject message = entry.key;
			final AvailObject bundle = entry.value;
			if (visibleNames.hasElement(message)
				&& !filteredAllBundles.hasKey(message))
			{
				filteredAllBundles =
					filteredAllBundles.mapAtPuttingCanDestroy(
						message,
						bundle,
						true);
				filteredUnclassified =
					filteredUnclassified.mapAtPuttingCanDestroy(
						message,
						bundle,
						true);
			}
		}
		filteredBundleTree.allBundles(filteredAllBundles);
		filteredBundleTree.unclassified(filteredUnclassified);
	}

	/**
	 * If there isn't one already, add a bundle to correspond to the given
	 * message.  Answer the new or existing bundle.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newBundle)
	{
		final AvailObject message = newBundle.message();
		AvailObject allBundles = object.allBundles();
		if (allBundles.hasKey(message))
		{
			return allBundles.mapAt(message);
		}
		allBundles = allBundles.mapAtPuttingCanDestroy(
			message,
			newBundle,
			true);
		object.setSlot(ObjectSlots.ALL_BUNDLES, allBundles);
		AvailObject unclassified = object.slot(ObjectSlots.UNCLASSIFIED);
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			newBundle,
			true);
		object.setSlot(ObjectSlots.UNCLASSIFIED, unclassified);
		return newBundle;
	}

	/**
	 * Remove the bundle with the given message name (expanded as parts).
	 * Answer true if this tree is now empty and should be removed.
	 */
	@Override @AvailMethod
	boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundle)
	{
		AvailObject allBundles = object.allBundles();
		final AvailObject message = bundle.message();
		if (allBundles.hasKey(message))
		{
			allBundles = allBundles.mapWithoutKeyCanDestroy(
				message,
				true);
			object.setSlot(ObjectSlots.ALL_BUNDLES, allBundles);
			AvailObject unclassified = object.unclassified();
			if (unclassified.hasKey(message))
			{
				// Easy to do.
				unclassified = unclassified.mapWithoutKeyCanDestroy(
					message,
					true);
			}
			else
			{
				// Not so easy -- just clear everything.
				object.lazyComplete(MapDescriptor.empty());
				object.lazyIncomplete(MapDescriptor.empty());
				object.lazyActions(MapDescriptor.empty());
				object.lazyPrefilterMap(MapDescriptor.empty());
				allBundles.makeImmutable();
				unclassified = allBundles;
			}
			object.unclassified(unclassified);
		}
		return allBundles.mapSize() == 0;
	}


	/**
	 * Expand the bundleTree if there's anything unclassified in it.
	 */
	@Override @AvailMethod
	void o_Expand (
		final @NotNull AvailObject object)
	{
		final AvailObject unclassified = object.slot(ObjectSlots.UNCLASSIFIED);
		if (unclassified.mapSize() == 0)
		{
			return;
		}
		AvailObject complete = object.slot(ObjectSlots.LAZY_COMPLETE);
		AvailObject incomplete = object.slot(ObjectSlots.LAZY_INCOMPLETE);
		AvailObject actionMap = object.slot(ObjectSlots.LAZY_ACTIONS);
		AvailObject prefilterMap = object.slot(ObjectSlots.LAZY_PREFILTER_MAP);
		final int pc = object.parsingPc();
		// Fail fast if someone messes with this during iteration.
		object.setSlot(ObjectSlots.UNCLASSIFIED, NullDescriptor.nullObject());
		for (final MapDescriptor.Entry entry : unclassified.mapIterable())
		{
			final AvailObject message = entry.key;
			final AvailObject bundle = entry.value;
			final AvailObject instructions = bundle.parsingInstructions();
			if (pc == instructions.tupleSize() + 1)
			{
				complete = complete.mapAtPuttingCanDestroy(
					message,
					bundle,
					true);
			}
			else
			{
				final int instruction = instructions.tupleIntAt(pc);
				final int keywordIndex =
					MessageSplitter.keywordIndexFromInstruction(instruction);
				if (keywordIndex != 0)
				{
					// It's a parseKeyword instruction.
					AvailObject subtree;
					final AvailObject part = bundle.messageParts().tupleAt(
						keywordIndex);
					if (incomplete.hasKey(part))
					{
						subtree = incomplete.mapAt(part);
					}
					else
					{
						subtree = newPc(pc + 1);
						incomplete = incomplete.mapAtPuttingCanDestroy(
							part,
							subtree,
							true);
					}
					subtree.includeBundle(bundle);
				}
				else
				{
					final AvailObject instructionObject =
						IntegerDescriptor.fromInt(instruction);
					final List<Integer> nextPcs =
						MessageSplitter.successorPcs(instruction, pc);
					final int checkArgumentIndex =
						MessageSplitter.checkArgumentIndex(instruction);
					if (checkArgumentIndex > 0)
					{
						// It's a checkArgument instruction.
						assert nextPcs.size() == 1;
						assert nextPcs.get(0) == pc + 1;
						// Add it to the action map.
						final AvailObject successor;
						if (actionMap.hasKey(instructionObject))
						{
							final AvailObject successors =
								actionMap.mapAt(instructionObject);
							assert successors.tupleSize() == 1;
							successor = successors.tupleAt(1);
						}
						else
						{
							successor = newPc(pc + 1);
							actionMap = actionMap.mapAtPuttingCanDestroy(
								instructionObject,
								TupleDescriptor.from(successor),
								true);
						}
						final AvailObject restrictionSet =
							bundle.grammaticalRestrictions().tupleAt(
								checkArgumentIndex);
						// Add it to every existing branch where it's permitted.
						for (final MapDescriptor.Entry prefilterEntry
								: prefilterMap.mapIterable())
						{
							if (!restrictionSet.hasElement(prefilterEntry.key))
							{
								prefilterEntry.value.includeBundle(bundle);
							}
						}
						// Add branches for any new restrictions.  Pre-populate
						// with every bundle present thus far, since none of
						// them had this restriction.
						for (final AvailObject restriction : restrictionSet)
						{
							if (!prefilterMap.hasKey(restriction))
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
									newTarget.includeBundle(
										existingEntry.value);
								}
								prefilterMap =
									prefilterMap.mapAtPuttingCanDestroy(
										restriction,
										newTarget,
										true);
							}
						}
						// Finally, add it to the action map.  This had to be
						// postponed, since we didn't want to add it under any
						// new restrictions, and the actionMap is what gets
						// visited to populate new restrictions.
						successor.includeBundle(bundle);
					}
					else
					{
						// It's an ordinary parsing instruction.
						AvailObject successors;
						if (actionMap.hasKey(instructionObject))
						{
							successors = actionMap.mapAt(instructionObject);
						}
						else
						{
							final List<AvailObject> successorsList =
								new ArrayList<AvailObject>(nextPcs.size());
							for (final int nextPc : nextPcs)
							{
								successorsList.add(newPc(nextPc));
							}
							successors = TupleDescriptor.fromCollection(
								successorsList);
							actionMap = actionMap.mapAtPuttingCanDestroy(
								instructionObject,
								successors,
								true);
						}
						assert successors.tupleSize() == nextPcs.size();
						for (final AvailObject successor : successors)
						{
							successor.includeBundle(bundle);
						}
					}
				}
			}
		}
		object.unclassified(MapDescriptor.empty());
		object.lazyComplete(complete);
		object.lazyIncomplete(incomplete);
		object.lazyActions(actionMap);
		object.lazyPrefilterMap(prefilterMap);
	}


	/**
	 * Create a new empty message bundle tree.
	 *
	 * @param pc A common index into each eligible message's instructions.
	 * @return The new unexpanded, empty message bundle tree.
	 */
	public static AvailObject newPc(final int pc)
	{
		final AvailObject result = mutable().create();
		result.setSlot(IntegerSlots.PARSING_PC, pc);
		result.setSlot(ObjectSlots.ALL_BUNDLES, MapDescriptor.empty());
		result.setSlot(ObjectSlots.UNCLASSIFIED, MapDescriptor.empty());
		result.setSlot(ObjectSlots.LAZY_COMPLETE, MapDescriptor.empty());
		result.setSlot(ObjectSlots.LAZY_INCOMPLETE, MapDescriptor.empty());
		result.setSlot(ObjectSlots.LAZY_ACTIONS, MapDescriptor.empty());
		result.setSlot(ObjectSlots.LAZY_PREFILTER_MAP, MapDescriptor.empty());
		result.makeImmutable();
		return result;
	}


	/**
	 * Construct a new {@link MessageBundleTreeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	private MessageBundleTreeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MessageBundleTreeDescriptor}.
	 */
	private static final MessageBundleTreeDescriptor mutable =
		new MessageBundleTreeDescriptor(true);

	/**
	 * Answer the mutable {@link MessageBundleTreeDescriptor}.
	 *
	 * @return The mutable {@link MessageBundleTreeDescriptor}.
	 */
	public static MessageBundleTreeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MessageBundleTreeDescriptor}.
	 */
	private static final MessageBundleTreeDescriptor immutable =
		new MessageBundleTreeDescriptor(false);

	/**
	 * Answer the immutable {@link MessageBundleTreeDescriptor}.
	 *
	 * @return The immutable {@link MessageBundleTreeDescriptor}.
	 */
	public static MessageBundleTreeDescriptor immutable ()
	{
		return immutable;
	}
}
