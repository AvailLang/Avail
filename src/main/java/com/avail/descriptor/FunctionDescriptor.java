/*
 * FunctionDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.parsing.A_Phrase;
import com.avail.descriptor.parsing.BlockPhraseDescriptor;
import com.avail.descriptor.parsing.PhraseDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.parsing.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionDescriptor.ObjectSlots.CODE;
import static com.avail.descriptor.FunctionDescriptor.ObjectSlots.OUTER_VAR_AT_;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.levelOne.L1Decompiler.decompile;

/**
 * A function associates {@linkplain CompiledCodeDescriptor compiled code} with
 * a referencing environment that binds the code's free variables to variables
 * defined in an outer lexical scope. In this way, a function constitutes a
 * proper closure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class FunctionDescriptor
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
		OUTER_VAR_AT_;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_RawFunction code = object.code();
		A_Phrase phrase = code.originatingPhrase();
		if (phrase.equalsNil())
		{
			phrase = decompile(object.code());
		}
		phrase.printOnAvoidingIndent(aStream, recursionMap, indent + 1);
	}

	@Override
	protected A_RawFunction o_Code (final AvailObject object)
	{
		return object.slot(CODE);
	}

	@Override
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsFunction(object);
	}

	@Override
	protected boolean o_EqualsFunction (
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
	protected int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit hash value. If outer vars of mutable functions can
		// peel away when executed (last use of an outer var of a mutable
		// function can clobber that var and replace the OUTER_VAR_AT_ entry
		// with 0 or something), it's ok because nobody could know what the hash
		// value *used to be* for this function.

		// In case the last reference to the object is being added to a set, but
		// subsequent execution might nil out a captured value.
		object.makeImmutable();
		final A_BasicObject code = object.slot(CODE);
		int hash = code.hash() ^ 0x1386D4F6;
		for (int i = 1, end = object.numOuterVars(); i <= end; i++)
		{
			hash = hash * 13 + object.outerVarAt(i).hash();
		}
		return hash;
	}

	@Override
	protected boolean o_IsFunction (final AvailObject object)
	{
		return true;
	}

	/**
	 * Answer the object's type. Simply asks the {@linkplain
	 * CompiledCodeDescriptor compiled code} for the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 */
	@Override
	protected A_Type o_Kind (final AvailObject object)
	{
		return object.slot(CODE).functionType();
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + " /* "
			+ object.code().methodName().asNativeString()
			+ " */";
	}

	/**
	 * Answer how many outer vars I've copied.
	 */
	@Override
	protected int o_NumOuterVars (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	@Override
	protected boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		if (isMutable())
		{
			object.setSlot(OUTER_VAR_AT_, index, nil);
			return true;
		}
		return false;
	}

	@Override
	AvailObject o_OuterVarAt (final AvailObject object, final int subscript)
	{
		return object.slot(OUTER_VAR_AT_, subscript);
	}

	@Override
	protected void o_OuterVarAtPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(OUTER_VAR_AT_, subscript, value);
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
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return true;
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function");
		writer.write("function implementation");
		object.slot(CODE).writeSummaryTo(writer);
		writer.write("outers");
		writer.startArray();
		for (int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			object.slot(OUTER_VAR_AT_, i).writeSummaryTo(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function");
		writer.write("function implementation");
		object.slot(CODE).writeTo(writer);
		writer.write("outers");
		writer.startArray();
		for (int i = 1, limit = object.variableObjectSlotsCount();
			i <= limit;
			i++)
		{
			object.slot(OUTER_VAR_AT_, i).writeSummaryTo(writer);
		}
		writer.endArray();
		writer.endObject();
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
	 * @return An appropriate function with the given signature.
	 */
	public static A_Function createStubWithSignature (
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
		final L1InstructionWriter writer = new L1InstructionWriter(nil, 0, nil);
		writer.argumentTypes(argTypesArray);
		writer.setReturnType(returnType);
		writer.write(
			0,
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(function));
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(0, L1Operation.L1_doPushLastLocal, i);
		}
		writer.write(0, L1Operation.L1_doMakeTuple, numArgs);
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.APPLY.bundle),
			writer.addLiteral(returnType));
		final AvailObject code = writer.compiledCode();
		final A_Function newFunction =
			createFunction(code, emptyTuple());
		newFunction.makeImmutable();
		return newFunction;
	}

	/**
	 * Create a function that takes arguments of the specified types, then calls
	 * the {@link A_Method} for the {@link A_Bundle} of the given {@link A_Atom}
	 * with those arguments.
	 *
	 * @param functionType
	 *        The type to which the resultant function should conform.
	 * @param atom
	 *        The {@link A_Atom} which names the {@link A_Method} to be invoked
	 *        by the new function.
	 * @return An appropriate function.
	 * @throws IllegalArgumentException
	 *         If the atom has no associated bundle/method, or the function
	 *         signature is inconsistent with the available method definitions.
	 */
	@SuppressWarnings("unused")
	public static A_Function createStubToCallMethod (
		final A_Type functionType,
		final A_Atom atom)
	{
		final A_Bundle bundle = atom.bundleOrNil();
		if (bundle.equalsNil())
		{
			throw new IllegalArgumentException("Atom to invoke has no method");
		}
		final A_Method method = bundle.bundleMethod();
		final A_Type argTypes = functionType.argsTupleType();
		// Check that there's a definition, even abstract, that will catch all
		// invocations for the given function type's argument types.
		final boolean ok = method.definitionsTuple().stream()
			.anyMatch(definition ->
				!definition.isMacroDefinition()
					&& definition.bodySignature().isSubtypeOf(functionType));
		if (!ok)
		{
			throw new IllegalArgumentException(
				"Function signature is not strong enough to call method "
					+ "safely");
		}
		final int numArgs = argTypes.sizeRange().lowerBound().extractInt();
		final A_Type[] argTypesArray = new AvailObject[numArgs];
		for (int i = 1; i <= numArgs; i++)
		{
			argTypesArray[i - 1] = argTypes.typeAtIndex(i);
		}
		final A_Type returnType = functionType.returnType();
		final L1InstructionWriter writer = new L1InstructionWriter(nil, 0, nil);
		writer.argumentTypes(argTypesArray);
		writer.setReturnType(returnType);
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(0, L1Operation.L1_doPushLastLocal, i);
		}
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(bundle),
			writer.addLiteral(returnType));
		final AvailObject code = writer.compiledCode();
		final A_Function newFunction =
			createFunction(code, emptyTuple());
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
	public static A_Function createFunction (
		final A_BasicObject code,
		final A_Tuple copiedTuple)
	{
		final int copiedSize = copiedTuple.tupleSize();
		final AvailObject object = mutable.create(copiedSize);
		object.setSlot(CODE, code);
		if (copiedSize > 0)
		{
			object.setSlotsFromTuple(
				OUTER_VAR_AT_, 1, copiedTuple, 1, copiedSize);
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
	@ReferencedInGeneratedCode
	public static AvailObject createExceptOuters (
		final A_RawFunction code,
		final int outersCount)
	{
		assert code.numOuters() == outersCount;
		final AvailObject object = mutable.create(outersCount);
		object.setSlot(CODE, code);
		return object;
	}

	/**
	 * Construct a function with the given code and one outer variable.
	 *
	 * @param code The code with which to build the function.
	 * @param outer1 The sole outer variable that will be enclosed.
	 * @return A function with its outer variable initialized.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject createWithOuters1 (
		final A_RawFunction code,
		final AvailObject outer1)
	{
		final AvailObject function = createExceptOuters(code, 1);
		function.setSlot(OUTER_VAR_AT_, 1, outer1);
		return function;
	}

	/**
	 * Construct a function with the given code and two outer variables.
	 *
	 * @param code The code with which to build the function.
	 * @param outer1 The first outer variable that will be enclosed.
	 * @param outer2 The second outer variable that will be enclosed.
	 * @return A function with its outer variables initialized.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject createWithOuters2 (
		final A_RawFunction code,
		final AvailObject outer1,
		final AvailObject outer2)
	{
		final AvailObject function = createExceptOuters(code, 2);
		function.setSlot(OUTER_VAR_AT_, 1, outer1);
		function.setSlot(OUTER_VAR_AT_, 2, outer2);
		return function;
	}

	/**
	 * Construct a function with the given code and three outer variables.
	 *
	 * @param code The code with which to build the function.
	 * @param outer1 The first outer variable that will be enclosed.
	 * @param outer2 The second outer variable that will be enclosed.
	 * @param outer3 The first outer variable that will be enclosed.
	 * @return A function with its outer variables initialized.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject createWithOuters3 (
		final A_RawFunction code,
		final AvailObject outer1,
		final AvailObject outer2,
		final AvailObject outer3)
	{
		final AvailObject function = createExceptOuters(code, 3);
		function.setSlot(OUTER_VAR_AT_, 1, outer1);
		function.setSlot(OUTER_VAR_AT_, 2, outer2);
		function.setSlot(OUTER_VAR_AT_, 3, outer3);
		return function;
	}

	/**
	 * Convert a {@link PhraseDescriptor phrase} into a zero-argument
	 * {@link A_Function}.
	 *
	 * @param phrase
	 *        The phrase to compile to a function.
	 * @param module
	 *        The {@linkplain ModuleDescriptor module} that is the context for
	 *        the phrase and function, or {@linkplain NilDescriptor#nil nil}
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
		final A_Phrase block = newBlockNode(
			emptyTuple(),
			0,
			tuple(phrase),
			TOP.o(),
			emptySet(),
			lineNumber,
			phrase.tokens());
		BlockPhraseDescriptor.recursivelyValidate(block);
		final A_RawFunction compiledBlock = block.generateInModule(module);
		// The block is guaranteed context-free (because imported
		// variables/values are embedded directly as constants in the generated
		// code), so build a function with no copied data.
		assert compiledBlock.numOuters() == 0;
		final A_Function function = createFunction(compiledBlock, emptyTuple());
		function.makeImmutable();
		return function;
	}

	/**
	 * Construct a bootstrap {@code FunctionDescriptor function} that
	 * crashes when invoked.
	 *
	 * @param messageString
	 *        The message string to prepend to the list of arguments, indicating
	 *        the basic nature of the failure.
	 * @param paramTypes
	 *        The {@linkplain TupleDescriptor tuple} of parameter {@linkplain
	 *        TypeDescriptor types}.
	 * @return The requested crash function.
	 * @see SpecialMethodAtom#CRASH
	 */
	public static A_Function newCrashFunction (
		final String messageString,
		final A_Tuple paramTypes)
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.argumentTypesTuple(paramTypes);
		writer.setReturnType(bottom());
		writer.write(
			0,
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(stringFrom(messageString)));
		final int numArgs = paramTypes.tupleSize();
		for (int i = 1; i <= numArgs; i++)
		{
			writer.write(0, L1Operation.L1_doPushLastLocal, i);
		}
		// Put the error message and arguments into a tuple.
		writer.write(0, L1Operation.L1_doMakeTuple, numArgs + 1);
		writer.write(
			0,
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialMethodAtom.CRASH.bundle),
			writer.addLiteral(bottom()));
		final A_RawFunction code = writer.compiledCode();
		code.setMethodName(stringFrom("VM crash function: " + messageString));
		final A_Function function = createFunction(code, emptyTuple());
		function.makeShared();
		return function;
	}

	/**
	 * Construct a new {@code FunctionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FunctionDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.FUNCTION_TAG, ObjectSlots.class, null);
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
