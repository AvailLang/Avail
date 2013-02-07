/**
 * MethodDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.MethodDescriptor.ObjectSlots.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.lang.Math.max;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.primitive.*;
import com.avail.serialization.SerializerOperation;

/**
 * A method maintains all definitions that have the same name.  At
 * compile time a name is looked up and the corresponding method is
 * stored as a literal in the object code.  At runtime the actual function is
 * located within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * <p>To support macros safely, a method must contain either all
 * {@linkplain MacroDefinitionDescriptor macro signatures} or all non-macro
 * {@linkplain DefinitionDescriptor signatures}, but not both.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MethodDescriptor
extends Descriptor
{
	/**
	 * A special {@linkplain AtomDescriptor atom} used to name the VM's method
	 * to crash during early bootstrapping problems.
	 */
	private static AvailObject vmCrashAtom;

	/**
	 * Answer the {@linkplain AtomDescriptor atom} used by the VM to name the
	 * method which is invoked in the event of a bootstrapping problem.
	 *
	 * @return The atom.
	 */
	public static AvailObject vmCrashAtom ()
	{
		return vmCrashAtom;
	}

	/**
	 * A {@linkplain MethodDescriptor method} containing a {@linkplain
	 * MethodDefinitionDescriptor function} that invokes {@linkplain
	 * P_256_EmergencyExit}. Needed by some hand-built bootstrap functions.
	 */
	private static AvailObject vmCrashMethod;

	/**
	 * Answer a {@linkplain MethodDescriptor method}
	 * containing a {@linkplain MethodDefinitionDescriptor function} that
	 * invokes {@linkplain P_256_EmergencyExit}. Needed by some hand-built
	 * bootstrap functions.
	 *
	 * @return A method.
	 */
	public static AvailObject vmCrashMethod ()
	{
		return vmCrashMethod;
	}

	/**
	 * Construct the {@linkplain MethodDescriptor method}
	 * for bootstrap emergency exit.
	 *
	 * @return A method.
	 */
	private static AvailObject newVMCrashMethod ()
	{
		assert P_256_EmergencyExit.instance.hasFlag(CannotFail);
		final AvailObject newFunction =
			newPrimitiveFunction(P_256_EmergencyExit.instance);

		// Create the new method. Note that the underscore is
		// essential here, as certain parts of the virtual machine (like the
		// decompiler) use the name to figure out how many arguments a method
		// accepts.
		final AvailObject method = newMethodWithName(vmCrashAtom);
		try
		{
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, newFunction));
		}
		catch (final SignatureException e)
		{
			assert false : "This should not be possible!";
			throw new RuntimeException(e);
		}

		return method;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap method-defining method.
	 */
	private static AvailObject vmMethodDefinerAtom;

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new methods.
	 *
	 * @return The name of the bootstrap method-defining method.
	 */
	public static AvailObject vmMethodDefinerAtom ()
	{
		return vmMethodDefinerAtom;
	}

	/**
	 * The special pre-bootstrap method-defining method constructed by the VM.
	 */
	private static AvailObject vmMethodDefinerMethod;

	/**
	 * Answer the special pre-bootstrap method-defining method that was created
	 * and installed into the {@link AvailRuntime} automatically.
	 *
	 * @return The bootstrap method-defining method.
	 */
	public static AvailObject vmMethodDefinerMethod ()
	{
		return vmMethodDefinerMethod;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap macro-defining method.
	 */
	private static AvailObject vmMacroDefinerAtom;

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new macros.
	 *
	 * @return The name of the bootstrap macro-defining method.
	 */
	public static AvailObject vmMacroDefinerAtom ()
	{
		return vmMacroDefinerAtom;
	}

	/**
	 * The special pre-bootstrap macro-defining method constructed by the VM.
	 */
	private static AvailObject vmMacroDefinerMethod;

	/**
	 * Answer the special pre-bootstrap macro-defining method that was created
	 * and installed into the {@link AvailRuntime} automatically.
	 *
	 * @return The bootstrap macro-defining method.
	 */
	public static AvailObject vmMacroDefinerMethod ()
	{
		return vmMacroDefinerMethod;
	}

	/**
	 * Construct a bootstrapped {@linkplain FunctionDescriptor function} that
	 * uses the specified primitive.  The primitive failure code should invoke
	 * the {@link #vmCrashMethod} with a tuple of passed arguments followed by
	 * the primitive failure value.
	 *
	 * @param primitive The {@link Primitive} to use.
	 * @return A function.
	 */
	public static AvailObject newPrimitiveFunction (final Primitive primitive)
	{
		final L1InstructionWriter writer = new L1InstructionWriter(
			NilDescriptor.nil(),
			0);
		writer.primitiveNumber(primitive.primitiveNumber);
		final AvailObject functionType = primitive.blockTypeRestriction();
		final AvailObject argsTupleType = functionType.argsTupleType();
		final int numArgs = argsTupleType.sizeRange().upperBound().extractInt();
		final AvailObject [] argTypes = new AvailObject[numArgs];
		for (int i = 0; i < argTypes.length; i++)
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
			for (int i = 1; i <= argTypes.length; i++)
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
					argTypes.length + 1));
			writer.write(
				new L1Instruction(
					L1Operation.L1_doCall,
					writer.addLiteral(vmCrashMethod()),
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
		final AvailObject function = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		function.makeImmutable();
		return function;
	}

	/**
	 * Construct the {@linkplain MethodDescriptor methods} for bootstrapping
	 * method definition.
	 *
	 * @return A method.
	 */
	private static AvailObject newVMMethodDefinerMethod ()
	{
		final AvailObject fromStringFunction = newPrimitiveFunction(
			P_253_SimpleMethodDeclaration.instance);
		final AvailObject fromAtomFunction = newPrimitiveFunction(
			P_228_MethodDeclarationFromAtom.instance);
		final AvailObject method = newMethodWithName(vmMethodDefinerAtom);
		try
		{
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, fromAtomFunction));
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, fromStringFunction));
		}
		catch (final SignatureException e)
		{
			assert false : "This should not be possible!";
			throw new RuntimeException(e);
		}
		return method;
	}

	/**
	 * Construct the {@linkplain MethodDescriptor method} for bootstrapping
	 * macro definition.
	 *
	 * @return A method.
	 */
	private static AvailObject newVMMacroDefinerMethod ()
	{
		final AvailObject fromStringFunction = newPrimitiveFunction(
			P_249_SimpleMacroDeclaration.instance);
		final AvailObject method = newMethodWithName(vmMacroDefinerAtom);
		try
		{
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, fromStringFunction));
		}
		catch (final SignatureException e)
		{
			assert false : "This should not be possible!";
			throw new RuntimeException(e);
		}
		return method;
	}

	/**
	 * The (special) name of the VM-built function application method.
	 */
	private static AvailObject vmFunctionApplyAtom;

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function application method.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static AvailObject vmFunctionApplyAtom ()
	{
		return vmFunctionApplyAtom;
	}

	/**
	 * A {@linkplain MethodDescriptor method} containing a {@linkplain
	 * MethodDefinitionDescriptor definition} that invokes {@link
	 * P_040_InvokeWithTuple} (function application). Needed by some hand-built
	 * functions.
	 */
	private static AvailObject vmFunctionApplyMethod;

	/**
	 * A {@linkplain MethodDescriptor method} containing a {@linkplain
	 * MethodDefinitionDescriptor definition} that invokes {@link
	 * P_040_InvokeWithTuple} (function application). Needed by some hand-built
	 * functions.
	 *
	 * @return A method.
	 */
	public static AvailObject vmFunctionApplyMethod ()
	{
		return vmFunctionApplyMethod;
	}

	/**
	 * Construct the {@linkplain MethodDescriptor method} for bootstrapping
	 * function application.
	 *
	 * @return A method.
	 */
	private static AvailObject newVMFunctionApplyMethod ()
	{
		final AvailObject newFunction =
			newPrimitiveFunction(P_040_InvokeWithTuple.instance);

		// Create the new method.
		final AvailObject method = newMethodWithName(vmFunctionApplyAtom);
		try
		{
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, newFunction));
		}
		catch (final SignatureException e)
		{
			assert false : "This should not be possible!";
			throw new RuntimeException(e);
		}

		return method;
	}

	/**
	 * The (special) name of the VM-built atom-set publication method.
	 */
	private static AvailObject vmPublishAtomsAtom;

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function which publishes a set of atoms from the current module.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static AvailObject vmPublishAtomsAtom ()
	{
		return vmPublishAtomsAtom;
	}

	/**
	 * A {@linkplain MethodDescriptor method} containing a {@linkplain
	 * MethodDefinitionDescriptor definition} that invokes {@link
	 * P_263_DeclareAllExportedAtoms} (atom-set publication). Needed by the
	 * module compilation system.
	 */
	private static AvailObject vmPublishAtomsMethod;

	/**
	 * A {@linkplain MethodDescriptor method} containing a {@linkplain
	 * MethodDefinitionDescriptor definition} that invokes {@link
	 * P_263_DeclareAllExportedAtoms} (atom-set publication). Needed by the
	 * module compilation system.
	 *
	 * @return A method.
	 */
	public static AvailObject vmPublishAtomsMethod ()
	{
		return vmPublishAtomsMethod;
	}

	/**
	 * Construct the {@linkplain MethodDescriptor method} for publishing a set
	 * of atoms from the current module.
	 *
	 * @return A method.
	 */
	private static AvailObject newVMPublishAtomsMethod ()
	{
		final AvailObject newFunction =
			newPrimitiveFunction(P_263_DeclareAllExportedAtoms.instance);

		// Create the new method.
		final AvailObject method = newMethodWithName(vmPublishAtomsAtom);
		try
		{
			method.addDefinition(
				MethodDefinitionDescriptor.create(method, newFunction));
		}
		catch (final SignatureException e)
		{
			assert false : "This should not be possible!";
			throw new RuntimeException(e);
		}

		return method;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		vmCrashAtom = AtomDescriptor.createSpecialAtom(
			StringDescriptor.from("vm crash_"));
		vmCrashMethod = newVMCrashMethod();
		vmFunctionApplyAtom = AtomDescriptor.createSpecialAtom(
			StringDescriptor.from("vm function apply_(«_‡,»)"));
		vmFunctionApplyMethod = newVMFunctionApplyMethod();
		vmMethodDefinerAtom = AtomDescriptor.createSpecialAtom(
			StringDescriptor.from("vm method_is_"));
		vmMethodDefinerMethod = newVMMethodDefinerMethod();
		vmMacroDefinerAtom = AtomDescriptor.createSpecialAtom(
			StringDescriptor.from("vm macro_is_"));
		vmMacroDefinerMethod = newVMMacroDefinerMethod();
		vmPublishAtomsAtom = AtomDescriptor.createSpecialAtom(
			StringDescriptor.from("vm publish atom set_(public=_)"));
		vmPublishAtomsMethod = newVMPublishAtomsMethod();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		vmCrashAtom = null;
		vmCrashMethod = null;
		vmFunctionApplyAtom = null;
		vmFunctionApplyMethod = null;
		vmMethodDefinerAtom = null;
		vmMethodDefinerMethod = null;
		vmMacroDefinerAtom = null;
		vmMacroDefinerMethod = null;
		vmPublishAtomsAtom = null;
		vmPublishAtomsMethod = null;
	}

	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain AtomDescriptor atom} that acts as the true name of
		 * this {@linkplain MethodDescriptor method}.
		 */
		NAME,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain
		 * DefinitionDescriptor definitions} that constitute this multimethod
		 * (or multimacro).
		 */
		DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain IntegerDescriptor
		 * integers} that encodes a decision tree for selecting the most
		 * specific multimethod appropriate for the argument types.
		 */
		PRIVATE_TESTING_TREE,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * FunctionDescriptor functions} which, when invoked with suitable
		 * {@linkplain TypeDescriptor types} as arguments, will determine
		 * whether the call arguments have mutually compatible types, and if so
		 * produce a type to which the call's return value is expected to
		 * conform.  This type strengthening is <em>assumed</em> to hold at
		 * compile time (of the call) and <em>checked</em> at runtime.
		 */
		TYPE_RESTRICTIONS_TUPLE,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * TupleTypeDescriptor tuple types} below which new signatures may no
		 * longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * L2ChunkDescriptor.IntegerSlots#INDEX indices} of {@linkplain
		 * L2ChunkDescriptor level two chunks} that depend on the membership of
		 * this {@linkplain MethodDescriptor method}.  A change to the
		 * membership should cause these chunks to be invalidated.
		 */
		DEPENDENT_CHUNK_INDICES
	}

	@Override boolean
	allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == DEFINITIONS_TUPLE
			|| e == PRIVATE_TESTING_TREE
			|| e == DEPENDENT_CHUNK_INDICES
			|| e == TYPE_RESTRICTIONS_TUPLE
			|| e == SEALED_ARGUMENTS_TYPES_TUPLE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final int size = object.definitionsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" definition");
		if (size != 1)
		{
			aStream.append('s');
		}
		aStream.append(" of ");
		aStream.append(object.name().name().asNativeString());
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	AvailObject o_DefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(DEFINITIONS_TUPLE);
		}
	}

	@Override @AvailMethod
	AvailObject o_TypeRestrictions (final AvailObject object)
	{
		return object.slot(TYPE_RESTRICTIONS_TUPLE);
	}

	@Override @AvailMethod
	AvailObject o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		return object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(NAME).hash() + 0x61AF3FC;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// A method is always shared. Never make it immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return METHOD.o();
	}

	@Override @AvailMethod
	void o_AddDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		synchronized (object)
		{
			// Record the fact that the chunk indexed by aChunkIndex depends on
			// this object not changing.
			AvailObject indices =
				object.slot(DEPENDENT_CHUNK_INDICES);
			indices = indices.setWithElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true);
			object.setSlot(
				DEPENDENT_CHUNK_INDICES,
				indices.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddDefinition (
			final AvailObject object,
			final AvailObject definition)
		throws SignatureException
	{
		synchronized (object)
		{
			final AvailObject oldTuple = object.definitionsTuple();
			if (oldTuple.tupleSize() > 0)
			{
				// Ensure that we're not mixing macro and non-macro signatures.
				assert definition.isMacroDefinition()
					== oldTuple.tupleAt(1).isMacroDefinition();
			}
			final AvailObject seals = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final AvailObject bodySignature = definition.bodySignature();
			final AvailObject paramTypes = bodySignature.argsTupleType();
			for (final AvailObject seal : seals)
			{
				final AvailObject sealType =
					TupleTypeDescriptor.forTypes(TupleDescriptor.toArray(seal));
				if (paramTypes.isSubtypeOf(sealType))
				{
					throw new SignatureException(
						AvailErrorCode.E_METHOD_IS_SEALED);
				}
			}
			final AvailObject newTuple =
				oldTuple.appendCanDestroy(definition, true);
			object.setSlot(
				DEFINITIONS_TUPLE, newTuple.traversed().makeShared());
			membershipChanged(object);
		}
	}

	/**
	 * Look up all method definitions that could match the given argument
	 * types. Answer a {@linkplain List list} of {@linkplain
	 * MethodDefinitionDescriptor method signatures}.
	 */
	@Override @AvailMethod
	List<AvailObject> o_FilterByTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final AvailObject impsTuple = object.definitionsTuple();
		for (int i = 1, end = impsTuple.tupleSize(); i <= end; i++)
		{
			final AvailObject imp = impsTuple.tupleAt(i);
			if (imp.bodySignature().acceptsListOfArgTypes(argTypes))
			{
				result.add(imp);
			}
		}
		return result;
	}

	/**
	 * Look up all method definitions that could match arguments with the
	 * given types, or anything more specific.  This should return the
	 * definitions that could be invoked at runtime at a call site with the
	 * given static types.  This set is subject to change as new methods and
	 * types are created.  If an argType and the corresponding argument type of
	 * a definition have no possible descendant except bottom, then
	 * disallow the definition (it could never actually be invoked because
	 * bottom is uninstantiable).  Answer a {@linkplain List list} of
	 * {@linkplain MethodDefinitionDescriptor method signatures}.
	 *
	 * <p>
	 * Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.
	 * </p>
	 */
	@Override @AvailMethod
	List<AvailObject> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final AvailObject impsTuple = object.definitionsTuple();
		for (int i = 1, end = impsTuple.tupleSize(); i <= end; i++)
		{
			final AvailObject imp = impsTuple.tupleAt(i);
			if (imp.bodySignature().couldEverBeInvokedWith(argTypes))
			{
				result.add(imp);
			}
		}
		return result;
	}

	/**
	 * Test if the definition is present within this method.
	 */
	@Override @AvailMethod
	boolean o_IncludesDefinition (
		final AvailObject object,
		final AvailObject definition)
	{
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		for (final AvailObject eachDefinition : object.definitionsTuple())
		{
			if (eachDefinition.equals(definition))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Look up the definition to invoke, given an array of argument types.
	 * Use the testingTree to find the definition to invoke (answer void if
	 * a lookup error occurs). There may be more entries in the tuple of
	 * argument types than we need, to allow the tuple to be a reusable buffer.
	 */
	@Override @AvailMethod
	AvailObject o_LookupByTypesFromTuple (
		final AvailObject object,
		final AvailObject argumentTypeTuple)
	{
		final AvailObject impsTuple;
		final AvailObject tree;
		synchronized (object)
		{
			impsTuple = object.slot(DEFINITIONS_TUPLE);
			tree = object.testingTree();
		}
		int index = 1;
		while (true)
		{
			int test = tree.tupleIntAt(index);
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NilDescriptor.nil()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
				.acceptsTupleOfArgTypes(argumentTypeTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleIntAt(index + 1);
			}
		}
	}

	/**
	 * Look up the definition to invoke, given an array of argument values.
	 * Use the testingTree to find the definition to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	AvailObject o_LookupByValuesFromList (
		final AvailObject object,
		final List<AvailObject> argumentList)
	{
		final AvailObject impsTuple;
		final AvailObject tree;
		synchronized (object)
		{
			impsTuple = object.slot(DEFINITIONS_TUPLE);
			tree = object.testingTree();
		}
		int index = 1;
		while (true)
		{
			int test = tree.tupleIntAt(index);
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NilDescriptor.nil()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
					.acceptsListOfArgValues(argumentList))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleIntAt(index + 1);
			}
		}
	}

	/**
	 * Look up the definition to invoke, given a tuple of argument values.
	 * Use the testingTree to find the definition to invoke (answer void if
	 * a lookup error occurs).  There may be more entries in the tuple of
	 * arguments than we're interested in (to allow the tuple to be a reusable
	 * buffer).
	 */
	@Override @AvailMethod
	AvailObject o_LookupByValuesFromTuple (
		final AvailObject object,
		final AvailObject argumentTuple)
	{
		final AvailObject impsTuple;
		final AvailObject tree;
		synchronized (object)
		{
			impsTuple = object.slot(DEFINITIONS_TUPLE);
			tree = object.testingTree();
		}
		int index = 1;
		while (true)
		{
			int test = tree.tupleIntAt(index);
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NilDescriptor.nil()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsTupleOfArguments(
				argumentTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleIntAt(index + 1);
			}
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks. This is probably
	 * because the chunk has been (A) removed by the garbage collector, or (B)
	 * invalidated by a new definition in either me or another method that the
	 * chunk is contingent on.
	 */
	@Override @AvailMethod
	void o_RemoveDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		synchronized (object)
		{
			AvailObject indices =
				object.slot(DEPENDENT_CHUNK_INDICES);
			indices = indices.setWithoutElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true);
			object.setSlot(
				DEPENDENT_CHUNK_INDICES, indices.traversed().makeShared());
		}
	}

	/**
	 * Remove the definition from me. Causes dependent chunks to be
	 * invalidated.
	 */
	@Override @AvailMethod
	void o_RemoveDefinition (
		final AvailObject object,
		final AvailObject definition)
	{
		synchronized (object)
		{
			AvailObject definitionsTuple = object.slot(DEFINITIONS_TUPLE);
			definitionsTuple = TupleDescriptor.without(
				definitionsTuple,
				definition);
			object.setSlot(
				DEFINITIONS_TUPLE,
				definitionsTuple.traversed().makeShared());
			membershipChanged(object);
		}
	}

	@Override @AvailMethod
	int o_NumArgs (final AvailObject object)
	{
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final AvailObject impsTuple = object.definitionsTuple();
		if (impsTuple.tupleSize() >= 1)
		{
			final AvailObject firstBody = impsTuple.tupleAt(1).bodySignature();
			final AvailObject argsTupleType = firstBody.argsTupleType();
			return argsTupleType.sizeRange().lowerBound().extractInt();
		}
		// Deal with it the slow way by using the MessageSplitter. This allows
		// the decompiler to continue to work even when a called method has no
		// definitions.
		try
		{
			final MessageSplitter splitter =
				new MessageSplitter(object.name().name());
			return splitter.numberOfArguments();
		}
		catch (final SignatureException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Answer the cached privateTestingTree. If there's a nil in that slot,
	 * compute and cache the testing tree based on the definitionsTuple.
	 * See {@linkplain #createTestingTree(AvailObject[], List, List, List)
	 * createTestingTree(...)} for an interpretation of the resulting tuple of
	 * integers.
	 */
	@Override @AvailMethod
	AvailObject o_TestingTree (final AvailObject object)
	{
		synchronized (object)
		{
			AvailObject result = object.slot(PRIVATE_TESTING_TREE);
			if (result.equalsNil())
			{
				//  Compute the tree.
				final AvailObject definitionsTuple =
					object.slot(DEFINITIONS_TUPLE);
				final int indicesSize = definitionsTuple.tupleSize();
				final List<Integer> allIndices =
					new ArrayList<Integer>(indicesSize);
				for (int i = 0; i < indicesSize; i++)
				{
					allIndices.add(i);
				}
				final List<AvailObject> definitionsList =
					new ArrayList<AvailObject>();
				for (final AvailObject imp : definitionsTuple)
				{
					definitionsList.add(imp);
				}
				final List<Integer> instructions = new ArrayList<Integer>();
				createTestingTree(
					definitionsList.toArray(
						new AvailObject[definitionsList.size()]),
					new ArrayList<Integer>(),
					allIndices,
					instructions);
				result = TupleDescriptor.fromIntegerList(instructions);
				object.setSlot(
					PRIVATE_TESTING_TREE, result.traversed().makeShared());
			}
			return result;
		}
	}

	@Override @AvailMethod
	void o_AddTypeRestriction (
		final AvailObject object,
		final AvailObject function)
	{
		synchronized (object)
		{
			final AvailObject oldTuple =
				object.slot(TYPE_RESTRICTIONS_TUPLE);
			final AvailObject newTuple =
				oldTuple.appendCanDestroy(function, true);
			object.setSlot(
				TYPE_RESTRICTIONS_TUPLE, newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_RemoveTypeRestriction (
		final AvailObject object,
		final AvailObject function)
	{
		synchronized (object)
		{
			final AvailObject oldTuple =
				object.slot(TYPE_RESTRICTIONS_TUPLE);
			final AvailObject newTuple =
				TupleDescriptor.without(oldTuple, function);
			assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
			object.setSlot(
				TYPE_RESTRICTIONS_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddSealedArgumentsType (
		final AvailObject object,
		final AvailObject sealSignature)
	{
		synchronized (object)
		{
			assert sealSignature.isTuple();
			final AvailObject oldTuple =
				object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final AvailObject newTuple = oldTuple.appendCanDestroy(
				sealSignature, true);
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final AvailObject tupleType)
	{
		synchronized (object)
		{
			final AvailObject oldTuple =
				object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final AvailObject newTuple =
				TupleDescriptor.without(oldTuple, tupleType);
			assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	boolean o_IsMethodEmpty (final AvailObject object)
	{
		synchronized (object)
		{
			final AvailObject definitionsTuple =
				object.slot(DEFINITIONS_TUPLE);
			if (definitionsTuple.tupleSize() > 0)
			{
				return false;
			}
			final AvailObject typeRestrictionsTuple =
				object.slot(TYPE_RESTRICTIONS_TUPLE);
			if (typeRestrictionsTuple.tupleSize() > 0)
			{
				return false;
			}
			final AvailObject sealedArgumentsTypesTuple =
				object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			if (sealedArgumentsTypesTuple.tupleSize() > 0)
			{
				return false;
			}
			return true;
		}
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.METHOD;
	}

	/**
	 * Create the testing tree for computing which definition to invoke when
	 * given a list of arguments.  The tree is flattened into a tuple of
	 * integers.  Testing begins with the first element of the tuple.  If it's
	 * odd, divide by two to get the index into definitionsTuple (a zero
	 * index indicates an ambiguous lookup).  If it's even, divide by two to get
	 * an index into definitionsTuple, then test the list of arguments
	 * against it.  If the arguments agree with the signature, add 2 to the
	 * current position (to skip the test number and an offset) and continue.
	 * If the arguments did not agree with the signature, add 2 + the value in
	 * the next slot of the tuple to the current position, then continue.
	 *
	 * <p>
	 * This method recurses once for each node of the resulting tree, using a
	 * simple MinMax algorithm to produce a reasonably balanced testing tree.
	 * The signature to check in a particular state is the one that minimizes
	 * the maximum number of remaining possible solutions after a test.
	 * </p>
	 *
	 * @param imps
	 *        The complete array of definitions to analyze.
	 * @param positives
	 *        Zero-based indices of definitions that are consistent with the
	 *        checks that have been performed to reach this point.
	 * @param possible
	 *        Zero-based indices of definitions that have neither been shown to
	 *        be consistent nor shown to be inconsistent with the checks that
	 *        have been performed to reach this point.
	 * @param instructions
	 *        An output list of Integer-encoded instructions.
	 */
	private static void createTestingTree (
		final AvailObject[] imps,
		final List<Integer> positives,
		final List<Integer> possible,
		final List<Integer> instructions)
	{
		if (possible.isEmpty())
		{
			outer_index:
			for (final int index1 : positives)
			{
				for (final int index2 : positives)
				{
					final AvailObject sig = imps[index2];
					if (!sig.bodySignature().acceptsArgTypesFromFunctionType(
						imps[index1].bodySignature()))
					{
						continue outer_index;
					}
				}
				instructions.add((index1 + 1) * 2 + 1);  //solution
				return;
			}
			// There was no most specific positive signature.  Indicate an
			// ambiguity error at this point in the tree.
			instructions.add(0 * 2 + 1);  //impossible
			return;
		}
		// See if there are any solutions still possible.  Scan the list of
		// possibilities (and known positives), and for each one see if it's
		// more specific than everything in the positive collection.  If there
		// are no such solutions, we are already at a point that represents an
		// ambiguous lookup.
		boolean possibleSolutionExists = false;
		AvailObject possibility;
		for (final int possibleIndex : possible)
		{
			if (!possibleSolutionExists)
			{
				possibility = imps[possibleIndex].bodySignature();
				boolean allPossibleAreParents = true;
				for (final int index2 : positives)
				{
					allPossibleAreParents = allPossibleAreParents
						&& imps[index2].bodySignature()
							.acceptsArgTypesFromFunctionType(possibility);
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		for (final int index1 : positives)
		{
			if (!possibleSolutionExists)
			{
				possibility = imps[index1].bodySignature();
				boolean allPossibleAreParents = true;
				for (final int index2 : positives)
				{
					allPossibleAreParents = allPossibleAreParents &&
						imps[index2].bodySignature()
							.acceptsArgTypesFromFunctionType(possibility);
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		if (!possibleSolutionExists)
		{
			instructions.add(0 * 2 + 1);  //impossible
			return;
		}
		//  Compute a one-layer MinMax to find a good signature to check next.
		int bestIndex = 0;
		int bestMax = possible.size() + 2;
		for (final int index1 : possible)
		{
			possibility = imps[index1];
			int trueCount = 0;
			int falseCount = 0;
			for (final int index2 : possible)
			{
				if (possibility.bodySignature().acceptsArgTypesFromFunctionType(
					imps[index2].bodySignature()))
				{
					trueCount++;
				}
				else
				{
					falseCount++;
				}
			}
		final int maxCount = max(trueCount, falseCount);
			if (maxCount < bestMax)
			{
				bestMax = maxCount;
				bestIndex = index1;
			}
		}
		final AvailObject bestSig = imps[bestIndex].bodySignature();

		// First recurse assuming the test came out true. Move all ancestors of
		// what was tested into the positive collection and out of the
		// possibilities collection. Also remove from the possibilities any
		// signatures that are strictly disjoint from the tested signature. By
		// disjoint I mean that one or more arguments is bottom when the
		// intersection of the signatures is computed.
		List<Integer> newPossible = new ArrayList<Integer>(possible);
		final List<Integer> newPositive = new ArrayList<Integer>(positives);
		for (final int index1 : possible)
		{
			possibility = imps[index1];
			if (possibility.bodySignature().acceptsArgTypesFromFunctionType(
				imps[bestIndex].bodySignature()))
			{
				newPositive.add(index1);
				newPossible.remove(new Integer(index1));
			}
			else
			{
				final AvailObject sig = possibility.bodySignature();
				final AvailObject intersection =
					sig.argsTupleType().typeIntersection(
						bestSig.argsTupleType());
				if (intersection.equals(BottomTypeDescriptor.bottom()))
				{
					newPossible.remove(new Integer(index1));
				}
			}
		}
		// Write a dummy branch and offset
		instructions.add((bestIndex + 1) * 2);  // test
		instructions.add(-666);                 // branch offset to be replaced
		final int startOfTrueSubtree = instructions.size();
		createTestingTree(
			imps,
			newPositive,
			newPossible,
			instructions);
		// Fix up the branch offset.
		assert instructions.get(startOfTrueSubtree - 1) == -666;
		instructions.set(
			startOfTrueSubtree - 1,
			instructions.size() - startOfTrueSubtree);
		// Now recurse assuming the test came out false. Remove all descendants
		// of the tested signature from the possibility collection.
		newPossible = new ArrayList<Integer>(possible);
		for (final int index1 : possible)
		{
			possibility = imps[index1];
			if (imps[bestIndex].bodySignature().acceptsArgTypesFromFunctionType(
				possibility.bodySignature()))
			{
				newPossible.remove(new Integer(index1));
			}
		}
		createTestingTree(
			imps,
			positives,
			newPossible,
			instructions);
	}

	/**
	 * Answer a new {@linkplain MethodDescriptor method}. Use the passed
	 * {@linkplain AtomDescriptor atom} as its name. A method is always
	 * immutable, but its definitionsTuple, privateTestingTree, and
	 * dependentsChunks can all be assigned to.
	 *
	 * @param messageName
	 *        The atom acting as the message name.
	 * @return A new method.
	 */
	public static AvailObject newMethodWithName (
		final AvailObject messageName)
	{
		assert messageName.isAtom();
		final AvailObject result = mutable.create();
		result.setSlot(
			DEFINITIONS_TUPLE,
			TupleDescriptor.empty());
		result.setSlot(
			DEPENDENT_CHUNK_INDICES,
			SetDescriptor.empty());
		result.setSlot(
			NAME,
			messageName);
		result.setSlot(
			PRIVATE_TESTING_TREE,
			TupleDescriptor.empty());
		result.setSlot(
			TYPE_RESTRICTIONS_TUPLE,
			TupleDescriptor.empty());
		result.setSlot(
			SEALED_ARGUMENTS_TYPES_TUPLE,
			TupleDescriptor.empty());
		result.makeShared();
		return result;
	}

	/**
	 * The membership of this {@linkplain MethodDescriptor
	 * method} has changed.  Invalidate anything that depended on
	 * the previous membership, including the {@linkplain
	 * ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any dependent level
	 * two chunks.
	 *
	 * @param object The method that changed.
	 */
	private static void membershipChanged (final AvailObject object)
	{
		assert Thread.holdsLock(object);
		// Invalidate any affected level two chunks.
		final AvailObject chunkIndices =
			object.slot(DEPENDENT_CHUNK_INDICES);
		if (chunkIndices.setSize() > 0)
		{
			for (final AvailObject chunkIndex : chunkIndices.asTuple())
			{
				L2ChunkDescriptor.invalidateChunkAtIndex(
					chunkIndex.extractInt());
			}
			// The chunk invalidations should have removed all dependencies...
			final AvailObject chunkIndicesAfter =
				object.slot(DEPENDENT_CHUNK_INDICES);
			assert chunkIndicesAfter.setSize() == 0;
		}
		// Clear the privateTestingTree cache.
		object.setSlot(
			PRIVATE_TESTING_TREE,
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@link MethodDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link MethodDescriptor}. */
	private static final MethodDescriptor mutable =
		new MethodDescriptor(Mutability.MUTABLE);

	@Override
	MethodDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MethodDescriptor}. */
	private static final MethodDescriptor shared =
		new MethodDescriptor(Mutability.SHARED);

	@Override
	MethodDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	MethodDescriptor shared ()
	{
		return shared;
	}
}
