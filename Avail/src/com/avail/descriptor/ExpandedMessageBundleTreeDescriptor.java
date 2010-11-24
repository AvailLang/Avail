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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ExpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.MessageBundleDescriptor;
import com.avail.descriptor.UnexpandedMessageBundleTreeDescriptor;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots("depth")
@ObjectSlots({
	"complete",
	"incomplete",
	"specialActions"
})
public class ExpandedMessageBundleTreeDescriptor extends MessageBundleTreeDescriptor
{


	// GENERATED accessors

	/**
	 * Setter for field complete.
	 */
	@Override
	public void ObjectComplete (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field depth.
	 */
	@Override
	public void ObjectDepth (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field incomplete.
	 */
	@Override
	public void ObjectIncomplete (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field specialActions.
	 */
	@Override
	public void ObjectSpecialActions (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Getter for field complete.
	 */
	@Override
	public AvailObject ObjectComplete (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field depth.
	 */
	@Override
	public int ObjectDepth (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field incomplete.
	 */
	@Override
	public AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field specialActions.
	 */
	@Override
	public AvailObject ObjectSpecialActions (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}



	// GENERATED special mutable slots

	@Override
	public boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -4)
		{
			return true;
		}
		if (index == -8)
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

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to
		//  scan my children and make them immutable as well (recursively down to immutable
		//  descendants).

		object.descriptor(ExpandedMessageBundleTreeDescriptor.immutableDescriptor());
		//  Don't bother scanning subobjects. They're allowed to be mutable even when object is immutable.
		return object;
	}



	// operations-bundleTree

	@Override
	public void ObjectAtMessageAddBundle (
			final AvailObject object,
			final AvailObject message,
			final AvailObject bundle)
	{
		//  Add the given message/bundle pair.

		final int depth = object.depth();
		final AvailObject parts = bundle.messageParts();
		if ((depth == (parts.tupleSize() + 1)))
		{
			AvailObject complete = object.complete();
			if (complete.hasKey(message))
			{
				error("That method is already in the bundle tree.", object);
				return;
			}
			complete = complete.mapAtPuttingCanDestroy(
				message,
				bundle,
				true);
			object.complete(complete);
		}
		else
		{
			final AvailObject part = parts.tupleAt(depth);
			AvailObject incomplete = object.incomplete();
			AvailObject subtree;
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
				object.incomplete(incomplete);
			}
			subtree.atMessageAddBundle(message, bundle);
		}
	}

	@Override
	public AvailObject ObjectBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		//  Answer the bundle with the given message (also split into parts).

		final int depth = object.depth();
		if ((depth == (parts.tupleSize() + 1)))
		{
			final AvailObject complete = object.complete();
			assert complete.hasKey(message) : "That message is not in the bundle tree";
			return complete.mapAt(message);
		}
		//  It should be in some subtree.
		final AvailObject part = parts.tupleAt(depth);
		final AvailObject incomplete = object.incomplete();
		assert incomplete.hasKey(part) : "That message is not in the bundle tree";
		return incomplete.mapAt(part).bundleAtMessageParts(message, parts);
	}

	@Override
	public void ObjectCopyToRestrictedTo (
			final AvailObject object,
			final AvailObject filteredBundleTree,
			final AvailObject visibleNames)
	{
		//  Copy the visible message bundles to the filteredBundleTree.  The Avail set
		//  of visible names (cyclicTypes) is in visibleNames.

		final AvailObject complete = object.complete();
		AvailObject.lock(complete);
		for (int mapIndex = 1, _end1 = complete.capacity(); mapIndex <= _end1; mapIndex++)
		{
			final AvailObject message = complete.keyAtIndex(mapIndex);
			AvailObject bundle;
			if (!message.equalsVoidOrBlank())
			{
				if (visibleNames.hasElement(message))
				{
					bundle = complete.valueAtIndex(mapIndex);
					filteredBundleTree.atMessageAddBundle(message, bundle);
				}
			}
		}
		AvailObject.unlock(complete);
		final AvailObject incomplete = object.incomplete();
		AvailObject.lock(incomplete);
		for (int mapIndex = 1, _end2 = incomplete.capacity(); mapIndex <= _end2; mapIndex++)
		{
			final AvailObject subtree = incomplete.valueAtIndex(mapIndex);
			if (!subtree.equalsVoid())
			{
				subtree.copyToRestrictedTo(filteredBundleTree, visibleNames);
			}
		}
		AvailObject.unlock(incomplete);
	}

	@Override
	public AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		//  If there isn't one already, add a bundle to correspond to the given message.
		//  Answer the new or existing bundle.

		final int depth = object.depth();
		if ((depth == (parts.tupleSize() + 1)))
		{
			AvailObject complete = object.complete();
			if (complete.hasKey(message))
			{
				return complete.mapAt(message);
			}
			final AvailObject bundle = MessageBundleDescriptor.newMessageParts(message, parts);
			complete = complete.mapAtPuttingCanDestroy(
				message,
				bundle,
				true);
			object.complete(complete);
			return bundle;
		}
		//  It should be in some subtree.
		final AvailObject part = parts.tupleAt(depth);
		AvailObject incomplete = object.incomplete();
		AvailObject subtree;
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
			object.incomplete(incomplete);
		}
		return subtree.includeBundleAtMessageParts(message, parts);
	}

	@Override
	public boolean ObjectRemoveMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		//  Remove the bundle with the given message name (expanded as parts).
		//  Answer true if this tree is now empty and should be removed.

		final int depth = object.depth();
		AvailObject complete = object.complete();
		AvailObject incomplete = object.incomplete();
		if ((depth == (parts.tupleSize() + 1)))
		{
			complete = complete.mapWithoutKeyCanDestroy(message, true);
			object.complete(complete);
		}
		else
		{
			final AvailObject part = parts.tupleAt(depth);
			if (object.incomplete().hasKey(part))
			{
				final AvailObject subtree = object.incomplete().mapAt(part);
				final boolean prune = subtree.removeMessageParts(message, parts);
				if (prune)
				{
					incomplete = incomplete.mapWithoutKeyCanDestroy(part, true);
					object.incomplete(incomplete);
				}
			}
		}
		return ((complete.mapSize() == 0) && (object.incomplete().mapSize() == 0));
	}

	@Override
	public AvailObject ObjectExpand (
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
	private final static ExpandedMessageBundleTreeDescriptor mutableDescriptor = new ExpandedMessageBundleTreeDescriptor(true);

	/**
	 * Answer the mutable {@link ExpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The mutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	public static ExpandedMessageBundleTreeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	private final static ExpandedMessageBundleTreeDescriptor immutableDescriptor = new ExpandedMessageBundleTreeDescriptor(false);

	/**
	 * Answer the immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 *
	 * @return The immutable {@link ExpandedMessageBundleTreeDescriptor}.
	 */
	public static ExpandedMessageBundleTreeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
