/**
 * descriptor/FunctionDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.util.Arrays.fill;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;

/**
 * A function associates {@linkplain CompiledCodeDescriptor compiled code} with
 * a referencing environment that binds the code's free variables to variables
 * defined in an outer lexical scope. In this way, a function constitutes a
 * proper closure.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class FunctionDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/** The {@linkplain CompiledCodeDescriptor compiled code}. */
		CODE,

		/** The outer variables. */
		OUTER_VAR_AT_
	}

	@Override
	public void o_Code (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CODE, value);
	}

	@Override
	public @NotNull AvailObject o_OuterVarAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.OUTER_VAR_AT_, subscript);
	}

	@Override
	public void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.objectSlotAtPut(ObjectSlots.OUTER_VAR_AT_, subscript, value);
	}

	@Override
	public @NotNull AvailObject o_Code (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CODE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		if (isMutable)
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
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsFunction(object);
	}

	@Override
	public boolean o_EqualsFunction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunction)
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
		// They're equal (but occupy disjoint storage). Replace one with an
		// indirection to the other to reduce storage costs and the frequency of
		// detailed comparisons.
		object.becomeIndirectionTo(aFunction);
		aFunction.makeImmutable();
		// Now that there are at least two references to it
		return true;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		// Answer a 32-bit hash value. If outer vars of mutable functions can
		// peel away when executed (last use of an outer var of a mutable
		// function can clobber that var and replace the OUTER_VAR_AT_ entry
		// with 0 or something), it's ok because nobody could know what the hash
		// value *used to be* for this function.

		int hash = object.code().hash() ^ 0x1386D4F6;
		for (int i = 1, end = object.numOuterVars(); i <= end; i++)
		{
			hash = hash * 13 + object.outerVarAt(i).hash();
		}
		return hash;
	}

	@Override
	public boolean o_IsFunction (
		final @NotNull AvailObject object)
	{
		return true;
	}

	/**
	 * Answer the object's type. Simply asks the {@linkplain
	 * CompiledCodeDescriptor compiled code} for the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 */
	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return object.code().functionType();
	}

	@Override
	public boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunction)
	{
		//  Answer true if either I am aFunction or I contain aFunction.

		if (object.equals(aFunction))
		{
			return true;
		}
		return object.code().containsBlock(aFunction);
	}

	@Override
	public boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index)
	{
		// This one's kind of tricky. An outer variable is being used by the
		// interpreter (the variable itself, but we don't yet know whether it
		// will be passed around, or sent the getValue or setValue message, or
		// even just popped. So don't destroy it yet. If this function is
		// mutable, unlink the outer variable from it (as the function no longer
		// needs it in that case). Answer true if it was mutable, otherwise
		// false, so the calling code knows what happened.

		if (isMutable)
		{
			object.outerVarAtPut(index, NullDescriptor.nullObject());
			return true;
		}
		return false;
	}

	/**
	 * Answer how many outer vars I've copied.
	 */
	@Override
	public int o_NumOuterVars (
		final @NotNull AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	/**
	 * Create a function that accepts a specific number of arguments and always
	 * returns the specified constant value.  The arguments may be of any type
	 * (except top or bottom), and in fact the function types them as "any".
	 *
	 * @param numArgs The number of arguments to accept.
	 * @param constantResult The constant that the new function should always
	 *                       produce.
	 * @return A function that takes N arguments and returns a constant.
	 */
	public static AvailObject createStubForNumArgsConstantResult (
		final int numArgs,
		final @NotNull AvailObject constantResult)
	{
		final L1InstructionWriter writer = new L1InstructionWriter();

		final AvailObject [] argTypes = new AvailObject [numArgs];
		fill(argTypes, ANY.o());
		writer.argumentTypes(argTypes);
		writer.returnType(InstanceTypeDescriptor.withInstance(constantResult));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(constantResult)));
		writer.primitiveNumber(
			Primitive.prim340_PushConstant.primitiveNumber);
		final AvailObject code = writer.compiledCode();
		final AvailObject function =
			FunctionDescriptor.create (
				code,
				TupleDescriptor.empty());
		function.makeImmutable();
		return function;
	}

	/**
	 * Create a function that takes arguments of the specified types, then turns
	 * around and sends a specific two-argument message.  The first argument of
	 * that message is specified here, and the second argument is a list of the
	 * arguments supplied to the function we're creating.  Ensure the send
	 * returns a value that complies with resultType.
	 *
	 * @param argTypes The types of arguments the new function should accept.
	 * @param implementationSet The two-argument message the new function should
	 *                          invoke.
	 * @param firstArg The first argument to send to the two-argument message.
	 * @param resultType The type that the invocation (and the new function)
	 *                   should return.
	 * @return A function which accepts arguments of the given types and
	 *         produces a value of the specified type.
	 */
	public static AvailObject createStubWithArgTypes (
		final @NotNull AvailObject argTypes,
		final @NotNull AvailObject implementationSet,
		final @NotNull AvailObject firstArg,
		final @NotNull AvailObject resultType)
	{
		final int numArgs = argTypes.tupleSize();
		final AvailObject [] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			assert argTypes.tupleAt(i).isInstanceOfKind(TYPE.o());
			argTypesArray[i - 1] = argTypes.tupleAt(i);
		}

		final L1InstructionWriter writer = new L1InstructionWriter();
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
		writer.write(new L1Instruction(L1Operation.L1_doMakeTuple, numArgs));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(implementationSet),
				writer.addLiteral(resultType)));

		final AvailObject code = writer.compiledCode();

		final AvailObject function =
			FunctionDescriptor.create (
				code,
				TupleDescriptor.empty());
		function.makeImmutable();
		return function;
	}

	/**
	 * Construct a function with the given code and tuple of copied variables.
	 *
	 * @param code The code with which to build the function.
	 * @param copiedTuple The outer variables and constants to enclose.
	 * @return A function.
	 */
	public static AvailObject create (
		final @NotNull AvailObject code,
		final @NotNull AvailObject copiedTuple)
	{
		final AvailObject object = mutable().create(
			copiedTuple.tupleSize());
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
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FunctionDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FunctionDescriptor}.
	 */
	private final static FunctionDescriptor mutable =
		new FunctionDescriptor(true);

	/**
	 * Answer the mutable {@link FunctionDescriptor}.
	 *
	 * @return The mutable {@link FunctionDescriptor}.
	 */
	public static FunctionDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link FunctionDescriptor}.
	 */
	private final static FunctionDescriptor immutable =
		new FunctionDescriptor(false);

	/**
	 * Answer the immutable {@link FunctionDescriptor}.
	 *
	 * @return The immutable {@link FunctionDescriptor}.
	 */
	public static FunctionDescriptor immutable ()
	{
		return immutable;
	}
}
