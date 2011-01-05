/**
 * descriptor/UnexpandedMessageBundleTreeDescriptor.java
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

import static com.avail.descriptor.AvailObject.error;
import java.util.*;
import com.avail.compiler.MessageSplitter;
import com.avail.utility.*;

public class UnexpandedMessageBundleTreeDescriptor extends MessageBundleTreeDescriptor
{

	public enum IntegerSlots
	{
		PARSING_PC
	}

	public enum ObjectSlots
	{
		UNCLASSIFIED,
		PAD1,
		PAD2
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
	 * Setter for field pad1.
	 */
	@Override
	public void o_Pad1 (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PAD1, value);
	}

	/**
	 * Setter for field pad2.
	 */
	@Override
	public void o_Pad2 (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PAD2, value);
	}

	/**
	 * Setter for field unclassified.
	 */
	@Override
	public void o_Unclassified (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.UNCLASSIFIED, value);
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
	 * Getter for field pad1.
	 */
	@Override
	public AvailObject o_Pad1 (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PAD1);
	}

	/**
	 * Getter for field pad2.
	 */
	@Override
	public AvailObject o_Pad2 (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PAD2);
	}

	/**
	 * Getter for field unclassified.
	 */
	@Override
	public AvailObject o_Unclassified (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.UNCLASSIFIED);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == ObjectSlots.UNCLASSIFIED)
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
		object.descriptor(
			UnexpandedMessageBundleTreeDescriptor.immutable());
		// Don't bother scanning subobjects. They're allowed to be mutable even
		// when object is immutable.
		return object;
	}


	/**
	 * Add the given message/bundle pair.
	 */
	@Override
	public void o_AtMessageAddBundle (
			final AvailObject object,
			final AvailObject message,
			final AvailObject bundle)
	{
		AvailObject unclassified = object.unclassified();
		if (unclassified.hasKey(message))
		{
			error("Message is already in bundle tree", object);
			return;
		}
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			bundle,
			true);
		object.unclassified(unclassified);
	}

	/**
	 * Answer the bundle with the given message (also split into parts).
	 */
	@Override
	public AvailObject o_BundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts,
			final AvailObject instructions)
	{
		final AvailObject unclassified = object.unclassified();
		assert unclassified.hasKey(message)
		: "That message is not in the bundle tree";
		return unclassified.mapAt(message);
	}

	@Override
	public void o_CopyToRestrictedTo (
			final AvailObject object,
			final AvailObject filteredBundleTree,
			final AvailObject visibleNames)
	{
		//  Copy the visible message bundles to the filteredBundleTree.  The Avail set
		//  of visible names (cyclicTypes) is in visibleNames.

		final AvailObject unclassified = object.unclassified();
		unclassified.mapDo(new Continuation2<AvailObject, AvailObject>()
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
		AvailObject unclassified = object.unclassified();
		final AvailObject message = messageBundle.message();
		if (unclassified.hasKey(message))
		{
			return unclassified.mapAt(message);
		}
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			messageBundle,
			true);
		object.unclassified(unclassified);
		return messageBundle;
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
		AvailObject unclassified = object.unclassified();
		unclassified = unclassified.mapWithoutKeyCanDestroy(
			bundle.message(),
			true);
		object.unclassified(unclassified);
		return unclassified.mapSize() == 0;
	}

	@Override
	public AvailObject o_Complete (
			final AvailObject object)
	{
		return object.expand().complete();
	}


	/**
	 * Expand the bundleTree.  Answer the resulting expanded tree.
	 */
	@Override
	public AvailObject o_Expand (
			final AvailObject object)
	{
		final AvailObject unclassified = object.unclassified();
		final Mutable<AvailObject> complete = new Mutable<AvailObject>(
			MapDescriptor.empty());
		final Mutable<AvailObject> incomplete = new Mutable<AvailObject>(
				MapDescriptor.empty());
		final Mutable<AvailObject> specialMap = new Mutable<AvailObject>(
				MapDescriptor.empty());
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
							IntegerDescriptor.objectFromInt(instruction);
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
							successors = TupleDescriptor.mutableObjectFromList(
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
		assert numberOfFixedObjectSlots() == ExpandedMessageBundleTreeDescriptor.immutable().numberOfFixedObjectSlots();
		object.descriptor(
			ExpandedMessageBundleTreeDescriptor.immutable());
		object.complete(complete.value);
		object.incomplete(incomplete.value);
		object.parsingPc(pc);
		object.specialActions(specialMap.value);
		return object;
	}

	@Override
	public AvailObject o_Incomplete (
			final AvailObject object)
	{
		return object.expand().incomplete();
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
		result.unclassified(MapDescriptor.empty());
		result.pad1(VoidDescriptor.voidObject());
		result.pad2(VoidDescriptor.voidObject());
		result.parsingPc(pc);
		result.makeImmutable();
		return result;
	};

	/**
	 * Construct a new {@link UnexpandedMessageBundleTreeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected UnexpandedMessageBundleTreeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 */
	private final static UnexpandedMessageBundleTreeDescriptor mutable = new UnexpandedMessageBundleTreeDescriptor(true);

	/**
	 * Answer the mutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The mutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 */
	public static UnexpandedMessageBundleTreeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 */
	private final static UnexpandedMessageBundleTreeDescriptor immutable = new UnexpandedMessageBundleTreeDescriptor(false);

	/**
	 * Answer the immutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The immutable {@link UnexpandedMessageBundleTreeDescriptor}.
	 */
	public static UnexpandedMessageBundleTreeDescriptor immutable ()
	{
		return immutable;
	}
}
