/**
 * descriptor/ImplementationSetDescriptor.java
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
import static java.lang.Math.max;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.interpreter.*;
import com.avail.utility.*;

/**
 * An implementation set maintains all methods that have the same name.  At
 * compile time a name is looked up and the corresponding implementation set is
 * stored as a literal in the object code.  At runtime the actual method is
 * located within the implementation set and then invoked.  The implementation
 * sets also keep track of bidirectional dependencies, so that a change of
 * membership causes an immediate invalidation of optimized level two code that
 * depends on the previous membership.
 *
 * <p>To support macros safely, an implementation set must contain either all
 * {@linkplain MacroSignatureDescriptor macro signatures} or all non-macro
 * {@linkplain SignatureDescriptor signatures}, but not both.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ImplementationSetDescriptor
extends Descriptor
{
	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@link AtomDescriptor atom} that acts as the true name of this
		 * {@link ImplementationSetDescriptor implementation set}.
		 */
		NAME,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@link SignatureDescriptor
		 * signatures} that constitute this multimethod (or multimacro).
		 */
		IMPLEMENTATIONS_TUPLE,

		/**
		 * A {@link TupleDescriptor tuple} of {@link IntegerDescriptor integers}
		 * that encodes a decision tree for selecting the most specific
		 * multimethod appropriate for the argument types.
		 */
		PRIVATE_TESTING_TREE,

		/**
		 * A {@link TupleDescriptor tuple} of {@linkplain FunctionDescriptor
		 * functions} which, when invoked with suitable {@linkplain
		 * TypeDescriptor types} as arguments, will determine whether the call
		 * arguments have mutually compatible types, and if so produce a type
		 * to which the call's return value is expected to conform.  This type
		 * strengthening is <em>assumed</em> to hold at compile time (of the
		 * call) and <em>checked</em> at runtime.
		 */
		TYPE_RESTRICTIONS_TUPLE,

		/**
		 * A {@link TupleDescriptor tuple} of {@linkplain TupleTypeDescriptor
		 * tuple types} below which new signatures may no longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * L2ChunkDescriptor.IntegerSlots#INDEX indices} of {@linkplain
		 * L2ChunkDescriptor level two chunks} that depend on the membership of
		 * this {@linkplain ImplementationSetDescriptor implementation set}.  A
		 * change to the membership should cause these chunks to be invalidated.
		 */
		DEPENDENT_CHUNK_INDICES
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ImplementationsTuple (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.IMPLEMENTATIONS_TUPLE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.IMPLEMENTATIONS_TUPLE
			|| e == ObjectSlots.PRIVATE_TESTING_TREE
			|| e == ObjectSlots.DEPENDENT_CHUNK_INDICES
			|| e == ObjectSlots.TYPE_RESTRICTIONS_TUPLE
			|| e == ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final int size = object.implementationsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" implementation");
		if (size != 1)
		{
			aStream.append('s');
		}
		aStream.append(" of ");
		aStream.append(object.name().name().asNativeString());
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// ImplementationSets compare by identity.
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.name().hash() + 0x61AF3FC;
	}

	/**
	 * Make the object immutable so it can be shared safely.  If I was mutable I
	 * have to scan my children and make them immutable as well (recursively
	 * down to immutable descendants).  Actually, I allow some of my
	 * slots to be mutable even when I'm immutable.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor = immutable();
		object.name().makeImmutable();
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return IMPLEMENTATION_SET.o();
	}

	@Override @AvailMethod
	void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		// Record the fact that the chunk indexed by aChunkIndex depends on
		// this implementationSet not changing.
		AvailObject indices =
			object.objectSlot(ObjectSlots.DEPENDENT_CHUNK_INDICES);
		indices = indices.setWithElementCanDestroy(
			IntegerDescriptor.fromInt(aChunkIndex),
			true);
		object.objectSlotPut(
			ObjectSlots.DEPENDENT_CHUNK_INDICES,
			indices);
	}

	/**
	 * Add the {@linkplain SignatureDescriptor signature implementation} to me.
	 * Causes dependent chunks to be invalidated.
	 *
	 * <p>Macro signatures and non-macro signatures should not be combined in
	 * the same implementation set.
	 *
	 * @param object The implementation set.
	 * @param implementation A {@linkplain SignatureDescriptor signature} to be
	 *
	 */
	@Override @AvailMethod
	void o_AddImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		final AvailObject oldTuple = object.implementationsTuple();
		if (oldTuple.tupleSize() > 0)
		{
			// Ensure that we're not mixing macro and non-macro signatures.
			assert implementation.isMacro() == oldTuple.tupleAt(1).isMacro();
		}
		AvailObject set = oldTuple.asSet();
		set = set.setWithElementCanDestroy(
			implementation,
			true);
		object.objectSlotPut(
			ObjectSlots.IMPLEMENTATIONS_TUPLE,
			set.asTuple());
		membershipChanged(object);
	}

	/**
	 * Look up all method implementations that could match the given argument
	 * types.  Answer a {@link List list} of {@link
	 * MethodSignatureDescriptor method signatures}.
	 */
	@Override @AvailMethod
	List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		final AvailObject impsTuple = object.implementationsTuple();
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
	 * Look up all method implementations that could match arguments with the
	 * given types, or anything more specific.  This should return the
	 * implementations that could be invoked at runtime at a call site with the
	 * given static types.  This set is subject to change as new methods and
	 * types are created.  If an argType and the corresponding argument type of
	 * an implementation have no possible descendant except bottom, then
	 * disallow the implementation (it could never actually be invoked because
	 * bottom is uninstantiable).  Answer a {@link List list} of {@link
	 * MethodSignatureDescriptor method signatures}.
	 * <p>
	 * Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.
	 */
	@Override @AvailMethod
	List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		final AvailObject impsTuple = object.implementationsTuple();
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
	 * Test if the implementation is present.
	 */
	@Override @AvailMethod
	boolean o_IncludesImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject imp)
	{
		for (final AvailObject signature : object.implementationsTuple())
		{
			if (signature.equals(imp))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Look up the implementation to invoke, given an array of argument types.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_LookupByTypesFromList (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentTypeList)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true)
		{
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NullDescriptor.nullObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsListOfArgTypes(
				argumentTypeList))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Assume the argument types have been pushed in the continuation.  The
	 * object at {@code stackp} is the last argument, and the object at {@code
	 * stackp + numArgs - 1} is the first.  Use the testingTree to find the
	 * implementation to invoke (answer void if a lookup error occurs).
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		final int numArgs = impsTuple.tupleAt(1).numArgs();
		int index = 1;
		while (true)
		{
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NullDescriptor.nullObject()
					: impsTuple.tupleAt(test);
			}
			final AvailObject functionType =
				impsTuple.tupleAt(test).bodySignature();
			if (functionType.acceptsArgumentTypesFromContinuation(
				continuation,
				stackp,
				numArgs))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given an array of argument types.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).  There may be more entries in the tuple of
	 * argument types than we need, to allow the tuple to be a reusable buffer.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTypeTuple)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true)
		{
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NullDescriptor.nullObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
				.acceptsTupleOfArgTypes(argumentTypeTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given an array of argument values.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentList)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true)
		{
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NullDescriptor.nullObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
					.acceptsListOfArgValues(argumentList))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given a tuple of argument values.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).  There may be more entries in the tuple of
	 * arguments than we're interested in (to allow the tuple to be a reusable
	 * buffer).
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTuple)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true)
		{
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? NullDescriptor.nullObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsTupleOfArguments(
				argumentTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks.  This is probably
	 * because the chunk has been (A) removed by the garbage collector, or (B)
	 * invalidated by a new implementation in either me or another
	 * implementation set that the chunk is contingent on.
	 */
	@Override @AvailMethod
	void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		AvailObject indices =
			object.objectSlot(ObjectSlots.DEPENDENT_CHUNK_INDICES);
		indices = indices.setWithoutElementCanDestroy(
			IntegerDescriptor.fromInt(aChunkIndex),
			true);
		object.objectSlotPut(ObjectSlots.DEPENDENT_CHUNK_INDICES, indices);
	}

	/**
	 * Remove the implementation from me.  Causes dependent chunks to be
	 * invalidated.
	 */
	@Override @AvailMethod
	void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		AvailObject implementationsTuple = object.implementationsTuple();
		implementationsTuple = TupleDescriptor.without(
			implementationsTuple,
			implementation);
		object.objectSlotPut(
			ObjectSlots.IMPLEMENTATIONS_TUPLE,
			implementationsTuple);
		membershipChanged(object);
	}

	/**
	 * Answer how many arguments my implementations require.
	 */
	@Override @AvailMethod
	int o_NumArgs (
		final @NotNull AvailObject object)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		assert impsTuple.tupleSize() >= 1;
		final AvailObject first = impsTuple.tupleAt(1);
		final AvailObject argsTupleType = first.bodySignature().argsTupleType();
		return argsTupleType.sizeRange().lowerBound().extractInt();
	}


	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Check the argument types for validity and return the result type of the
	 * message send.  Use not only the applicable {@linkplain
	 * MethodSignatureDescriptor method signatures}, but also any type
	 * restriction functions.  The type restriction functions may choose to
	 * {@linkplain Primitive#prim352_RejectParsing reject} the parse, indicating
	 * that the argument types are mutually incompatible.
	 * </p>
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter anAvailInterpreter,
		final @NotNull Continuation1<Generator<String>> failBlock)
	{
		// Filter the implementations down to those that are locally most
		// specific.  Fail if more than one survives.
		final AvailObject implementationsTuple = object.implementationsTuple();
		if (implementationsTuple.tupleSize() > 0
			&& !implementationsTuple.tupleAt(1).isMacro())
		{
			// This consists of method implementations.
			for (int index = 1, end = argTypes.size(); index <= end; index++)
			{
				final int finalIndex = index;
				if (argTypes.get(finalIndex - 1).equals(
					BottomTypeDescriptor.bottom()))
				{
					failBlock.value(new Generator<String> ()
					{
						@Override
						public String value()
						{
							return "argument #"
								+ Integer.toString(finalIndex)
								+ " of message \""
								+ object.name().name().asNativeString()
								+ "\" to have a type other than bottom";
						}
					});
					return NullDescriptor.nullObject();
				}
				if (argTypes.get(finalIndex - 1).equals(TOP.o()))
				{
					failBlock.value(new Generator<String> ()
					{
						@Override
						public String value()
						{
							return "argument #"
								+ Integer.toString(finalIndex)
								+ " of message \""
								+ object.name().name().asNativeString()
								+ "\" to have a type other than top";
						}
					});
					return NullDescriptor.nullObject();
				}
			}
		}
		final List<AvailObject> satisfyingTypes =
			object.filterByTypes(argTypes);
		if (satisfyingTypes.size() == 0)
		{
			failBlock.value(new Generator<String> ()
			{
				@Override
				public String value()
				{
					final List<AvailObject> signatures =
						new ArrayList<AvailObject>(2);
					for (final AvailObject imp : implementationsTuple)
					{
						signatures.add(imp.bodySignature());
					}
					String string;
					final List<Integer> allFailedIndices =
						new ArrayList<Integer>(3);
					each_arg:
					for (int index = argTypes.size(); index >= 1; index--)
					{
						for (final AvailObject sig : signatures)
						{
							if (argTypes.get(index - 1).isSubtypeOf(
								sig.argsTupleType().typeAtIndex(index)))
							{
								continue each_arg;
							}
						}
						allFailedIndices.add(0, index);
					}
					if (allFailedIndices.size() >= 1
							&& allFailedIndices.size()
								<= argTypes.size() - 1)
					{
						string = "arguments at indices "
							+ allFailedIndices.toString()
							+ " of message "
							+ object.name().name().asNativeString()
							+ " to match a method.  I got: "
							+ argTypes.toString();
					}
					else
					{
						string = "arguments of "
							+ object.name().name().asNativeString()
							+ " to have applicable types like {";
						boolean first = true;
						for (final AvailObject sig : signatures)
						{
							if (!first)
							{
								string += ", ";
							}
							first = false;
							string += "[";
							final AvailObject tupleType = sig.argsTupleType();
							final int numArgs = tupleType
								.sizeRange()
								.lowerBound()
								.extractInt();
							for (int i = 1; i <= numArgs; i++)
							{
								if (i > 1)
								{
									string += ", ";
								}
								string += tupleType.typeAtIndex(i);
							}
							string += "]";
						}
						string += "}, ***not*** " + argTypes.toString();
					}
					return string;
				}
			});
			return NullDescriptor.nullObject();
		}
		// The requires clauses are only checked after a top-level statement has
		// been parsed and is being validated.
		AvailObject intersection =
			satisfyingTypes.get(0).bodySignature().returnType();
		for (int i = satisfyingTypes.size() - 1; i >= 1; i--)
		{
			intersection = intersection.typeIntersection(
				satisfyingTypes.get(i).bodySignature().returnType());
		}
		final AvailObject restrictions = object.typeRestrictions();
		final Mutable<Boolean> anyFailures = new Mutable<Boolean>(false);
		for (int i = restrictions.tupleSize(); i >= 1; i--)
		{
			final AvailObject restriction = restrictions.tupleAt(i);
			if (restriction.kind().acceptsListOfArgValues(argTypes))
			{
				try
				{
					final AvailObject restrictionType =
						anAvailInterpreter.runFunctionArguments(
							restriction,
							argTypes);
					intersection = intersection.typeIntersection(
						restrictionType);
				}
				catch (final AvailRejectedParseException e)
				{
					final AvailObject problem = e.rejectionString();
					failBlock.value(
						new Generator<String>()
						{
							@Override
							public String value ()
							{
								return
									problem.asNativeString()
									+ " (while parsing send of "
									+ object.name().name().asNativeString()
									+ ")";
							}
						});
					anyFailures.value = true;
				}
			}
		}
		if (anyFailures.value)
		{
			return NullDescriptor.nullObject();
		}
		return intersection;
	}

	/**
	 * Answer the cached privateTestingTree.  If there's a null object in that
	 * slot, compute and cache the testing tree based on implementationsSet.
	 * See {@linkplain #createTestingTree(AvailObject[], List, List, List)
	 * createTestingTree(...)} for an interpretation of the resulting tuple of
	 * integers.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_TestingTree (
		final @NotNull AvailObject object)
	{
		AvailObject result =
			object.objectSlot(ObjectSlots.PRIVATE_TESTING_TREE);
		if (!result.equalsNull())
		{
			return result;
		}
		//  Compute the tree.
		final AvailObject implementationsTuple = object.implementationsTuple();
		final int indicesSize = implementationsTuple.tupleSize();
		final List<Integer> allIndices = new ArrayList<Integer>(indicesSize);
		for (int i = 0; i < indicesSize; i++)
		{
			allIndices.add(i);
		}
		final List<AvailObject> implementationsList =
			new ArrayList<AvailObject>();
		for (final AvailObject imp : implementationsTuple)
		{
			implementationsList.add(imp);
		}
		final List<Integer> instructions = new ArrayList<Integer>();
		createTestingTree(
			implementationsList.toArray(
				new AvailObject[implementationsList.size()]),
			new ArrayList<Integer>(),
			allIndices,
			instructions);
		result = TupleDescriptor.fromIntegerList(instructions);
		object.objectSlotPut(ObjectSlots.PRIVATE_TESTING_TREE, result);
		return result;
	}

	@Override @AvailMethod
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject function)
	{
		final AvailObject oldTuple =
			object.objectSlot(ObjectSlots.TYPE_RESTRICTIONS_TUPLE);
		final AvailObject newTuple =
			TupleDescriptor.append(oldTuple, function);
		object.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTIONS_TUPLE,
			newTuple);
	}

	@Override @AvailMethod
	void o_RemoveTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject function)
	{
		final AvailObject oldTuple =
			object.objectSlot(ObjectSlots.TYPE_RESTRICTIONS_TUPLE);
		final AvailObject newTuple =
			TupleDescriptor.without(oldTuple, function);
		assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
		object.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTIONS_TUPLE,
			newTuple);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeRestrictions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TYPE_RESTRICTIONS_TUPLE);
	}

	@Override @AvailMethod
	void o_AddSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		final AvailObject oldTuple =
			object.objectSlot(ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE);
		final AvailObject newTuple =
			TupleDescriptor.append(oldTuple, tupleType);
		object.objectSlotPut(
			ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE,
			newTuple);
	}

	@Override @AvailMethod
	void o_RemoveSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		final AvailObject oldTuple =
			object.objectSlot(ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE);
		final AvailObject newTuple =
			TupleDescriptor.without(oldTuple, tupleType);
		assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
		object.objectSlotPut(
			ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE,
			newTuple);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SealedArgumentsTypesTuple (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	boolean o_IsImplementationSetEmpty (
		final @NotNull AvailObject object)
	{
		final AvailObject implementationsTuple =
			object.objectSlot(ObjectSlots.IMPLEMENTATIONS_TUPLE);
		if (implementationsTuple.tupleSize() > 0)
		{
			return false;
		}
		final AvailObject typeRestrictionsTuple =
			object.objectSlot(ObjectSlots.TYPE_RESTRICTIONS_TUPLE);
		if (typeRestrictionsTuple.tupleSize() > 0)
		{
			return false;
		}
		final AvailObject sealedArgumentsTypesTuple =
			object.objectSlot(ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE);
		if (sealedArgumentsTypesTuple.tupleSize() > 0)
		{
			return false;
		}
		return true;
	}


	/**
	 * Create the testing tree for computing which implementation to invoke when
	 * given a list of arguments.  The tree is flattened into a tuple of
	 * integers.  Testing begins with the first element of the tuple.  If it's
	 * odd, divide by two to get the index into implementationsTuple (a zero
	 * index indicates an ambiguous lookup).  If it's even, divide by two to get
	 * an index into implementationsTuple, then test the list of arguments
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
	 *            The complete array of implementations to analyze.
	 * @param positives
	 *            Zero-based indices of implementations that are consistent
	 *            with the checks that have been performed to reach this point.
	 * @param possible
	 *            Zero-based indices of implementations that have neither been
	 *            shown to be consistent nor shown to be inconsistent with
	 *            the checks that have been performed to reach this point.
	 * @param instructions
	 *            An output list of Integer-encoded instructions.
	 */
	static public void createTestingTree (
		final @NotNull AvailObject[] imps,
		final @NotNull List<Integer> positives,
		final @NotNull List<Integer> possible,
		final @NotNull List<Integer> instructions)
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

		// First recurse assuming the test came out true.  Move all ancestors of
		// what was tested into the positive collection and out of the
		// possibilities collection.  Also remove from the possibilities any
		// signatures that are strictly disjoint from the tested signature.  By
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
		// Now recurse assuming the test came out false.  Remove all descendants
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
	 * Answer a new implementation set.  Use the passed cyclicType as its name.
	 * An implementation set is always immutable, but its implementationsTuple,
	 * privateTestingTree, and dependentsChunks can all be assigned to.
	 *
	 * @param messageName
	 *            The {@link AtomDescriptor atom} acting as the message name.
	 * @return
	 *            A new {@link ImplementationSetDescriptor implementation set}.
	 */
	public static AvailObject newImplementationSetWithName (
		final AvailObject messageName)
	{
		assert messageName.isAtom();
		final AvailObject result = mutable().create();
		result.objectSlotPut(
			ObjectSlots.IMPLEMENTATIONS_TUPLE,
			TupleDescriptor.empty());
		result.objectSlotPut(
			ObjectSlots.DEPENDENT_CHUNK_INDICES,
			SetDescriptor.empty());
		result.objectSlotPut(
			ObjectSlots.NAME,
			messageName);
		result.objectSlotPut(
			ObjectSlots.PRIVATE_TESTING_TREE,
			TupleDescriptor.empty());
		result.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTIONS_TUPLE,
			TupleDescriptor.empty());
		result.objectSlotPut(
			ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE,
			TupleDescriptor.empty());
		result.makeImmutable();
		return result;
	}

	/**
	 * The membership of this {@linkplain ImplementationSetDescriptor
	 * implementation set} has changed.  Invalidate anything that depended on
	 * the previous membership, including the {@linkplain
	 * ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any dependent level
	 * two chunks.
	 *
	 * @param object The implementation set that changed.
	 */
	private static void membershipChanged (
		final @NotNull AvailObject object)
	{
		// Invalidate any affected level two chunks.
		final AvailObject chunkIndices =
			object.objectSlot(ObjectSlots.DEPENDENT_CHUNK_INDICES);
		if (chunkIndices.setSize() > 0)
		{
			for (final AvailObject chunkIndex : chunkIndices.asTuple())
			{
				L2ChunkDescriptor.invalidateChunkAtIndex(
					chunkIndex.extractInt());
			}
			// The chunk invalidations should have removed all dependencies...
			final AvailObject chunkIndicesAfter =
				object.objectSlot(ObjectSlots.DEPENDENT_CHUNK_INDICES);
			assert chunkIndicesAfter.setSize() == 0;
		}
		// Clear the privateTestingTree cache.
		object.objectSlotPut(
			ObjectSlots.PRIVATE_TESTING_TREE,
			NullDescriptor.nullObject());
	}

	/**
	 * Construct a new {@link ImplementationSetDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ImplementationSetDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ImplementationSetDescriptor}.
	 */
	private final static ImplementationSetDescriptor mutable =
		new ImplementationSetDescriptor(true);

	/**
	 * Answer the mutable {@link ImplementationSetDescriptor}.
	 *
	 * @return The mutable {@link ImplementationSetDescriptor}.
	 */
	public static ImplementationSetDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ImplementationSetDescriptor}.
	 */
	private final static ImplementationSetDescriptor immutable =
		new ImplementationSetDescriptor(false);

	/**
	 * Answer the immutable {@link ImplementationSetDescriptor}.
	 *
	 * @return The immutable {@link ImplementationSetDescriptor}.
	 */
	public static ImplementationSetDescriptor immutable ()
	{
		return immutable;
	}
}
