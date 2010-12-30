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
import com.avail.compiler.MessageSplitter;
import com.avail.utility.Continuation2;

public class ExpandedMessageBundleTreeDescriptor extends MessageBundleTreeDescriptor
{

	public enum IntegerSlots
	{
		PARSING_PC
	}

	public enum ObjectSlots
	{
		COMPLETE,
		INCOMPLETE,
		SPECIAL_ACTIONS
	}


	// GENERATED accessors

	/**
	 * Setter for field complete.
	 */
	@Override
	public void o_Complete (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.COMPLETE, value);
	}

	/**
	 * Setter for field parsingPc.
	 */
	@Override
	public void o_ParsingPc (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.PARSING_PC, value);
	}

	/**
	 * Setter for field incomplete.
	 */
	@Override
	public void o_Incomplete (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.INCOMPLETE, value);
	}

	/**
	 * Setter for field specialActions.
	 */
	@Override
	public void o_SpecialActions (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.SPECIAL_ACTIONS, value);
	}

	/**
	 * Getter for field complete.
	 */
	@Override
	public AvailObject o_Complete (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.COMPLETE);
	}

	/**
	 * Getter for field parsingPc.
	 */
	@Override
	public int o_ParsingPc (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PARSING_PC);
	}

	/**
	 * Getter for field incomplete.
	 */
	@Override
	public AvailObject o_Incomplete (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INCOMPLETE);
	}

	/**
	 * Getter for field specialActions.
	 */
	@Override
	public AvailObject o_SpecialActions (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SPECIAL_ACTIONS);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == ObjectSlots.COMPLETE)
		{
			return true;
		}
		if (e == ObjectSlots.INCOMPLETE)
		{
			return true;
		}
		if (e == ObjectSlots.SPECIAL_ACTIONS)
		{
			return true;
		}
		if (e == IntegerSlots.PARSING_PC)
		{
			return true;
		}
		return false;
	}



	/**
	 * Make the object immutable so it can be shared safely.  If I was mutable I
	 * have to scan my children and make them immutable as well (recursively
	 * down to immutable descendants).
	 */
	@Override
	public AvailObject o_MakeImmutable (
			final AvailObject object)
	{

		object.descriptor(immutable());
		// Don't bother scanning subobjects. They're allowed to be mutable even
		// when object is immutable.
		return object;
	}



	@Override
	public AvailObject o_BundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts,
			final AvailObject instructions)
	{
		//  Answer the bundle with the given message (also split into parts).

		final int pc = object.parsingPc();
		if (pc == instructions.tupleSize() + 1)
		{
			final AvailObject complete = object.complete();
			assert complete.hasKey(message)
			: "That message is not in the bundle tree";
			return complete.mapAt(message);
		}
		//  It should be in some subtree.
		final AvailObject instruction = instructions.tupleAt(pc);
		final int instructionInt = instructions.tupleAt(pc).extractInt();
		final int keywordIndex =
			MessageSplitter.keywordIndexFromInstruction(instructionInt);
		if (keywordIndex != 0)
		{
			// This is a keyword parse instruction.  Look up the indicated
			// keyword.
			AvailObject keyword = parts.tupleAt(keywordIndex);
			final AvailObject incomplete = object.incomplete();
			assert incomplete.hasKey(keyword)
			: "That message is not in the bundle tree (keyword)";
			return incomplete.mapAt(keyword).bundleAtMessageParts(
				message,
				parts,
				instructions);
		}
		// This is not a keyword parse instruction.  Look up the parsing action
		// in the specialActions table.
		final AvailObject specials = object.specialActions();
		assert specials.hasKey(instruction)
		: "That message is not in the bundle tree (special action)";
		return object.specialActions().mapAt(instruction).bundleAtMessageParts(
			message,
			parts,
			instructions);
	}

	/**
	 * Copy the visible message bundles to the filteredBundleTree.  The Avail
	 * set of visible names (cyclicTypes) is in visibleNames.
	 */
	@Override
	public void o_CopyToRestrictedTo (
			final AvailObject object,
			final AvailObject filteredBundleTree,
			final AvailObject visibleNames)
	{
		object.complete().mapDo(
		new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (final AvailObject message, final AvailObject bundle)
			{
				if (visibleNames.hasElement(message))
				{
					filteredBundleTree.atMessageAddBundle(message, bundle);
				}
			}
		});
		object.incomplete().mapDo(
		new Continuation2<AvailObject, AvailObject>()
		{
			@Override
			public void value (final AvailObject message, final AvailObject subtree)
			{
				if (!subtree.equalsVoid())
				{
					subtree.copyToRestrictedTo(filteredBundleTree, visibleNames);
				}
			}
		});
	}

	/**
	 * If there isn't one already, add a bundle to correspond to the given
	 * message.  Answer the new or existing bundle.
	 */
	@Override
	public AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject messageBundle)
	{
		final int pc = object.parsingPc();
		final AvailObject message = messageBundle.message();
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
				subtree = UnexpandedMessageBundleTreeDescriptor.newPc(pc + 1);
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
					UnexpandedMessageBundleTreeDescriptor.newPc(successorPc));
			}
			subtrees = TupleDescriptor.mutableObjectFromArray(newTrees);
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
			final AvailObject object,
			final AvailObject bundle)
	{
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
					subtree = UnexpandedMessageBundleTreeDescriptor.newPc(
						pc + 1);
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
		return complete.mapSize() == 0
			&& incomplete.mapSize() == 0
			&& object.specialActions().mapSize() == 0;
	}

	@Override
	public AvailObject o_Expand (
			final AvailObject object)
	{
		//  Expand the bundleTree.  In this case, do nothing as I am already expanded.

		return object;
	}

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
