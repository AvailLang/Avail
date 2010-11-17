/**
 * descriptor/SignatureDescriptor.java
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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public abstract class SignatureDescriptor extends Descriptor
{


	// accessing

	AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter)
	{
		//  Determine the return type for a call site invoking this method or an override of it
		//  (which is also constrained to support any specializations declared at this level).

		error("Subclass responsibility: Object:computeReturnTypeFromArgumentTypes:interpreter: in Avail.SignatureDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter interpreter)
	{
		//  Determine if these argument types are appropriate at a call site.

		error("Subclass responsibility: Object:isValidForArgumentTypes:interpreter: in Avail.SignatureDescriptor", object);
		return false;
	}

	AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		//  Answer a closureType whose argument types reflect the position in the
		//  multi-method hierarchy where this method resides.  The closureType's
		//  return type is the return type promised when invoking this method with
		//  matching arguments.

		error("Subclass responsibility: ObjectBodySignature: in Avail.SignatureDescriptor", object);
		return VoidDescriptor.voidObject();
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Compare by address (identity) for now.  Eventually we can introduce value semantics.

		return another.traversed().sameAddressAs(object);
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  I'm abstract and my subclasses are concrete, so they
		//  should designate their own types.

		error("Subclass responsibility: ObjectExactType: in Avail.SignatureDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.  I'm abstract and my subclasses are concrete, so they
		//  should designate their own types.

		error("Subclass responsibility: ObjectType: in Avail.SignatureDescriptor", object);
		return VoidDescriptor.voidObject();
	}



	// testing

	boolean ObjectIsAbstract (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsForward (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsImplementation (
			final AvailObject object)
	{
		return false;
	}



	// validation

	void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		//  Make sure my requires clauses and returns clauses are expecting the
		//  right types, based on the declaration of the body.  Defaulted here, but
		//  subclasses may need to override.

		final AvailObject sig = object.bodySignature();
		final AvailObject req = object.requiresBlock().type();
		final AvailObject ret = object.returnsBlock().type();
		assert (req.numArgs() == sig.numArgs()) : "Wrong number of arguments in requires block";
		assert (ret.numArgs() == sig.numArgs()) : "Wrong number of arguments in returns block.";
		assert req.returnType().isSubtypeOf(Types.booleanType.object()) : "Wrong return type in requires block";
		assert ret.returnType().isSubtypeOf(Types.type.object()) : "Wrong return type in returns block";
		for (int i = 1, _end1 = sig.numArgs(); i <= _end1; i++)
		{
			final AvailObject bodyType = sig.argTypeAt(i);
			final AvailObject bodyMeta = bodyType.type();
			if (! bodyMeta.isSubtypeOf(req.argTypeAt(i)))
			{
				error("Argument of requires clause was not metacovariant");
			}
			if (! bodyMeta.isSubtypeOf(ret.argTypeAt(i)))
			{
				error("Argument of returns clause was not metacovariant");
			}
		}
	}

	/**
	 * Construct a new {@link SignatureDescriptor}.
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
	protected SignatureDescriptor (
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
}
