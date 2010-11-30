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

public class ClosureDescriptor extends Descriptor
{

	enum ObjectSlots
	{
		CODE,
		OUTER_VAR_AT_
	}


	/**
	 * Setter for field code.
	 */
	@Override
	public void ObjectCode (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CODE, value);
	}

	@Override
	public AvailObject ObjectOuterVarAt (
			final AvailObject object,
			final int subscript)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAt(ObjectSlots.OUTER_VAR_AT_, subscript);
	}

	@Override
	public void ObjectOuterVarAtPut (
			final AvailObject object,
			final int subscript,
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtPut(ObjectSlots.OUTER_VAR_AT_, subscript, value);
	}

	/**
	 * Getter for field code.
	 */
	@Override
	public AvailObject ObjectCode (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CODE);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
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

	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsClosure(object);
	}

	@Override
	public boolean ObjectEqualsClosure (
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

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Simply asks the compiled code for the closureType.

		return object.code().closureType();
	}

	@Override
	public int ObjectHash (
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

	@Override
	public boolean ObjectIsClosure (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Answer whether this object's hash value can be computed without creating
	 * new objects.  This method is used by the garbage collector to decide
	 * which objects to attempt to coalesce.  The garbage collector uses the
	 * hash values to find objects that likely can be coalesced together.
	 */
	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
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

	/**
	 * Answer the object's type.  Simply asks the compiled code for the
	 * closureType.
	 */
	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return object.code().closureType();
	}



	// operations-closure

	@Override
	public boolean ObjectContainsBlock (
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

	@Override
	public boolean ObjectOptionallyNilOuterVar (
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

	/**
	 * Answer how many outer vars I've copied.
	 */
	@Override
	public int ObjectNumOuterVars (
			final AvailObject object)
	{
		return object.objectSlotsCount() - numberOfFixedObjectSlots();
	}

	
	/**
	 * Create a closure that accepts a specific number of arguments and always
	 * returns the specified constant value.  The arguments may be of any type
	 * (except void or terminates), and in fact the closure types them as "all".
	 * 
	 * @param numArgs The number of arguments to accept.
	 * @param constantResult The constant that the new closure should always
	 *                       produce.
	 * @return A closure that takes N arguments and returns a constant.
	 */
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

	/**
	 * Create a closure that takes arguments of the specified types, then turns
	 * around and sends a specific two-argument message.  The first argument of
	 * that message is specified here, and the second argument is a list of the
	 * arguments supplied to the closure we're creating.  Ensure the send
	 * returns a value that complies with resultType.
	 * 
	 * @param argTypes The types of arguments the new closure should accept.
	 * @param implementationSet The two-argument message the new closure should
	 *                          invoke.
	 * @param firstArg The first argument to send to the two-argument message.
	 * @param resultType The type that the invocation (and the new closure)
	 *                   should return.
	 * @return A closure which accepts arguments of the given types and produces
	 *         a value of the specified type.
	 */
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
			assert argTypes.tupleAt(i).isInstanceOfSubtypeOf(
				Types.type.object());
			argTypesArray[i - 1] = argTypes.tupleAt(i);
		}

		L1InstructionWriter writer = new L1InstructionWriter();
		writer.argumentTypes(argTypesArray);
		writer.returnType(resultType);
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(firstArg)));
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(new L1Instruction(L1Operation.L1_doPushLastLocal, i));
		}
		writer.write(new L1Instruction(L1Operation.L1Ext_doMakeList, numArgs));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(implementationSet)));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doVerifyType,
				writer.addLiteral(resultType)));

		AvailObject code = writer.compiledCode();

		AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple (
			code,
			TupleDescriptor.empty());
		closure.makeImmutable();
		return closure;
	}


	/**
	 * Construct a closure with the given code and tuple of copied variables.
	 * 
	 * @param code The code with which to build the closure.
	 * @param copiedTuple The outer variables and constants to enclose.
	 * @return A closure.
	 */
	public static AvailObject newMutableObjectWithCodeAndCopiedTuple (
			AvailObject code,
			AvailObject copiedTuple)
	{
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
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ClosureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
	
	/**
	 * The mutable {@link ClosureDescriptor}.
	 */
	private final static ClosureDescriptor mutableDescriptor = new ClosureDescriptor(true);

	/**
	 * Answer the mutable {@link ClosureDescriptor}.
	 *
	 * @return The mutable {@link ClosureDescriptor}.
	 */
	public static ClosureDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ClosureDescriptor}.
	 */
	private final static ClosureDescriptor immutableDescriptor = new ClosureDescriptor(false);

	/**
	 * Answer the immutable {@link ClosureDescriptor}.
	 *
	 * @return The immutable {@link ClosureDescriptor}.
	 */
	public static ClosureDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
