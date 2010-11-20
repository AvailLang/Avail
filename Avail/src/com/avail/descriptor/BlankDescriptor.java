/**
 * descriptor/BlankDescriptor.java
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
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public class BlankDescriptor extends Descriptor
{


	// java printing

	@Override
	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append("Blank");
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsBlank();
	}

	@Override
	public boolean ObjectEqualsBlank (
			final AvailObject object)
	{
		//  There is only one blank.

		return true;
	}

	@Override
	public boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		error("The blank object has no type", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer the object's hash.  The blank object should hash to zero, because the
		//  only place it can appear in a data structure is as a filler object.  The blank object
		//  may only appear in sets and maps.

		return 0;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		error("The blank object has no type", object);
		return VoidDescriptor.voidObject();
	}




	// Startup/shutdown

	static AvailObject SoleInstance;

	static void createWellKnownObjects ()
	{
		SoleInstance = AvailObject.newIndexedDescriptor(0, immutableDescriptor());
	}

	static void clearWellKnownObjects ()
	{
		//  Clear the SoleInstance variable as well as the default stuff.

		SoleInstance = null;
	}



	/* Object creation */
	static AvailObject blank ()
	{
		return SoleInstance;
	};

	/**
	 * Construct a new {@link BlankDescriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected BlankDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static BlankDescriptor mutableDescriptor = new BlankDescriptor(true);

	public static BlankDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static BlankDescriptor immutableDescriptor = new BlankDescriptor(false);

	public static BlankDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
