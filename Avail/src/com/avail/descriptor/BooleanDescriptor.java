/**
 * descriptor/BooleanDescriptor.java
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
import com.avail.descriptor.FalseDescriptor;
import com.avail.descriptor.TrueDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public abstract class BooleanDescriptor extends Descriptor
{


	// java printing

	@Override
	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append(object.extractBoolean());
	}



	// operations-booleans

	@Override
	public boolean ObjectExtractBoolean (
			final AvailObject object)
	{
		//  Extract a Smalltalk Boolean from object.

		error("Subclass responsibility: ObjectExtractBoolean: in Avail.BooleanDescriptor", object);
		return false;
	}

	@Override
	public boolean ObjectIsBoolean (
			final AvailObject object)
	{
		return true;
	}




	// Startup/shutdown

	static AvailObject TrueBooleanObject;


	static AvailObject FalseBooleanObject;

	static void createWellKnownObjects ()
	{
		TrueBooleanObject = AvailObject.newIndexedDescriptor(0, TrueDescriptor.immutableDescriptor());
		FalseBooleanObject = AvailObject.newIndexedDescriptor(0, FalseDescriptor.immutableDescriptor());
	}

	static void clearWellKnownObjects ()
	{
		TrueBooleanObject = null;
		FalseBooleanObject = null;
	}



	/* Object creation */
	public static AvailObject objectFromBoolean (boolean b)
	{
		return b ? TrueBooleanObject : FalseBooleanObject;
	};

	/**
	 * Construct a new {@link BooleanDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected BooleanDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
