/**
 * FunctionDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.util.List;
import com.avail.annotations.*;
import com.avail.interpreter.levelOne.*;
import com.avail.serialization.SerializerOperation;

/**
 * A function associates {@linkplain CompiledCodeDescriptor compiled code} with
 * a referencing environment that binds the code's free variables to variables
 * defined in an outer lexical scope. In this way, a function constitutes a
 * proper closure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class FunctionDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/** The {@linkplain CompiledCodeDescriptor compiled code}. */
		CODE,

		/** The outer variables. */
		OUTER_VAR_AT_
	}

	@Override
	void o_Code (final AvailObject object, final AvailObject value)
	{
		object.setSlot(ObjectSlots.CODE, value);
	}

	@Override
	AvailObject o_OuterVarAt (final AvailObject object, final int subscript)
	{
		return object.slot(ObjectSlots.OUTER_VAR_AT_, subscript);
	}

	@Override
	void o_OuterVarAtPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(ObjectSlots.OUTER_VAR_AT_, subscript, value);
	}

	@Override
	AvailObject o_Code (final AvailObject object)
	{
		return object.slot(ObjectSlots.CODE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		if (isMutable())
		{
			aStream.append("Mutable function with code: ");
		}
		else
		{
			aStream.append("Immutable function with code: ");
		}
		L1Decompiler.parse(object).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
	}

	@Override
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.equalsFunction(object);
	}

	@Override
	boolean o_EqualsFunction (
		final AvailObject object,
		final AvailObject aFunction)
	{
		if (!object.code().equals(aFunction.code()))
		{
			return false;
		}
		if (object.numOuterVars() != aFunction.numOuterVars())
		{
			return false;
		}
		for (int i = 1, end = object.numOuterVars(); i <= end; i++)
		{
			if (!object.outerVarAt(i).equals(aFunction.outerVarAt(i)))
			{
				return false;
			}
		}
		// They're equal, but occupy disjoint storage. If possible, then replace
		// one with an indirection to the other to reduce storage costs and the
		// frequency of detailed comparisons.
		if (!isShared())
		{
			aFunction.makeImmutable();
			object.becomeIndirectionTo(aFunction);
		}
		else if (!aFunction.descriptor.isShared())
		{
			object.makeImmutable();
			aFunction.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override
	int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit hash value. If outer vars of mutable functions can
		// peel away when executed (last use of an outer var of a mutable
		// function can clobber that var and replace the OUTER_VAR_AT_ entry
		// with 0 or something), it's ok because nobody could know what the hash
		// value *used to be* for this function.

		final AvailObject code = object.slot(ObjectSlots.CODE);
		int hash = code.hash() ^ 0x1386D4F6;
		for (int i = 1, end = object.numOuterVars(); i <= end; i++)
		{
			hash = hash * 13 + object.outerVarAt(i).hash();
		}
		return hash;
	}

	@Override
	boolean o_IsFunction (final AvailObject object)
	{
		return true;
	}

	/**
	 * Answer the object's type. Simply asks the {@linkplain
	 * CompiledCodeDescriptor compiled code} for the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 */
	@Override
	AvailObject o_Kind (final AvailObject object)
	{
		return object.slot(ObjectSlots.CODE).functionType();
	}

	@Override
	boolean o_ContainsBlock (
		final AvailObject object,
		final AvailObject aFunction)
	{
		//  Answer true if either I am aFunction or I contain aFunction.

		if (object.equals(aFunction))
		{
			return true;
		}
		return object.slot(ObjectSlots.CODE).containsBlock(aFunction);
	}

	@Override
	boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		// This one's kind of tricky. An outer variable is being used by the
		// interpreter (the variable itself, but we don't yet know whether it
		// will be passed around, or sent the getValue or setValue message, or
		// even just popped. So don't destroy it yet. If this function is
		// mutable, unlink the outer variable from it (as the function no longer
		// needs it in that case). Answer true if it was mutable, otherwise
		// false, so the calling code knows what happened.

		if (isMutable())
		{
			object.outerVarAtPut(index, NilDescriptor.nil());
			return true;
		}
		return false;
	}

	/**
	 * Answer how many outer vars I've copied.
	 */
	@Override
	int o_NumOuterVars (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	@Override
	@AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		if (object.numOuterVars() == 0)
		{
			return SerializerOperation.CLEAN_FUNCTION;
		}
		return SerializerOperation.GENERAL_FUNCTION;
	}

	@Override
	String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + ": "
			+ object.code().methodName();
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return true;
	}

	/**
	 * Create a function that takes arguments of the specified types, then turns
	 * around and calls the function invocation method with the given function
	 * and the passed arguments assembled into a tuple.
	 *
	 * @param functionType
	 *        The type to which the resultant function should conform.
	 * @param function
	 *        The function which the new function should invoke when itself
	 *        invoked.
	 * @return Any appropriate function.
	 */
	public static AvailObject createStubWithArgTypes (
		final AvailObject functionType,
		final AvailObject function)
	{
		final AvailObject argTypes = functionType.argsTupleType();
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final AvailObject[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final AvailObject returnType = functionType.returnType();
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.argumentTypes(argTypesArray);
		writer.returnType(returnType);
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(function)));
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(new L1Instruction(L1Operation.L1_doPushLastLocal, i));
		}
		writer.write(new L1Instruction(L1Operation.L1_doMakeTuple, numArgs));
		final AvailObject method =
			MethodDescriptor.vmFunctionApplyMethod();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(method),
				writer.addLiteral(returnType)));
		final AvailObject code = writer.compiledCode();
		final AvailObject newFunction = FunctionDescriptor.create(
			code,
			TupleDescriptor.empty());
		newFunction.makeImmutable();
		return newFunction;
	}

	/**
	 * Given a function f, create another function that takes a single argument,
	 * a tuple, and invokes f with the elements of that tuple, returning the
	 * result.  The new function's sole argument type should match the types
	 * expected by f.
	 *
	 * @param function
	 *        The function which the new function should invoke.
	 * @return An appropriate function of one argument.
	 */
	public static AvailObject createStubTakingTupleFrom (
		final AvailObject function)
	{
		final AvailObject argTypes = function.functionType().argsTupleType();
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final AvailObject[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final AvailObject tupleType =
			TupleTypeDescriptor.forTypes(argTypesArray);
		final AvailObject returnType = function.functionType().returnType();
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.argumentTypes(new AvailObject[] {tupleType});
		writer.returnType(returnType);
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(function)));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLastLocal,
				1));
		final AvailObject method =
			MethodDescriptor.vmFunctionApplyMethod();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(method),
				writer.addLiteral(returnType)));
		final AvailObject code = writer.compiledCode();
		final AvailObject newFunction = FunctionDescriptor.create(
			code,
			TupleDescriptor.empty());
		newFunction.makeImmutable();
		return newFunction;
	}

	/**
	 * Construct a function with the given code and tuple of copied variables.
	 *
	 * @param code The code with which to build the function.
	 * @param copiedTuple The outer variables and constants to enclose.
	 * @return A function.
	 */
	public static AvailObject create (
		final AvailObject code,
		final AvailObject copiedTuple)
	{
		final AvailObject object = mutable.create(copiedTuple.tupleSize());
		object.code(code);
		for (int i = copiedTuple.tupleSize(); i >= 1; -- i)
		{
			object.outerVarAtPut(i, copiedTuple.tupleAt(i));
		}
		return object;
	}

	/**
	 * Construct a new {@link FunctionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FunctionDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link FunctionDescriptor}. */
	public static final FunctionDescriptor mutable =
		new FunctionDescriptor(Mutability.MUTABLE);

	@Override
	FunctionDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FunctionDescriptor}. */
	private static final FunctionDescriptor immutable =
		new FunctionDescriptor(Mutability.IMMUTABLE);

	@Override
	FunctionDescriptor immutable ()
	{
		return immutable;
	}


	/** The shared {@link FunctionDescriptor}. */
	private static final FunctionDescriptor shared =
		new FunctionDescriptor(Mutability.SHARED);

	@Override
	FunctionDescriptor shared ()
	{
		return shared;
	}
}
