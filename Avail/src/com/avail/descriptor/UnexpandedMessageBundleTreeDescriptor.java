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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ExpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.MessageBundleDescriptor;
import com.avail.descriptor.UnexpandedMessageBundleTreeDescriptor;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots("depth")
@ObjectSlots({
	"unclassified", 
	"pad"
})
public class UnexpandedMessageBundleTreeDescriptor extends MessageBundleTreeDescriptor
{


	// GENERATED accessors

	void ObjectDepth (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectPad (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectUnclassified (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	int ObjectDepth (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	AvailObject ObjectPad (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	AvailObject ObjectUnclassified (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -4)
		{
			return true;
		}
		if (index == 4)
		{
			return true;
		}
		return false;
	}



	// operations

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to
		//  scan my children and make them immutable as well (recursively down to immutable
		//  descendants).

		object.descriptor(UnexpandedMessageBundleTreeDescriptor.immutableDescriptor());
		//  Don't bother scanning subobjects. They're allowed to be mutable even when object is immutable.
		return object;
	}



	// operations-bundleTree

	void ObjectAtMessageAddBundle (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject bundle)
	{
		//  Add the given message/bundle pair.

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

	AvailObject ObjectBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  Answer the bundle with the given message (also split into parts).

		final AvailObject unclassified = object.unclassified();
		assert unclassified.hasKey(message) : "That message is not in the bundle tree";
		return unclassified.mapAt(message);
	}

	void ObjectCopyToRestrictedTo (
			final AvailObject object, 
			final AvailObject filteredBundleTree, 
			final AvailObject visibleNames)
	{
		//  Copy the visible message bundles to the filteredBundleTree.  The Avail set
		//  of visible names (cyclicTypes) is in visibleNames.

		final AvailObject unclassified = object.unclassified();
		AvailObject.lock(unclassified);
		for (int mapIndex = 1, _end1 = unclassified.capacity(); mapIndex <= _end1; mapIndex++)
		{
			final AvailObject message = unclassified.keyAtIndex(mapIndex);
			AvailObject bundle;
			if (!message.equalsVoidOrBlank())
			{
				if (visibleNames.hasElement(message))
				{
					bundle = unclassified.valueAtIndex(mapIndex);
					filteredBundleTree.atMessageAddBundle(message, bundle);
				}
			}
		}
		AvailObject.unlock(unclassified);
	}

	AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  If there isn't one already, add a bundle to correspond to the given message.
		//  Answer the new or existing bundle.

		AvailObject unclassified = object.unclassified();
		if (unclassified.hasKey(message))
		{
			return unclassified.mapAt(message);
		}
		final AvailObject bundle = MessageBundleDescriptor.newMessageParts(message, parts);
		unclassified = unclassified.mapAtPuttingCanDestroy(
			message,
			bundle,
			true);
		object.unclassified(unclassified);
		return bundle;
	}

	boolean ObjectRemoveMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  Remove the bundle with the given message name (expanded as parts).
		//  Answer true if this tree is now empty and should be removed.

		AvailObject unclassified = object.unclassified();
		if (!unclassified.hasKey(message))
		{
			error("Message was not in bundle tree", object);
			return false;
		}
		unclassified = unclassified.mapWithoutKeyCanDestroy(message, true);
		object.unclassified(unclassified);
		return (unclassified.mapSize() == 0);
	}

	AvailObject ObjectComplete (
			final AvailObject object)
	{
		return object.expand().complete();
	}

	AvailObject ObjectExpand (
			final AvailObject object)
	{
		//  Expand the bundleTree.  Answer the resulting expanded tree.

		final AvailObject unclassified = object.unclassified();
		final int depth = object.depth();
		AvailObject complete = MapDescriptor.empty();
		AvailObject incomplete = MapDescriptor.empty();
		AvailObject.lock(unclassified);
		for (int mapIndex = 1, _end1 = unclassified.capacity(); mapIndex <= _end1; mapIndex++)
		{
			final AvailObject message = unclassified.keyAtIndex(mapIndex);
			AvailObject part;
			AvailObject subtree;
			AvailObject bundle;
			AvailObject parts;
			if (!message.equalsVoidOrBlank())
			{
				bundle = unclassified.valueAtIndex(mapIndex);
				parts = bundle.messageParts();
				if ((depth == (parts.tupleSize() + 1)))
				{
					complete = complete.mapAtPuttingCanDestroy(
						message,
						bundle,
						true);
				}
				else
				{
					part = parts.tupleAt(depth);
					if (incomplete.hasKey(part))
					{
						subtree = incomplete.mapAt(part);
					}
					else
					{
						subtree = UnexpandedMessageBundleTreeDescriptor.newDepth(depth + 1);
						incomplete = incomplete.mapAtPuttingCanDestroy(
							part,
							subtree,
							true);
					}
					subtree.atMessageAddBundle(message, bundle);
				}
			}
		}
		AvailObject.unlock(unclassified);
		assert (numberOfFixedObjectSlots() == ExpandedMessageBundleTreeDescriptor.immutableDescriptor().numberOfFixedObjectSlots());
		object.descriptor(ExpandedMessageBundleTreeDescriptor.immutableDescriptor());
		object.complete(complete);
		object.incomplete(incomplete);
		object.depth(depth);
		return object;
	}

	AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		return object.expand().incomplete();
	}





	/* Object creation */
	public static AvailObject newDepth(int depth)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(
			0,
			UnexpandedMessageBundleTreeDescriptor.immutableDescriptor());
		result.unclassified(MapDescriptor.empty());
		result.pad(VoidDescriptor.voidObject());
		result.depth(depth);
		return result;
	};

	/**
	 * Construct a new {@link UnexpandedMessageBundleTreeDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected UnexpandedMessageBundleTreeDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	public static UnexpandedMessageBundleTreeDescriptor mutableDescriptor()
	{
		return (UnexpandedMessageBundleTreeDescriptor) allDescriptors [160];
	}

	public static UnexpandedMessageBundleTreeDescriptor immutableDescriptor()
	{
		return (UnexpandedMessageBundleTreeDescriptor) allDescriptors [161];
	}
}
