/**
 * descriptor/ClosureDescriptor.java
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
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.levelOne.AvailDecompiler;
import com.avail.interpreter.levelOne.L1Instruction;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import java.util.List;
import static java.util.Arrays.*;

@ObjectSlots({
	"code", 
	"outerVarAt#"
})
public class ClosureDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectCode (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectOuterVarAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + -4));
	}

	void ObjectOuterVarAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + -4), value);
	}

	AvailObject ObjectCode (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		if (isMutable)
		{
			aStream.append("Mutable closure with code: ");
		}
		else
		{
			aStream.append("Immutable closure with code: ");
		}
		AvailDecompiler.parse(object).printOnIndent(aStream, indent + 1);
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsClosure(object);
	}

	boolean ObjectEqualsClosure (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		if (!object.code().equals(aClosure.code()))
		{
			return false;
		}
		if (object.numOuterVars() != aClosure.numOuterVars())
		{
			return false;
		}
		for (int i = 1, _end1 = object.numOuterVars(); i <= _end1; i++)
		{
			if (!object.outerVarAt(i).equals(aClosure.outerVarAt(i)))
			{
				return false;
			}
		}
		//  They're equal (but occupy disjoint storage).  Replace one with an indirection to the other
		//  to reduce storage costs and the frequency of detailed comparisons.
		object.becomeIndirectionTo(aClosure);
		aClosure.makeImmutable();
		//  Now that there are at least two references to it
		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Simply asks the compiled code for the closureType.

		return object.code().closureType();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.  If outer vars of mutable
		//  closures can peel away when executed (last use of an outer var of a
		//  mutable closure can clobber that var and replace the outerAt: entry with
		//  0 or something), it's ok because nobody could know what the hash value
		//  *used to be* for this closure.

		int hash = (object.code().hash() ^ 0x1386D4F6);
		for (int i = 1, _end1 = object.numOuterVars(); i <= _end1; i++)
		{
			hash = ((hash * 13) + object.outerVarAt(i).hash());
		}
		return hash;
	}

	boolean ObjectIsClosure (
			final AvailObject object)
	{
		return true;
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that likely can be coalesced together.

		if (!object.code().isHashAvailable())
		{
			return false;
		}
		for (int i = 1, _end1 = object.numOuterVars(); i <= _end1; i++)
		{
			if (!object.outerVarAt(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.  Simply asks the compiled code for the closureType.

		return object.code().closureType();
	}



	// operations-closure

	boolean ObjectContainsBlock (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		//  Answer true if either I am aClosure or I contain aClosure.

		if (object.equals(aClosure))
		{
			return true;
		}
		return object.code().containsBlock(aClosure);
	}

	boolean ObjectOptionallyNilOuterVar (
			final AvailObject object, 
			final int index)
	{
		//  This one's kind of tricky.  An outer variable is being used by the interpreter (the
		//  variable itself, but we don't yet know whether it will be passed around, or sent
		//  the getValue or setValue message, or even just popped.  So don't destroy it
		//  yet.  If this closure is mutable, unlink the outer variable from it (as the closure no
		//  longer needs it in that case).  Answer true if it was mutable, otherwise false, so
		//  the calling code knows what happened.

		if (isMutable)
		{
			object.outerVarAtPut(index, VoidDescriptor.voidObject());
			return true;
		}
		return false;
	}

	int ObjectNumOuterVars (
			final AvailObject object)
	{
		//  Answer how many outer vars I've copied.

		return (object.objectSlotsCount() - numberOfFixedObjectSlots());
	}





	/* Object creation */

	public static AvailObject newStubForNumArgsConstantResult (
			int numArgs,
			AvailObject constantResult)
	{
		L1InstructionWriter writer = new L1InstructionWriter();

		AvailObject [] argTypes = new AvailObject [numArgs];
		fill(argTypes, Types.all.object());
		writer.argumentTypes(argTypes);
		writer.returnType(constantResult.type());

		writer.write(new L1Instruction(L1Operation.L1_doPushLiteral, writer.addLiteral(constantResult)));

		AvailObject code = writer.compiledCode();
		AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple (
			code,
			TupleDescriptor.empty());
		closure.makeImmutable();
		return closure;
	};

	public static AvailObject newStubCollectingArgsWithTypesIntoAListAndSendingImplementationSetFirstArgumentResultType (
			AvailObject argTypes,
			AvailObject implementationSet,
			AvailObject firstArg,
			AvailObject resultType)
	{
		int numArgs = argTypes.tupleSize();
		AvailObject [] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			assert argTypes.tupleAt(i).isInstanceOfSubtypeOf(Types.type.object());
			argTypesArray[i - 1] = argTypes.tupleAt(i);
		}

		L1InstructionWriter writer = new L1InstructionWriter();
		writer.argumentTypes(argTypesArray);
		writer.returnType(resultType);
		writer.write(new L1Instruction(L1Operation.L1_doPushLiteral, writer.addLiteral(firstArg)));
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(new L1Instruction(L1Operation.L1_doPushLastLocal, i));
		}
		writer.write(new L1Instruction(L1Operation.L1Ext_doMakeList, numArgs));
		writer.write(new L1Instruction(L1Operation.L1_doCall, writer.addLiteral(implementationSet)));
		writer.write(new L1Instruction(L1Operation.L1_doVerifyType, writer.addLiteral(resultType)));

		AvailObject code = writer.compiledCode();

		AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple (
			code,
			TupleDescriptor.empty());
		closure.makeImmutable();
		return closure;
	}


	public static AvailObject newMutableObjectWithCodeAndCopiedTuple (
			AvailObject code,
			AvailObject copiedTuple)
	{
		//  Construct a closure with the given code and tuple of copied variables.
		AvailObject object = AvailObject.newIndexedDescriptor (
			copiedTuple.tupleSize(),
			ClosureDescriptor.mutableDescriptor());
		object.code (code);
		for (int i = copiedTuple.tupleSize(); i >= 1; -- i)
			object.outerVarAtPut (i, copiedTuple.tupleAt (i));
		return object;
	};

	/**
	 * Construct a new {@link ClosureDescriptor}.
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
	protected ClosureDescriptor (
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

	public static ClosureDescriptor mutableDescriptor()
	{
		return (ClosureDescriptor) allDescriptors [26];
	}

	public static ClosureDescriptor immutableDescriptor()
	{
		return (ClosureDescriptor) allDescriptors [27];
	}
}
