/**
 * descriptor/VoidDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;

public class VoidDescriptor extends Descriptor
{


	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append("VoidDescriptor void");
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsVoid();
	}

	boolean ObjectEqualsVoid (
			final AvailObject object)
	{
		//  There is only one void.

		return true;
	}

	boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.voidType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash.  The void object should hash to zero, because the
		//  only place it can appear in a data structure is as a filler object.  This currently
		//  (as of July 1998) applies to sets, maps, containers, and continuations.

		return 0;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.voidType();
	}



	// operations-new sets

	AvailObject ObjectBinAddingElementHashLevelCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final byte myLevel, 
			final boolean canDestroy)
	{
		//  The voidObject can't be an actual member of a set, so if one receives this message
		//  it must be the rootBin of a set (empty by definition).  Answer the new element, which
		//  will become the new rootBin, indicating a set of size one.

		if (! canDestroy)
		{
			elementObject.makeImmutable();
		}
		return elementObject;
	}

	boolean ObjectIsBinSubsetOf (
			final AvailObject object, 
			final AvailObject potentialSuperset)
	{
		//  Void can't actually be a member of a set, so treat it as a
		//  structural component indicating an empty bin within a set.
		//  Since it's empty, it is a subset of potentialSuperset.

		return true;
	}



	// operations-set bins

	AvailObject ObjectBinRemoveElementHashCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final boolean canDestroy)
	{
		//  Remove elementObject from the bin object, if present.  Answer the resulting bin.  The bin
		//  may be modified if it's mutable and canDestroy.  In particular, this is voidObject acting as
		//  a bin of size zero, so the answer must be voidObject.

		return VoidDescriptor.voidObject();
	}

	int ObjectPopulateTupleStartingAt (
			final AvailObject object, 
			final AvailObject mutableTuple, 
			final int startingIndex)
	{
		//  Write set bin elements into the tuple, starting at the given startingIndex.  Answer
		//  the next available index in which to write.  The voidObject acts as an empty bin,
		//  so do nothing.

		assert mutableTuple.descriptor().isMutable();
		return startingIndex;
	}

	int ObjectBinHash (
			final AvailObject object)
	{
		//  A voidObject acting as a size zero bin has a bin hash which is the sum of
		//  the elements' hashes, which in this case is zero.

		return 0;
	}

	int ObjectBinSize (
			final AvailObject object)
	{
		//  Answer how many elements this bin contains.  I act as an empty bin.

		return 0;
	}

	AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		//  Answer the union of the types of this bin's elements.  I act as a bin of size zero.

		return TypeDescriptor.terminates();
	}




	// Startup/shutdown

	static AvailObject SoleInstance;

	static void createWellKnownObjects ()
	{
		//  Clear the SoleInstance variable as well as the default stuff.

		SoleInstance = AvailObject.newIndexedDescriptor(0, immutableDescriptor());
	}

	static void clearWellKnownObjects ()
	{
		//  Clear the SoleInstance variable as well as the default stuff.

		SoleInstance = null;
	}



	public static AvailObject voidObject ()
	{
		return SoleInstance;
	};


	/* Descriptor lookup */
	public static VoidDescriptor mutableDescriptor()
	{
		return (VoidDescriptor) AllDescriptors [162];
	};
	public static VoidDescriptor immutableDescriptor()
	{
		return (VoidDescriptor) AllDescriptors [163];
	};

}
