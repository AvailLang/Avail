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

import static com.avail.descriptor.FunctionDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import java.util.Collections;
import java.util.List;
import com.avail.annotations.*;
import com.avail.interpreter.Primitive;
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
	AvailObject o_OuterVarAt (final AvailObject object, final int subscript)
	{
		return object.slot(OUTER_VAR_AT_, subscript);
	}

	@Override
	void o_OuterVarAtPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(OUTER_VAR_AT_, subscript, value);
	}

	@Override
	A_RawFunction o_Code (final AvailObject object)
	{
		return object.slot(CODE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		switch (mutability)
		{
			case MUTABLE:
				aStream.append("Mutable function with code: ");
				break;
			case IMMUTABLE:
				aStream.append("Immutable function with code: ");
				break;
			case SHARED:
				aStream.append("Shared function with code: ");
				break;
			default:
				assert false;
		}
		L1Decompiler.parse(object).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
	}

	@Override
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsFunction(object);
	}

	@Override
	boolean o_EqualsFunction (
		final AvailObject object,
		final A_Function aFunction)
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
		else if (!aFunction.descriptor().isShared())
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

		final A_BasicObject code = object.slot(CODE);
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
	A_Type o_Kind (final AvailObject object)
	{
		return object.slot(CODE).functionType();
	}

	@Override
	boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		if (isMutable())
		{
			object.setSlot(OUTER_VAR_AT_, index, NilDescriptor.nil());
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
	public static A_Function createStubWithArgTypes (
		final A_Type functionType,
		final A_Function function)
	{
		final A_Type argTypes = functionType.argsTupleType();
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final A_Type[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final A_Type returnType = functionType.returnType();
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
		final A_Bundle bundle =
			MethodDescriptor.vmFunctionApplyAtom().bundleOrNil();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(bundle),
				writer.addLiteral(returnType)));
		final AvailObject code = writer.compiledCode();
		final A_Function newFunction = FunctionDescriptor.create(
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
	public static A_Function createStubTakingTupleFrom (
		final A_Function function)
	{
		final A_Type argTypes = function.kind().argsTupleType();
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final A_Type[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final A_Type tupleType =
			TupleTypeDescriptor.forTypes(argTypesArray);
		final A_Type returnType = function.kind().returnType();
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.argumentTypes(new A_Type[] {tupleType});
		writer.returnType(returnType);
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(function)));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLastLocal,
				1));
		final A_Bundle bundle =
			MethodDescriptor.vmFunctionApplyAtom().bundleOrNil();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(bundle),
				writer.addLiteral(returnType)));
		final A_RawFunction code = writer.compiledCode();
		final A_Function newFunction = FunctionDescriptor.create(
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
	public static A_Function create (
		final A_BasicObject code,
		final A_Tuple copiedTuple)
	{
		final AvailObject object = mutable.create(copiedTuple.tupleSize());
		object.setSlot(CODE, code);
		for (int i = copiedTuple.tupleSize(); i >= 1; -- i)
		{
			object.setSlot(OUTER_VAR_AT_, i, copiedTuple.tupleAt(i));
		}
		return object;
	}

	/**
	 * Construct a function with the given code and room for the given number of
	 * outer variables.  Do not initialize any outer variable slots.
	 *
	 * @param code The code with which to build the function.
	 * @param outersCount The number of outer variables that will be enclosed.
	 * @return A function without its outer variables initialized.
	 */
	public static A_Function createExceptOuters (
		final A_RawFunction code,
		final int outersCount)
	{
		assert code.numOuters() == outersCount;
		final AvailObject object = mutable.create(outersCount);
		object.setSlot(CODE, code);
		return object;
	}

	/**
	 * Convert a {@link ParseNodeDescriptor phrase} into a zero-argument
	 * {@link FunctionDescriptor function}.
	 *
	 * @param phrase
	 *        The phrase to compile to a function.
	 * @param module
	 *        The {@linkplain ModuleDescriptor module} that is the context for
	 *        the phrase and function, or {@linkplain NilDescriptor#nil() nil}
	 *        if there is no context.
	 * @param lineNumber
	 *        The line number to attach to the new function, or {@code 0} if no
	 *        meaningful line number is available.
	 * @return A zero-argument function.
	 */
	public static A_Function createFunctionForPhrase (
		final A_Phrase phrase,
		final A_Module module,
		final int lineNumber)
	{
		final A_Phrase block = BlockNodeDescriptor.newBlockNode(
			Collections.<A_Phrase>emptyList(),
			0,
			Collections.singletonList(phrase),
			TOP.o(),
			SetDescriptor.empty(),
			lineNumber);
		BlockNodeDescriptor.recursivelyValidate(block);
		final A_RawFunction compiledBlock = block.generateInModule(module);
		// The block is guaranteed context-free (because imported
		// variables/values are embedded directly as constants in the generated
		// code), so build a function with no copied data.
		assert compiledBlock.numOuters() == 0;
		final A_Function function = FunctionDescriptor.create(
			compiledBlock,
			TupleDescriptor.empty());
		function.makeImmutable();
		return function;
	}

	/**
	 * Construct a bootstrapped {@linkplain FunctionDescriptor function} that
	 * uses the specified primitive.  The primitive failure code should invoke
	 * the {@link MethodDescriptor#vmCrashAtom}'s bundle with a tuple of passed
	 * arguments followed by the primitive failure value.
	 *
	 * @param primitive The {@link Primitive} to use.
	 * @return A function.
	 */
	public static A_Function newPrimitiveFunction (final Primitive primitive)
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.primitiveNumber(primitive.primitiveNumber);
		final A_Type functionType = primitive.blockTypeRestriction();
		final A_Type argsTupleType = functionType.argsTupleType();
		final int numArgs = argsTupleType.sizeRange().upperBound().extractInt();
		final A_Type [] argTypes = new AvailObject[numArgs];
		for (int i = 0; i < numArgs; i++)
		{
			argTypes[i] = argsTupleType.typeAtIndex(i + 1);
		}
		writer.argumentTypes(argTypes);
		writer.returnType(functionType.returnType());
		if (!primitive.hasFlag(CannotFail))
		{
			// Produce failure code.  First declare the local that holds
			// primitive failure information.
			final int failureLocal = writer.createLocal(
				VariableTypeDescriptor.wrapInnerType(
					IntegerRangeTypeDescriptor.naturalNumbers()));
			for (int i = 1; i <= numArgs; i++)
			{
				writer.write(
					new L1Instruction(L1Operation.L1_doPushLastLocal, i));
			}
			// Get the failure code.
			writer.write(
				new L1Instruction(
					L1Operation.L1_doGetLocal,
					failureLocal));
			// Put the arguments and failure code into a tuple.
			writer.write(
				new L1Instruction(
					L1Operation.L1_doMakeTuple,
					numArgs + 1));
			writer.write(
				new L1Instruction(
					L1Operation.L1_doCall,
					writer.addLiteral(
						MethodDescriptor.vmCrashAtom().bundleOrCreate()),
					writer.addLiteral(BottomTypeDescriptor.bottom())));
		}
		else
		{
			// Primitive cannot fail, but be nice to the decompiler.
			writer.write(
				new L1Instruction(
					L1Operation.L1_doPushLiteral,
					writer.addLiteral(NilDescriptor.nil())));
		}
		final A_Function function = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		function.makeShared();
		return function;
	}

	/**
	 * Construct a bootstrap {@linkplain FunctionDescriptor function} that
	 * crashes when invoked.
	 *
	 * @param paramTypes
	 *        The {@linkplain TupleDescriptor tuple} of parameter {@linkplain
	 *        TypeDescriptor types}.
	 * @return The requested crash function.
	 * @see MethodDescriptor#vmCrashAtom()
	 */
	public static A_Function newCrashFunction (final A_Tuple paramTypes)
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.primitiveNumber(0);
		writer.argumentTypesTuple(paramTypes);
		writer.returnType(BottomTypeDescriptor.bottom());
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(StringDescriptor.from(
					"unexpected VM failure"))));
		final int numArgs = paramTypes.tupleSize();
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(
				new L1Instruction(L1Operation.L1_doPushLastLocal, i));
		}
		// Put the error message and arguments into a tuple.
		writer.write(
			new L1Instruction(
				L1Operation.L1_doMakeTuple,
				numArgs + 1));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmCrashAtom().bundleOrCreate()),
				writer.addLiteral(BottomTypeDescriptor.bottom())));
		final A_Function function = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		function.makeShared();
		return function;
	}

	/**
	 * Construct a new {@link FunctionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FunctionDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
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
