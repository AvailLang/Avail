/**
 * MethodDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeAdaptor;
import com.avail.dispatch.TypeComparison;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.primitive.atoms.P_AtomSetProperty;
import com.avail.interpreter.primitive.continuations.P_ContinuationCaller;
import com.avail.interpreter.primitive.controlflow.P_InvokeWithTuple;
import com.avail.interpreter.primitive.controlflow.P_ResumeContinuation;
import com.avail.interpreter.primitive.general.P_DeclareStringificationAtom;
import com.avail.interpreter.primitive.general.P_EmergencyExit;
import com.avail.interpreter.primitive.methods.*;
import com.avail.interpreter.primitive.modules.P_DeclareAllExportedAtoms;
import com.avail.interpreter.primitive.phrases.P_CreateLiteralExpression;
import com.avail.interpreter.primitive.phrases.P_CreateLiteralToken;
import com.avail.interpreter.primitive.variables.P_GetValue;
import com.avail.optimizer.L2Translator;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.avail.descriptor.MethodDescriptor.IntegerSlots.*;
import static com.avail.descriptor.MethodDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.METHOD;

/**
 * A method maintains all definitions that have the same name.  At compile time
 * a name is looked up and the corresponding method is stored as a literal in
 * the object code for a call site.  At runtime the actual function is located
 * within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * <p>Methods and macros are stored in separate lists.  Note that macros may be
 * polymorphic (multiple {@linkplain MacroDefinitionDescriptor definitions}),
 * and a lookup structure is used at compile time to decide which macro is most
 * specific.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MethodDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the hash and the argument count.  See below.
		 */
		@HideFieldInDebugger
		HASH_AND_NUM_ARGS;

		/**
		 * The hash of this method.  It's set to a random number during
		 * construction.
		 */
		static final BitField HASH = bitField(
			HASH_AND_NUM_ARGS, 0, 32);

		/**
		 * The number of arguments expected by this method.  Set at construction
		 * time.
		 */
		static final BitField NUM_ARGS = bitField(
			HASH_AND_NUM_ARGS, 32, 32);
	}

	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * MessageBundleDescriptor message bundles} that name this method.  The
		 * method itself has no intrinsic name, as its bundles completely
		 * determine what it is called in various modules (based on the module
		 * scope of the bundles' {@linkplain AtomDescriptor atomic names}).
		 *
		 * TODO [MvG] - Maybe this should be a weak set, and the members should
		 * first be forced to be traversed (across indirections) and Shared.
		 */
		OWNING_BUNDLES,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain
		 * DefinitionDescriptor definitions} that constitute this multimethod.
		 */
		DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a {@link
		 * LookupTree} used to determine the most specific method definition
		 * that satisfies the supplied argument types.  A {@linkplain
		 * NilDescriptor#nil() nil} indicates the tree has not yet been
		 * constructed.
		 */
		PRIVATE_TESTING_TREE,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * SemanticRestrictionDescriptor semantic restrictions} which, when
		 * their functions are invoked with suitable {@linkplain TypeDescriptor
		 * types} as arguments, will determine whether the call arguments have
		 * mutually compatible types, and if so produce a type to which the
		 * call's return value is expected to conform.  This type strengthening
		 * is <em>assumed</em> to hold at compile time (of the call) and
		 * <em>checked</em> at runtime.
		 *
		 * <p>When the {@link L2Translator} inlines a {@link Primitive} method
		 * definition, it asks the primitive what type it guarantees ({@link
		 * Primitive#returnTypeGuaranteedByVM(List)}) to return for the specific
		 * provided argument types.  If that return type is sufficiently strong,
		 * the above runtime check may be waived.</p>
		 */
		SEMANTIC_RESTRICTIONS_SET,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * TupleTypeDescriptor tuple types} below which new signatures may no
		 * longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a weak set
		 * (implemented as the {@linkplain Map#keySet() key set} of a {@link
		 * WeakHashMap}) of {@link L2Chunk}s that depend on the membership of
		 * this method.  A change to the membership will invalidate all such
		 * chunks.  This field holds the {@linkplain NilDescriptor#nil() nil}
		 * object initially.
		 */
		DEPENDENT_CHUNKS_WEAK_SET_POJO,

		/**
		 * The {@linkplain A_Tuple tuple} of {@linkplain
		 * MacroDefinitionDescriptor macro definitions} that are defined for
		 * this macro.
		 */
		MACRO_DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a {@link
		 * LookupTree} used to determine the most specific {@linkplain
		 * MacroDefinitionDescriptor macro definition} that satisfies the
		 * supplied argument types.  A {@linkplain NilDescriptor#nil() nil}
		 * indicates the tree has not yet been constructed.
		 */
		MACRO_TESTING_TREE;
	}

	/**
	 * A {@link LookupTreeAdaptor} used for building and navigating the {@link
	 * LookupTree}s that implement runtime dispatching.  Also used for looking
	 * up macros.
	 *
	 * @see MethodDescriptor.ObjectSlots#PRIVATE_TESTING_TREE
	 * @see MethodDescriptor.ObjectSlots#MACRO_TESTING_TREE
	 */
	public final static LookupTreeAdaptor<A_Definition, A_Tuple, Void>
		runtimeDispatcher = new LookupTreeAdaptor<A_Definition, A_Tuple, Void>()
	{
		@Override
		public A_Type extractSignature (final A_Definition element)
		{
			return element.bodySignature().argsTupleType();
		}

		@Override
		public A_Tuple constructResult (
			final List<? extends A_Definition> elements,
			final Void ignored)
		{
			return TupleDescriptor.fromList(elements);
		}

		@Override
		public TypeComparison compareTypes (
			final A_Type criterionType, final A_Type someType)
		{
			return TypeComparison.compareForDispatch(criterionType, someType);
		}

		@Override
		public boolean testsArgumentPositions ()
		{
			return true;
		}

		@Override
		public boolean subtypesHideSupertypes ()
		{
			return true;
		}
	};

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == OWNING_BUNDLES
			|| e == DEFINITIONS_TUPLE
			|| e == PRIVATE_TESTING_TREE
			|| e == SEMANTIC_RESTRICTIONS_SET
			|| e == SEALED_ARGUMENTS_TYPES_TUPLE
			|| e == MACRO_DEFINITIONS_TUPLE
			|| e == MACRO_TESTING_TREE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final int size =
			object.definitionsTuple().tupleSize()
			+ object.macroDefinitionsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" definition");
		if (size != 1)
		{
			aStream.append('s');
		}
		aStream.append(" of ");
		boolean first = true;
		for (final A_Bundle eachBundle : object.bundles())
		{
			if (!first)
			{
				aStream.append(" a.k.a. ");
			}
			aStream.append(eachBundle.message());
			first = false;
		}
	}

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		// Record the fact that the given chunk depends on this object not
		// changing.  Local synchronization is sufficient, since invalidation
		// can't happen while L2 code is running (and therefore when the
		// L2Translator could be calling this).
		synchronized (object)
		{
			final A_BasicObject pojo =
				object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
			final Set<L2Chunk> chunkSet =
				(Set<L2Chunk>) pojo.javaObjectNotNull();
			chunkSet.add(chunk);
		}
	}

	@Override @AvailMethod
	void o_AddSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		synchronized (object)
		{
			assert typeTuple.isTuple();
			final A_Tuple oldTuple = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Tuple newTuple = oldTuple.appendCanDestroy(typeTuple, true);
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		synchronized (object)
		{
			A_Set set = object.slot(SEMANTIC_RESTRICTIONS_SET);
			set = set.setWithElementCanDestroy(restriction, true);
			object.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared());
		}
	}

	@Override
	A_Set o_Bundles (final AvailObject object)
	{
		return object.slot(OWNING_BUNDLES);
	}

	@Override
	A_Bundle o_ChooseBundle (final AvailObject object)
	{
		final AvailLoader loader = Interpreter.current().availLoader();
		final A_Set visibleModules = loader.module().allAncestors();
		final A_Set bundles = object.slot(OWNING_BUNDLES);
		for (final A_Bundle bundle : bundles)
		{
			if (visibleModules.hasElement(bundle.message().issuingModule()))
			{
				return bundle;
			}
		}
		return bundles.iterator().next();
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
	List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		final List<A_Definition> result = new ArrayList<>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final A_Tuple definitionsTuple = object.definitionsTuple();
		for (final A_Definition definition : definitionsTuple)
		{
			if (definition.bodySignature().couldEverBeInvokedWith(argTypes))
			{
				result.add(definition);
			}
		}
		return result;
	}

	@Override @AvailMethod
	A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(DEFINITIONS_TUPLE);
		}
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Look up all method definitions that could match the given argument
	 * types. Answer a {@linkplain List list} of {@linkplain
	 * MethodDefinitionDescriptor method signatures}.
	 */
	@Override @AvailMethod
	List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		final List<A_Definition> result = new ArrayList<>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final A_Tuple impsTuple = object.definitionsTuple();
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

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	/**
	 * Test if the definition is present within this method.
	 */
	@Override @AvailMethod
	boolean o_IncludesDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		for (final A_BasicObject eachDefinition : object.definitionsTuple())
		{
			if (eachDefinition.equals(definition))
			{
				return true;
			}
		}
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMethodEmpty (final AvailObject object)
	{
		synchronized (object)
		{
			final A_Tuple definitionsTuple = object.slot(DEFINITIONS_TUPLE);
			if (definitionsTuple.tupleSize() > 0)
			{
				return false;
			}
			final A_Set semanticRestrictions =
				object.slot(SEMANTIC_RESTRICTIONS_SET);
			if (semanticRestrictions.setSize() > 0)
			{
				return false;
			}
			final A_Tuple sealedArgumentsTypesTuple =
				object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			return sealedArgumentsTypesTuple.tupleSize() <= 0
				&& object.slot(MACRO_DEFINITIONS_TUPLE).tupleSize() == 0;
		}
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return METHOD.o();
	}

	/**
	 * Look up the definition to invoke, given a tuple of argument types.
	 * Use the testingTree to find the definition to invoke.
	 */
	@Override @AvailMethod
	A_Definition o_LookupByTypesFromTuple (
		final AvailObject object,
		final A_Tuple argumentTypeTuple)
	throws MethodDefinitionException
	{
		@SuppressWarnings("unchecked")
		LookupTree<A_Definition, A_Tuple, Void> tree =
			(LookupTree<A_Definition, A_Tuple, Void>)
				object.slot(PRIVATE_TESTING_TREE).javaObjectNotNull();
		A_Tuple resultTuple = runtimeDispatcher.lookupByTypes(
			tree, argumentTypeTuple, null);
		return MethodDefinitionException.extractUniqueMethod(resultTuple);
	}

	/**
	 * Look up the definition to invoke, given an array of argument values.
	 * Use the testingTree to find the definition to invoke (answer nil if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		@SuppressWarnings("unchecked")
		LookupTree<A_Definition, A_Tuple, Void> tree =
			(LookupTree<A_Definition, A_Tuple, Void>)
				object.slot(PRIVATE_TESTING_TREE).javaObjectNotNull();
		A_Tuple results = runtimeDispatcher.lookupByValues(
			tree, argumentList, null);
		return MethodDefinitionException.extractUniqueMethod(results);
	}

	/**
	 * Look up the macro definition to invoke, given an array of argument
	 * phrases.  Use the {@linkplain
	 * ObjectSlots#MACRO_TESTING_TREE macro testing tree} to
	 * find the macro definition to invoke.  Throw a {@link
	 * MethodDefinitionException} if the macro cannot be determined uniquely.
	 *
	 * <p>Note that this testing tree approach is only applicable if all of the
	 * macro definitions are visible (defined in the current module or an
	 * ancestor.  That should be the <em>vast</em> majority of the use of
	 * macros, but when it isn't, other lookup approaches are necessary.</p>
	 */
	@Override @AvailMethod
	A_Definition o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple)
	throws MethodDefinitionException
	{
		@SuppressWarnings("unchecked")
		LookupTree<A_Definition, A_Tuple, Void> tree =
			(LookupTree<A_Definition, A_Tuple, Void>)
				object.slot(MACRO_TESTING_TREE).javaObjectNotNull();
		final A_Tuple results =
			runtimeDispatcher.lookupByValues(
				tree, argumentPhraseTuple, null);
		return MethodDefinitionException.extractUniqueMethod(results);
	}

	@Override @AvailMethod
	A_Tuple o_MacroDefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(MACRO_DEFINITIONS_TUPLE);
		}
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
	void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		A_Set bundles = object.slot(OWNING_BUNDLES);
		bundles = bundles.setWithElementCanDestroy(bundle, false);
		bundles.makeShared();
		object.setSlot(OWNING_BUNDLES, bundles);
	}

	@Override @AvailMethod
	void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Translator from these changes.
		//
		// Also create definition parsing plans for each bundle.  HOWEVER, note
		// that we don't update the current module's message bundle tree here,
		// and leave that to the caller to deal with.  Other modules' parsing
		// should be unaffected (although runtime execution may change).
		L2Chunk.invalidationLock.lock();
		try
		{
			final A_Type bodySignature = definition.bodySignature();
			final A_Type paramTypes = bodySignature.argsTupleType();
			if (definition.isMacroDefinition())
			{
				// Install the macro.
				final A_Tuple oldTuple = object.slot(MACRO_DEFINITIONS_TUPLE);
				final A_Tuple newTuple = oldTuple.appendCanDestroy(
					definition, true);
				object.setSlot(MACRO_DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			else
			{
				final A_Tuple oldTuple = object.slot(DEFINITIONS_TUPLE);
				final A_Tuple seals = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
				for (final A_Tuple seal : seals)
				{
					final A_Type sealType =
						TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
							IntegerRangeTypeDescriptor.singleInt(
								seal.tupleSize()),
							seal,
							BottomTypeDescriptor.bottom());
					if (paramTypes.isSubtypeOf(sealType))
					{
						throw new SignatureException(
							AvailErrorCode.E_METHOD_IS_SEALED);
					}
				}
				final A_Tuple newTuple = oldTuple.appendCanDestroy(
					definition, true);
				object.setSlot(DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			for (A_Bundle bundle : object.slot(OWNING_BUNDLES))
			{
				final A_DefinitionParsingPlan plan =
					DefinitionParsingPlanDescriptor.createPlan(
						bundle, definition);
				bundle.addDefinitionParsingPlan(plan);
			}
			membershipChanged(object);
		}
		finally
		{
			L2Chunk.invalidationLock.unlock();
		}
	}

	@Override
	A_String o_MethodName (final AvailObject object)
	{
		return object.chooseBundle().message().atomName();
	}

	@Override @AvailMethod
	int o_NumArgs (final AvailObject object)
	{
		return object.slot(NUM_ARGS);
	}

	/**
	 * Remove the definition from me. Causes dependent chunks to be
	 * invalidated.
	 */
	@Override @AvailMethod
	void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		assert !definition.definitionModule().equalsNil();
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Translator from these changes.
		L2Chunk.invalidationLock.lock();
		try
		{
			final ObjectSlotsEnum slot =
				!definition.isMacroDefinition()
					? DEFINITIONS_TUPLE
					: MACRO_DEFINITIONS_TUPLE;
			A_Tuple definitionsTuple = object.slot(slot);
			definitionsTuple = TupleDescriptor.without(
				definitionsTuple, definition);
			object.setSlot(
				slot, definitionsTuple.traversed().makeShared());
			for (final A_Bundle bundle : object.slot(OWNING_BUNDLES))
			{
				bundle.removePlanForDefinition(definition);
			}
			membershipChanged(object);
		}
		finally
		{
			L2Chunk.invalidationLock.unlock();
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks because it has been
	 * invalidated by a new definition in either me or another method on which
	 * the chunk is contingent.
	 */
	@Override @AvailMethod
	void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		@SuppressWarnings("unchecked")
		final Set<L2Chunk> chunkSet = (Set<L2Chunk>) pojo.javaObjectNotNull();
		chunkSet.remove(chunk);
	}

	@Override @AvailMethod
	void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		synchronized (object)
		{
			final A_Tuple oldTuple = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Tuple newTuple =
				TupleDescriptor.without(oldTuple, typeTuple);
			assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_RemoveSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		synchronized (object)
		{
			A_Set set = object.slot(SEMANTIC_RESTRICTIONS_SET);
			set = set.setWithoutElementCanDestroy(restriction, true);
			object.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared());
		}
	}

	@Override @AvailMethod
	A_Tuple o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		return object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	A_Set o_SemanticRestrictions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(SEMANTIC_RESTRICTIONS_SET);
		}
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.METHOD;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method");
		writer.write("aliases");
		object.slot(OWNING_BUNDLES).writeTo(writer);
		writer.write("definitions");
		object.slot(DEFINITIONS_TUPLE).writeTo(writer);
		writer.write("macro definitions");
		object.slot(MACRO_DEFINITIONS_TUPLE).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method");
		writer.write("aliases");
		object.slot(OWNING_BUNDLES).writeSummaryTo(writer);
		writer.write("definitions");
		object.slot(DEFINITIONS_TUPLE).writeSummaryTo(writer);
		writer.write("macro definitions");
		object.slot(MACRO_DEFINITIONS_TUPLE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Answer a new {@linkplain MethodDescriptor method}. It has no name yet,
	 * but will before it gets used in a send node.  It gets named by virtue of
	 * it being referenced by one or more {@linkplain MessageBundleDescriptor
	 * message bundle}s, each of which keeps track of how to parse it using that
	 * bundle's name.  The bundles will be grouped into a bundle tree to allow
	 * parsing of many possible message sends in aggregate.
	 *
	 * <p>A method is always {@linkplain Mutability#SHARED shared}, but its
	 * set of owning bundles, its tuple of definitions, its cached
	 * privateTestingTree, its macro testing tree, and its set of dependents
	 * chunk indices can all be updated (while holding a lock).</p>
	 *
	 * @param numArgs
	 *        The number of arguments that this method expects.
	 * @return A new method with no name.
	 */
	static AvailObject newMethod (
		final int numArgs)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH, AvailRuntime.nextHash());
		result.setSlot(NUM_ARGS, numArgs);
		result.setSlot(OWNING_BUNDLES, SetDescriptor.empty());
		result.setSlot(DEFINITIONS_TUPLE, TupleDescriptor.empty());
		result.setSlot(SEMANTIC_RESTRICTIONS_SET, SetDescriptor.empty());
		result.setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, TupleDescriptor.empty());
		result.setSlot(MACRO_DEFINITIONS_TUPLE, TupleDescriptor.empty());
		final Set<L2Chunk> chunkSet = Collections.newSetFromMap(
			new WeakHashMap<L2Chunk, Boolean>());
		result.setSlot(
			DEPENDENT_CHUNKS_WEAK_SET_POJO,
			RawPojoDescriptor.identityWrap(chunkSet).makeShared());
		final List<A_Type> initialTypes = nCopiesOfAny(numArgs);
		final LookupTree<A_Definition, A_Tuple, Void> definitionsTree =
			runtimeDispatcher.createRoot(
				Collections.<A_Definition>emptyList(), initialTypes, null);
		result.setSlot(
			PRIVATE_TESTING_TREE,
			RawPojoDescriptor.identityWrap(definitionsTree).makeShared());
		final LookupTree<A_Definition, A_Tuple, Void> macrosTree =
			runtimeDispatcher.createRoot(
				Collections.<A_Definition>emptyList(), initialTypes, null);
		result.setSlot(
			MACRO_TESTING_TREE,
			RawPojoDescriptor.identityWrap(macrosTree).makeShared());
		result.makeShared();
		return result;
	}

	/** The number of lists to cache of N occurrences of the type any. */
	private static final int sizeOfListsOfAny = 10;

	/** A list of lists of increasing size consisting only of the type any. */
	private static final List<List<A_Type>> listsOfAny;

	static
	{
		listsOfAny = new ArrayList<>(sizeOfListsOfAny);
		for (int i = 0; i < sizeOfListsOfAny; i++)
		{
			listsOfAny.add(Collections.<A_Type>nCopies(i, ANY.o()));
		}
	}

	/**
	 * Return a list of n copies of the type any.  N is required to be ≥ 0.
	 *
	 * @param n The number of elements in the desired list, all the type any.
	 * @return The list.  Do not modify it, as it may be cached and reused.
	 */
	private static List<A_Type> nCopiesOfAny (final int n)
	{
		if (n < sizeOfListsOfAny)
		{
			return listsOfAny.get(n);
		}
		return Collections.<A_Type>nCopies(n, ANY.o());
	}

	/**
	 * The membership of this {@linkplain MethodDescriptor method} has changed.
	 * Invalidate anything that depended on the previous membership, including
	 * the {@linkplain ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any
	 * {@linkplain ObjectSlots#DEPENDENT_CHUNKS_WEAK_SET_POJO dependent}
	 * {@link L2Chunk}s.
	 *
	 * @param object The method that changed.
	 */
	private static void membershipChanged (final AvailObject object)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		// Invalidate any affected level two chunks.
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		// Copy the set of chunks to avoid modification during iteration.
		@SuppressWarnings("unchecked")
		final Set<L2Chunk> originalSet =
			(Set<L2Chunk>) pojo.javaObjectNotNull();
		for (final L2Chunk chunk : new ArrayList<>(originalSet))
		{
			chunk.invalidate();
		}
		// The chunk invalidations should have removed all dependencies.
		assert originalSet.isEmpty();

		// Rebuild the roots of the lookup trees.
		final int numArgs = object.slot(NUM_ARGS);
		final List<A_Type> initialTypes = nCopiesOfAny(numArgs);
		final LookupTree<A_Definition, A_Tuple, Void> definitionsTree =
			runtimeDispatcher.createRoot(
				TupleDescriptor.<A_Definition>toList(
					object.slot(DEFINITIONS_TUPLE)),
				initialTypes,
				null);
		object.setSlot(
			PRIVATE_TESTING_TREE,
			RawPojoDescriptor.identityWrap(definitionsTree).makeShared());
		final LookupTree<A_Definition, A_Tuple, Void> macrosTree =
			runtimeDispatcher.createRoot(
				TupleDescriptor.<A_Definition>toList(
					object.slot(MACRO_DEFINITIONS_TUPLE)),
				initialTypes,
				null);
		object.setSlot(
			MACRO_TESTING_TREE,
			RawPojoDescriptor.identityWrap(macrosTree).makeShared());
	}

	/**
	 * Construct a new {@link MethodDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
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

	/**
	 * Create a new special atom with the given name.  The name is not globally
	 * unique, but serves to help to visually distinguish atoms.  Also create
	 * a {@linkplain MessageBundleDescriptor message bundle} within it, with its
	 * own {@linkplain MethodDescriptor method}.  Add {@linkplain
	 * MethodDefinitionDescriptor method definitions} implementing the specified
	 * primitives.
	 *
	 * @param name
	 *        A string used to help identify the new atom.
	 * @param primitives
	 *        The {@link Primitive}s to instantiate as method definitions in
	 *        this atom's message bundle's method.
	 * @return
	 *        The new atom, not equal to any object in use before this method
	 *        was invoked.
	 */
	private static A_Atom createSpecialMethodAtom (
		final String name,
		final Primitive... primitives)
	{
		final A_Atom atom = AtomDescriptor.createSpecialAtom(name);
		final A_Bundle bundle;
		try
		{
			bundle = atom.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			assert false : "This should not happen!";
			throw new RuntimeException(
				"VM method name is invalid: " + name.toString(), e);
		}
		final A_Method method = bundle.bundleMethod();
		for (final Primitive primitive : primitives)
		{
			final A_Function function = FunctionDescriptor.newPrimitiveFunction(
				primitive, NilDescriptor.nil(), 0);
			final A_Definition definition = MethodDefinitionDescriptor.create(
				method,
				NilDescriptor.nil(),  // System definitions have no module.
				function);
			try
			{
				method.methodAddDefinition(definition);
			}
			catch (final SignatureException e)
			{
				assert false : "This should not happen!";
				throw new RuntimeException(
					"VM method name is invalid: " + name, e);
			}
		}
		assert atom.descriptor().isShared();
		assert atom.isAtomSpecial();
		return atom;
	}

	/**
	 * A special {@linkplain AtomDescriptor atom} used to name the VM's method
	 * to crash during early bootstrapping problems.
	 */
	private static final A_Atom vmCrashAtom = createSpecialMethodAtom(
		"vm crash:_",
		P_EmergencyExit.instance);

	/**
	 * Answer the {@linkplain AtomDescriptor atom} used by the VM to name the
	 * method which is invoked in the event of a bootstrapping problem.
	 *
	 * @return The atom.
	 */
	public static A_Atom vmCrashAtom ()
	{
		return vmCrashAtom;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap method-defining method.
	 */
	private static final A_Atom vmMethodDefinerAtom = createSpecialMethodAtom(
		"vm method_is_",
		P_SimpleMethodDeclaration.instance,
		P_MethodDeclarationFromAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new methods.
	 *
	 * @return The name of the bootstrap method-defining method.
	 */
	public static A_Atom vmMethodDefinerAtom ()
	{
		return vmMethodDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap macro-defining method.
	 */
	private static final A_Atom vmMacroDefinerAtom = createSpecialMethodAtom(
		"vm macro_is«_,»_",
		P_SimpleMacroDeclaration.instance,
		P_SimpleMacroDefinitionForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new macros.
	 *
	 * @return The name of the bootstrap macro-defining method.
	 */
	public static A_Atom vmMacroDefinerAtom ()
	{
		return vmMacroDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built method used to load abstract
	 * definitions.
	 */
	private static final A_Atom vmAbstractDefinerAtom = createSpecialMethodAtom(
		"vm abstract_for_",
		P_AbstractMethodDeclarationForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to load abstract definitions.
	 *
	 * @return The name of the abstract definer method.
	 */
	public static A_Atom vmAbstractDefinerAtom ()
	{
		return vmAbstractDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built method used to load forward
	 * definitions.
	 */
	private static final A_Atom vmForwardDefinerAtom = createSpecialMethodAtom(
		"vm forward_for_",
		P_ForwardMethodDeclarationForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to load forward method definitions.
	 *
	 * @return The name of the forward definer method.
	 */
	public static A_Atom vmForwardDefinerAtom ()
	{
		return vmForwardDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define grammatical
	 * restrictions.
	 */
	private static final A_Atom vmGrammaticalRestrictionsAtom =
		createSpecialMethodAtom(
			"vm grammatical restriction_is_",
			P_GrammaticalRestrictionFromAtoms.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to define grammatical restrictions.
	 *
	 * @return The name of the grammatical restriction method.
	 */
	public static A_Atom vmGrammaticalRestrictionsAtom ()
	{
		return vmGrammaticalRestrictionsAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define semantic
	 * restrictions.
	 */
	private static final A_Atom vmSemanticRestrictionAtom =
		createSpecialMethodAtom(
			"vm semantic restriction_is_",
			P_AddSemanticRestrictionForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to declare semantic restrictions.
	 *
	 * @return The name of the method for adding semantic restrictions.
	 */
	public static A_Atom vmSemanticRestrictionAtom ()
	{
		return vmSemanticRestrictionAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define seal methods.
	 */
	private static final A_Atom vmSealAtom =
		createSpecialMethodAtom(
			"vm seal_at_",
			P_SealMethodByAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to seal methods.
	 *
	 * @return The name of the sealing method.
	 */
	public static A_Atom vmSealAtom ()
	{
		return vmSealAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define aliased atoms.
	 */
	private static final A_Atom vmAliasAtom =
		createSpecialMethodAtom(
			"vm alias new name_to_",
			P_Alias.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to created aliased atoms.
	 *
	 * @return The name of the aliasing method.
	 */
	public static A_Atom vmAliasAtom ()
	{
		return vmAliasAtom;
	}

	/**
	 * The (special) name of the VM-built method used to add atom properties.
	 */
	private static final A_Atom vmAddAtomPropertyAtom =
		createSpecialMethodAtom(
			"vm atom_at property_put_",
			P_AtomSetProperty.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to created add properties to atoms.
	 *
	 * @return The name of the method that adds atom properties.
	 */
	public static A_Atom vmAddAtomPropertyAtom ()
	{
		return vmAddAtomPropertyAtom;
	}

	/**
	 * The (special) name of the VM-built function application method.
	 */
	private static final A_Atom vmFunctionApplyAtom = createSpecialMethodAtom(
		"vm function apply_(«_‡,»)",
		P_InvokeWithTuple.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function application method.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static A_Atom vmFunctionApplyAtom ()
	{
		return vmFunctionApplyAtom;
	}

	/**
	 * The (special) name of the VM-built atom-set publication method.
	 */
	private static final A_Atom vmPublishAtomsAtom = createSpecialMethodAtom(
		"vm publish atom set_(public=_)",
		P_DeclareAllExportedAtoms.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function which publishes a set of atoms from the current module.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static A_Atom vmPublishAtomsAtom ()
	{
		return vmPublishAtomsAtom;
	}

	/**
	 * The (special) name of the VM-built stringifier declaration atom.
	 */
	private static final A_Atom vmDeclareStringifierAtom =
		createSpecialMethodAtom(
			"vm stringifier:=_",
			P_DeclareStringificationAtom.instance);

	/**
	 * Answer the (special) name of the VM-built stringifier declaration atom.
	 *
	 * @return The name of the VM's stringifier declaration atom.
	 */
	public static A_Atom vmDeclareStringifierAtom ()
	{
		return vmDeclareStringifierAtom;
	}

	/**
	 * The (special) name of the VM-built continuation caller atom.
	 */
	private static final A_Atom vmContinuationCallerAtom =
		createSpecialMethodAtom(
			"vm_'s caller",
			P_ContinuationCaller.instance);

	/**
	 * Answer the (special) name of the VM-built continuation caller atom.
	 *
	 * @return The name of the VM's continuation caller atom.
	 */
	public static A_Atom vmContinuationCallerAtom ()
	{
		return vmContinuationCallerAtom;
	}

	/**
	 * The (special) name of the VM-built variable accessor atom.
	 */
	private static final A_Atom vmVariableGetAtom =
		createSpecialMethodAtom(
			"vm↓_",
			P_GetValue.instance);

	/**
	 * Answer the (special) name of the VM-built variable accessor atom.
	 *
	 * @return The name of the VM's variable accessor atom.
	 */
	public static A_Atom vmVariableGetAtom ()
	{
		return vmVariableGetAtom;
	}

	/**
	 * The (special) name of the VM-built continuation resumption atom.
	 */
	private static final A_Atom vmResumeContinuationAtom =
		createSpecialMethodAtom(
			"vm resume_",
			P_ResumeContinuation.instance);

	/**
	 * Answer the (special) name of the VM-built continuation resumption atom.
	 *
	 * @return The name of the VM's continuation resumption atom.
	 */
	public static A_Atom vmResumeContinuationAtom ()
	{
		return vmResumeContinuationAtom;
	}

	/**
	 * The (special) name of the VM-built literal token creation atom.
	 */
	private static final A_Atom vmCreateLiteralTokenAtom =
		createSpecialMethodAtom(
			"vm create literal token_,_",
			P_CreateLiteralToken.instance);

	/**
	 * Answer the (special) name of the VM-built literal token creation atom.
	 *
	 * @return The name of the VM's literal token creation atom.
	 */
	public static A_Atom vmCreateLiteralTokenAtom ()
	{
		return vmCreateLiteralTokenAtom;
	}

	/**
	 * The (special) name of the VM-built literal phrase creation atom.
	 */
	private static final A_Atom vmCreateLiteralExpressionAtom =
		createSpecialMethodAtom(
			"vm create literal phrase_",
			P_CreateLiteralExpression.instance);

	/**
	 * Answer the (special) name of the VM-built literal phrase creation atom.
	 *
	 * @return The name of the VM's literal phrase creation atom.
	 */
	public static A_Atom vmCreateLiteralExpressionAtom ()
	{
		return vmCreateLiteralExpressionAtom;
	}
}
