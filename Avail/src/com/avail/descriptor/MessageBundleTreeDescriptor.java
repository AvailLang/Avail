/**
 * descriptor/MessageBundleTreeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
import com.avail.annotations.NotNull;
import com.avail.compiler.MessageSplitter;


public class MessageBundleTreeDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		PARSING_PC
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		ALL_BUNDLES,
		UNCLASSIFIED,
		LAZY_COMPLETE,
		LAZY_INCOMPLETE,
		LAZY_SPECIAL_ACTIONS,
	}


	@Override
	public void o_ParsingPc (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.PARSING_PC, value);
	}

	@Override
	public void o_AllBundles (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.ALL_BUNDLES, value);
	}

	@Override
	public void o_Unclassified (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.UNCLASSIFIED, value);
	}

	@Override
	public void o_LazyComplete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.LAZY_COMPLETE, value);
	}

	@Override
	public void o_LazyIncomplete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.LAZY_INCOMPLETE, value);
	}

	@Override
	public void o_LazySpecialActions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.LAZY_SPECIAL_ACTIONS, value);
	}

	@Override
	public int o_ParsingPc (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PARSING_PC);
	}

	@Override
	public @NotNull AvailObject o_AllBundles (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ALL_BUNDLES);
	}

	@Override
	public @NotNull AvailObject o_Unclassified (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.UNCLASSIFIED);
	}

	@Override
	public @NotNull AvailObject o_LazyComplete (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.LAZY_COMPLETE);
	}

	@Override
	public @NotNull AvailObject o_LazyIncomplete (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.LAZY_INCOMPLETE);
	}

	@Override
	public @NotNull AvailObject o_LazySpecialActions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.LAZY_SPECIAL_ACTIONS);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.LAZY_COMPLETE
			|| e == ObjectSlots.LAZY_INCOMPLETE
			|| e == ObjectSlots.LAZY_SPECIAL_ACTIONS
			|| e == ObjectSlots.UNCLASSIFIED
			|| e == ObjectSlots.ALL_BUNDLES;
	}

	/**
	 * Make the object immutable so it can be shared safely.  If I was mutable I
	 * have to scan my children and make them immutable as well (recursively
	 * down to immutable descendants).
	 */
	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor(immutable());
		// Don't bother scanning subobjects. They're allowed to be mutable even
		// when object is immutable.
		return object;
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.
		return MESSAGE_BUNDLE_TREE.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		// Answer a 32-bit hash value.  Do something better than this
		// eventually.
		return 0;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return MESSAGE_BUNDLE_TREE.o();
	}

	@Override
	public AvailObject o_Complete (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazyComplete();
	}

	@Override
	public AvailObject o_Incomplete (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazyIncomplete();
	}

	@Override
	public AvailObject o_SpecialActions (
		final @NotNull AvailObject object)
	{
		object.expand();
		return object.lazySpecialActions();
	}

	/**
	 * Add the given message/bundle pair.
	 */
	@Override
	public void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject bundle)
	{
		object.allBundles(
			object.allBundles().mapAtPuttingCanDestroy(
				message,
				bundle,
				true));
		AvailObject unclassified = object.unclassified();
		assert !unclassified.hasKey(message);
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			bundle,
			true);
		object.unclassified(unclassified);
	}

	/**
	 * Copy the visible message bundles to the filteredBundleTree.  The Avail
	 * {@linkplain SetDescriptor set} of visible names ({@linkplain
	 * CyclicTypeDescriptor cyclicTypes}) is in {@code visibleNames}.
	 */
	@Override
	public void o_CopyToRestrictedTo (
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
		};
		filteredBundleTree.allBundles(filteredAllBundles);
		filteredBundleTree.unclassified(filteredUnclassified);
	}

	/**
	 * If there isn't one already, add a bundle to correspond to the given
	 * message.  Answer the new or existing bundle.
	 */
	@Override
	public @NotNull AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject newBundle)
	{
		final AvailObject allBundles = object.allBundles();
		final AvailObject message = newBundle.message();
		if (allBundles.hasKey(message))
		{
			return allBundles.mapAt(message);
		}
		object.allBundles(
			object.allBundles().mapAtPuttingCanDestroy(
				message,
				newBundle,
				true));
		object.unclassified(
			object.unclassified().mapAtPuttingCanDestroy(
				message,
				newBundle,
				true));
		return newBundle;
	}

	/**
	 * Remove the bundle with the given message name (expanded as parts).
	 * Answer true if this tree is now empty and should be removed.
	 */
	@Override
	public boolean o_RemoveBundle (
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
			object.allBundles(allBundles);
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
				object.lazySpecialActions(MapDescriptor.empty());
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
	@Override
	public void o_Expand (
		final @NotNull AvailObject object)
	{
		final AvailObject unclassified = object.unclassified();
		if (unclassified.mapSize() == 0)
		{
			return;
		}
		AvailObject complete = object.lazyComplete();
		AvailObject incomplete = object.lazyIncomplete();
		AvailObject specialMap = object.lazySpecialActions();
		final int pc = object.parsingPc();
		// Fail fast if someone messes with this during iteration.
		object.unclassified(VoidDescriptor.voidObject());
		for (MapDescriptor.Entry entry : unclassified.mapIterable())
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
				int instruction = instructions.tupleIntAt(pc);
				int keywordIndex =
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
					// It's a special instruction.
					AvailObject successors;
					AvailObject instructionObject =
						IntegerDescriptor.fromInt(instruction);
					List<Integer> nextPcs =
						MessageSplitter.successorPcs(instruction, pc);
					if (specialMap.hasKey(instructionObject))
					{
						successors = specialMap.mapAt(instructionObject);
					}
					else
					{
						List<AvailObject> successorsList =
							new ArrayList<AvailObject>(nextPcs.size());
						for (int nextPc : nextPcs)
						{
							successorsList.add(newPc(nextPc));
						}
						successors = TupleDescriptor.fromList(
							successorsList);
						specialMap = specialMap.mapAtPuttingCanDestroy(
							instructionObject,
							successors,
							true);
					}
					assert successors.tupleSize() == nextPcs.size();
					for (AvailObject successor : successors)
					{
						successor.includeBundle(bundle);
					}
				}
			}
		};
		object.unclassified(MapDescriptor.empty());
		object.lazyComplete(complete);
		object.lazyIncomplete(incomplete);
		object.lazySpecialActions(specialMap);
	}


	/**
	 * Create a new empty message bundle tree.
	 *
	 * @param pc A common index into each eligible message's instructions.
	 * @return The new unexpanded, empty message bundle tree.
	 */
	public static AvailObject newPc(final int pc)
	{
		AvailObject result = mutable().create();
		result.parsingPc(pc);
		result.allBundles(MapDescriptor.empty());
		result.unclassified(MapDescriptor.empty());
		result.lazyComplete(MapDescriptor.empty());
		result.lazyIncomplete(MapDescriptor.empty());
		result.lazySpecialActions(MapDescriptor.empty());
		result.makeImmutable();
		return result;
	};


	/**
	 * Construct a new {@link MessageBundleTreeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MessageBundleTreeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MessageBundleTreeDescriptor}.
	 */
	private final static MessageBundleTreeDescriptor mutable =
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
	private final static MessageBundleTreeDescriptor immutable =
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
