/**
 * descriptor/ExpandedMessageBundleTreeDescriptor.java
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

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.MessageSplitter;
import com.avail.utility.*;

public class ExpandedMessageBundleTreeDescriptor
extends MessageBundleTreeDescriptor
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
		COMPLETE,
		INCOMPLETE,
		SPECIAL_ACTIONS,
		UNCLASSIFIED
	}

	@Override
	public void o_Complete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.COMPLETE, value);
	}

	@Override
	public void o_ParsingPc (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.PARSING_PC, value);
	}

	@Override
	public void o_Incomplete (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.INCOMPLETE, value);
	}

	@Override
	public void o_SpecialActions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.SPECIAL_ACTIONS, value);
	}

	@Override
	public void o_Unclassified (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.UNCLASSIFIED, value);
	}

	@Override
	public @NotNull AvailObject o_Complete (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.COMPLETE);
	}

	@Override
	public int o_ParsingPc (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PARSING_PC);
	}

	@Override
	public @NotNull AvailObject o_Incomplete (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INCOMPLETE);
	}

	@Override
	public @NotNull AvailObject o_SpecialActions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SPECIAL_ACTIONS);
	}

	@Override
	public @NotNull AvailObject o_Unclassified (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.UNCLASSIFIED);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.COMPLETE
			|| e == ObjectSlots.INCOMPLETE
			|| e == ObjectSlots.SPECIAL_ACTIONS
			|| e == ObjectSlots.UNCLASSIFIED
			|| e == IntegerSlots.PARSING_PC;
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

	/**
	 * Add the given message/bundle pair.
	 */
	@Override
	public void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject bundle)
	{
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
		Continuation2<AvailObject, AvailObject> action =
			new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject message,
					final AvailObject bundle)
				{
					if (visibleNames.hasElement(message))
					{
						filteredBundleTree.atMessageAddBundle(message, bundle);
					}
				}
			};
		object.unclassified().mapDo(action);
		object.complete().mapDo(action);
		object.incomplete().mapDo(
			new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject message,
					final AvailObject subtree)
				{
					if (!subtree.equalsVoid())
					{
						subtree.copyToRestrictedTo(
							filteredBundleTree,
							visibleNames);
					}
				}
			});
	}

	/**
	 * If there isn't one already, add a bundle to correspond to the given
	 * message.  Answer the new or existing bundle.
	 */
	@Override
	public @NotNull AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject messageBundle)
	{
		AvailObject unclassified = object.unclassified();
		final AvailObject message = messageBundle.message();
		if (unclassified.hasKey(message))
		{
			return unclassified.mapAt(message);
		}

		final int pc = object.parsingPc();
		final AvailObject instructions = messageBundle.parsingInstructions();
		if (pc == instructions.tupleSize() + 1)
		{
			// It's just past the last instruction -- it's complete.
			AvailObject complete = object.complete();
			if (complete.hasKey(message))
			{
				return complete.mapAt(message);
			}
			complete = complete.mapAtPuttingCanDestroy(
				message,
				messageBundle,
				true);
			object.complete(complete);
			return messageBundle;
		}
		// It'll be in zero or more subtrees, and it should be added if it's
		// not found.
		final int instruction = instructions.tupleIntAt(pc);
		final int keywordIndex =
			MessageSplitter.keywordIndexFromInstruction(instruction);
		if (keywordIndex != 0)
		{
			// This is a keyword parse instruction.  Look up the keyword.
			final AvailObject parts = messageBundle.messageParts();
			final AvailObject keyword = parts.tupleAt(keywordIndex);
			AvailObject incomplete = object.incomplete();
			AvailObject subtree;
			if (incomplete.hasKey(keyword))
			{
				subtree = incomplete.mapAt(keyword);
			}
			else
			{
				subtree = ExpandedMessageBundleTreeDescriptor.newPc(pc + 1);
				incomplete = incomplete.mapAtPuttingCanDestroy(
					keyword,
					subtree,
					true);
				object.incomplete(incomplete);
			}
			return subtree.includeBundle(messageBundle);
		}
		// This is not a keyword parse instruction.  Look up the parsing action
		// in the specialActions table.
		final AvailObject instructionObject = instructions.tupleAt(pc);
		AvailObject specials = object.specialActions();
		AvailObject subtrees;
		if (specials.hasKey(instructionObject))
		{
			subtrees = specials.mapAt(instructionObject);
		}
		else
		{
			List<Integer> successorPcs = MessageSplitter.successorPcs(
				instruction,
				pc);
			List<AvailObject> newTrees = new ArrayList<AvailObject>(
				successorPcs.size());
			for (int successorPc : successorPcs)
			{
				newTrees.add(
					ExpandedMessageBundleTreeDescriptor.newPc(successorPc));
			}
			subtrees = TupleDescriptor.fromList(newTrees);
			specials = specials.mapAtPuttingCanDestroy(
				instructionObject,
				subtrees,
				true);
			object.specialActions(specials);
		}
		AvailObject result = null;
		for (AvailObject subtree : subtrees)
		{
			AvailObject newResult = subtree.includeBundle(messageBundle);
			if (result != null)
			{
				assert newResult.sameAddressAs(result);
			}
			result = newResult;
		}
		assert result != null
		: "Every parsing instruction should have at least one successor";
		return result;
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
		AvailObject unclassified = object.unclassified();
		unclassified = unclassified.mapWithoutKeyCanDestroy(
			bundle.message(),
			true);
		object.unclassified(unclassified);

		final int pc = object.parsingPc();
		AvailObject complete = object.complete();
		AvailObject incomplete = object.incomplete();
		final AvailObject instructions = bundle.parsingInstructions();
		final AvailObject message = bundle.message();
		final AvailObject parts = bundle.messageParts();
		if (pc == instructions.tupleSize() + 1)
		{
			complete = complete.mapWithoutKeyCanDestroy(message, true);
			object.complete(complete);
		}
		else
		{
			// An incomplete parse.
			final int instruction = instructions.tupleIntAt(pc);
			final int keywordIndex =
				MessageSplitter.keywordIndexFromInstruction(instruction);
			if (keywordIndex != 0)
			{
				// This is a keyword parse instruction.  Look up the keyword.
				AvailObject keyword = parts.tupleAt(keywordIndex);
				AvailObject subtree;
				if (incomplete.hasKey(keyword))
				{
					subtree = incomplete.mapAt(keyword);
				}
				else
				{
					subtree = ExpandedMessageBundleTreeDescriptor.newPc(pc + 1);
					incomplete = incomplete.mapAtPuttingCanDestroy(
						keyword,
						subtree,
						true);
					object.incomplete(incomplete);
				}
			}

			final AvailObject part = parts.tupleAt(pc);
			if (object.incomplete().hasKey(part))
			{
				final AvailObject subtree = object.incomplete().mapAt(part);
				final boolean prune = subtree.removeBundle(
					bundle);
				if (prune)
				{
					incomplete = incomplete.mapWithoutKeyCanDestroy(part, true);
					object.incomplete(incomplete);
				}
			}
		}
		return unclassified.mapSize() == 0
			&& complete.mapSize() == 0
			&& incomplete.mapSize() == 0
			&& object.specialActions().mapSize() == 0;
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
		final Mutable<AvailObject> complete =
			new Mutable<AvailObject>(object.complete());
		final Mutable<AvailObject> incomplete =
			new Mutable<AvailObject>(object.incomplete());
		final Mutable<AvailObject> specialMap =
			new Mutable<AvailObject>(object.specialActions());
		final int pc = object.parsingPc();
		unclassified.mapDo(new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (
				final AvailObject message,
				final AvailObject bundle)
			{
				final AvailObject instructions = bundle.parsingInstructions();
				if (pc == instructions.tupleSize() + 1)
				{
					complete.value = complete.value.mapAtPuttingCanDestroy(
						message,
						bundle,
						true);
				}
				else
				{
					int instruction = instructions.tupleIntAt(pc);
					int keywordIndex =
						MessageSplitter.keywordIndexFromInstruction(
							instruction);
					if (keywordIndex != 0)
					{
						// It's a parseKeyword instruction.
						AvailObject subtree;
						final AvailObject part = bundle.messageParts().tupleAt(
							keywordIndex);
						if (incomplete.value.hasKey(part))
						{
							subtree = incomplete.value.mapAt(part);
						}
						else
						{
							subtree = newPc(pc + 1);
							incomplete.value =
								incomplete.value.mapAtPuttingCanDestroy(
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
						if (specialMap.value.hasKey(instructionObject))
						{
							successors = specialMap.value.mapAt(
								instructionObject);
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
							specialMap.value =
								specialMap.value.mapAtPuttingCanDestroy(
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
			}
		});
		object.unclassified(MapDescriptor.empty());
		object.complete(complete.value);
		object.incomplete(incomplete.value);
		object.specialActions(specialMap.value);
	}


	/**
	 * Create a new message bundle tree, but don't yet break down the messages
	 * and categorize them into complete/incomplete.  That will happen on
	 * demand during parsing.
	 *
	 * @param pc A common index into each eligible message's instructions.
	 * @return The new unexpanded, empty message bundle tree.
	 */
	public static AvailObject newPc(final int pc)
	{
		AvailObject result = mutable().create();
		result.parsingPc(pc);
		result.unclassified(MapDescriptor.empty());
		result.complete(MapDescriptor.empty());
		result.incomplete(MapDescriptor.empty());
		result.specialActions(MapDescriptor.empty());
		result.makeImmutable();
		return result;
	};


	/**
	 * Construct a new {@link ExpandedMessageBundleTreeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ExpandedMessageBundleTreeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	private final static ExpandedMessageBundleTreeDescriptor mutable = new ExpandedMessageBundleTreeDescriptor(true);

	/**
	 * Answer the mutable {@link ExpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The mutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	public static ExpandedMessageBundleTreeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	private final static ExpandedMessageBundleTreeDescriptor immutable = new ExpandedMessageBundleTreeDescriptor(false);

	/**
	 * Answer the immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	public static ExpandedMessageBundleTreeDescriptor immutable ()
	{
		return immutable;
	}
}
