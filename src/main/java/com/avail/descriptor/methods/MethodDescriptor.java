/*
 * MethodDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.methods;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.Descriptor;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.atoms.AtomDescriptor;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.MessageBundleDescriptor;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.parsing.A_DefinitionParsingPlan;
import com.avail.descriptor.parsing.A_Lexer;
import com.avail.descriptor.pojos.RawPojoDescriptor;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TupleTypeDescriptor;
import com.avail.descriptor.types.TypeDescriptor;
import com.avail.descriptor.types.TypeTag;
import com.avail.dispatch.LookupTree;
import com.avail.dispatch.LookupTreeAdaptor;
import com.avail.dispatch.TypeComparison;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.primitive.atoms.P_AtomRemoveProperty;
import com.avail.interpreter.primitive.atoms.P_AtomSetProperty;
import com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckImportVersion;
import com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckModuleName;
import com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckModuleVersion;
import com.avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPseudoMacro;
import com.avail.interpreter.primitive.continuations.P_ContinuationCaller;
import com.avail.interpreter.primitive.controlflow.P_InvokeWithTuple;
import com.avail.interpreter.primitive.controlflow.P_ResumeContinuation;
import com.avail.interpreter.primitive.general.P_EmergencyExit;
import com.avail.interpreter.primitive.hooks.P_DeclareStringificationAtom;
import com.avail.interpreter.primitive.hooks.P_GetRaiseJavaExceptionInAvailFunction;
import com.avail.interpreter.primitive.methods.*;
import com.avail.interpreter.primitive.modules.P_AddUnloadFunction;
import com.avail.interpreter.primitive.modules.P_DeclareAllExportedAtoms;
import com.avail.interpreter.primitive.modules.P_PrivateCreateModuleVariable;
import com.avail.interpreter.primitive.objects.P_RecordNewTypeName;
import com.avail.interpreter.primitive.phrases.P_CreateLiteralExpression;
import com.avail.interpreter.primitive.phrases.P_CreateLiteralToken;
import com.avail.interpreter.primitive.variables.P_AtomicAddToMap;
import com.avail.interpreter.primitive.variables.P_GetValue;
import com.avail.optimizer.L2Generator;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Locks.Auto;
import com.avail.utility.json.JSONWriter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.avail.AvailRuntimeSupport.nextHash;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.atoms.AtomDescriptor.createSpecialAtom;
import static com.avail.descriptor.functions.CompiledCodeDescriptor.newPrimitiveRawFunction;
import static com.avail.descriptor.functions.FunctionDescriptor.createFunction;
import static com.avail.descriptor.methods.MacroDefinitionDescriptor.newMacroDefinition;
import static com.avail.descriptor.methods.MethodDefinitionDescriptor.newMethodDefinition;
import static com.avail.descriptor.methods.MethodDescriptor.CreateMethodOrMacroEnum.CREATE_MACRO;
import static com.avail.descriptor.methods.MethodDescriptor.CreateMethodOrMacroEnum.CREATE_METHOD;
import static com.avail.descriptor.methods.MethodDescriptor.IntegerSlots.HASH;
import static com.avail.descriptor.methods.MethodDescriptor.IntegerSlots.NUM_ARGS;
import static com.avail.descriptor.methods.MethodDescriptor.ObjectSlots.*;
import static com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.newParsingPlan;
import static com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.sets.SetDescriptor.emptySet;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.tuples.TupleDescriptor.tupleWithout;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.types.TypeDescriptor.Types.METHOD;
import static com.avail.dispatch.TypeComparison.compareForDispatch;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.anyRestriction;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static com.avail.utility.Locks.auto;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

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
	public enum IntegerSlots implements IntegerSlotsEnumJava
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
	public enum ObjectSlots implements ObjectSlotsEnumJava
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
		 * NilDescriptor#nil nil} indicates the tree has not yet been
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
		 * <p>When the {@link L2Generator} inlines a {@link Primitive} method
		 * definition, it asks the primitive what type it guarantees ({@link
		 * Primitive#returnTypeGuaranteedByVM(A_RawFunction, List)}) to return
		 * for the specific provided argument types.  If that return type is
		 * sufficiently strong, the above runtime check may be waived.</p>
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
		 * chunks.  This field holds the {@linkplain NilDescriptor#nil nil}
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
		 * supplied argument types.  A {@linkplain NilDescriptor#nil nil}
		 * indicates the tree has not yet been constructed.
		 */
		MACRO_TESTING_TREE,

		/**
		 * The method's {@linkplain A_Lexer lexer} or {@link NilDescriptor#nil
		 * nil}.
		 */
		LEXER_OR_NIL;
	}

	/** An indication of whether a method or a macro is being created. */
	public enum CreateMethodOrMacroEnum
	{
		/** Indicates a method is being created. */
		CREATE_METHOD,

		/** Indicates a macro is being created. */
		CREATE_MACRO;
	}

	/**
	 * A {@link LookupTreeAdaptor} used for building and navigating the {@link
	 * LookupTree}s that implement runtime dispatching.  Also used for looking
	 * up macros.
	 *
	 * @see ObjectSlots#PRIVATE_TESTING_TREE
	 * @see ObjectSlots#MACRO_TESTING_TREE
	 */
	public static final LookupTreeAdaptor<A_Definition, A_Tuple, Boolean>
		runtimeDispatcher = new LookupTreeAdaptor<
			A_Definition, A_Tuple, Boolean>()
	{
		@Override
		public A_Type extractSignature (final A_Definition element)
		{
			return element.bodySignature().argsTupleType();
		}

		@Override
		public A_Tuple constructResult (
			final List<? extends A_Definition> elements,
			final Boolean ignored)
		{
			return tupleFromList(elements);
		}

		@Override
		public TypeComparison compareTypes (
			final List<TypeRestriction> argumentRestrictions,
			final A_Type signatureType)
		{
			return compareForDispatch(
				argumentRestrictions, signatureType);
		}

		@Override
		public boolean getTestsArgumentPositions()
		{
			return true;
		}

		@Override
		public boolean getSubtypesHideSupertypes()
		{
			return true;
		}
	};

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == OWNING_BUNDLES
			|| e == DEFINITIONS_TUPLE
			|| e == PRIVATE_TESTING_TREE
			|| e == SEMANTIC_RESTRICTIONS_SET
			|| e == SEALED_ARGUMENTS_TYPES_TUPLE
			|| e == MACRO_DEFINITIONS_TUPLE
			|| e == MACRO_TESTING_TREE
			|| e == LEXER_OR_NIL;
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
		aStream.append(size);
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

	@Override @AvailMethod
	protected void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		// Record the fact that the given chunk depends on this object not
		// changing.  Local synchronization is sufficient, since invalidation
		// can't happen while L2 code is running (and therefore when the
		// L2Generator could be calling this).
		synchronized (object)
		{
			final A_BasicObject pojo =
				object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
			final Set<L2Chunk> chunkSet = pojo.javaObjectNotNull();
			chunkSet.add(chunk);
		}
	}

	@Override @AvailMethod
	protected void o_AddSealedArgumentsType (
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
	protected void o_AddSemanticRestriction (
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
	protected A_Set o_Bundles (final AvailObject object)
	{
		return object.slot(OWNING_BUNDLES);
	}

	@Override
	protected A_Bundle o_ChooseBundle (
		final AvailObject object,
		final A_Module currentModule)
	{
		final A_Set visibleModules = currentModule.allAncestors();
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
	 * Look up all method definitions that could match arguments satisfying the
	 * given {@link TypeRestriction}s.  This should return the definitions that
	 * could be invoked at runtime at a call site with the given restrictions.
	 * This set is subject to change as new methods and types are created.  If a
	 * restriction and the corresponding argument type of a definition have no
	 * possible intersection except bottom, then disallow the definition (it
	 * could never actually be invoked because bottom is uninstantiable).
	 * Answer a {@linkplain List list} of {@linkplain MethodDefinitionDescriptor
	 * method signatures}.
	 *
	 * <p>Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.</p>
	 */
	@Override @AvailMethod
	protected List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		final List<A_Definition> result = new ArrayList<>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final A_Tuple definitionsTuple = object.definitionsTuple();
		for (final A_Definition definition : definitionsTuple)
		{
			if (definition.bodySignature().couldEverBeInvokedWith(
				argRestrictions))
			{
				result.add(definition);
			}
		}
		return result;
	}

	@Override @AvailMethod
	protected A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(DEFINITIONS_TUPLE);
		}
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Look up all method definitions that could match the given argument
	 * types. Answer a {@linkplain List list} of {@linkplain
	 * MethodDefinitionDescriptor method signatures}.
	 */
	@Override @AvailMethod
	protected List<A_Definition> o_FilterByTypes (
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
	public int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	/**
	 * Test if the definition is present within this method.
	 */
	@Override @AvailMethod
	protected boolean o_IncludesDefinition (
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
	protected boolean o_IsMethodEmpty (final AvailObject object)
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
	protected A_Type o_Kind (final AvailObject object)
	{
		return METHOD.o();
	}

	@Override @AvailMethod
	protected A_Lexer o_Lexer (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(LEXER_OR_NIL);
		}
	}

	/**
	 * Look up the definition to invoke, given a tuple of argument types.
	 * Use the testingTree to find the definition to invoke.
	 */
	@Override @AvailMethod
	protected A_Definition o_LookupByTypesFromTuple (
		final AvailObject object,
		final A_Tuple argumentTypeTuple)
	throws MethodDefinitionException
	{
		final LookupTree<A_Definition, A_Tuple> tree =
			object.slot(PRIVATE_TESTING_TREE).javaObjectNotNull();
		final A_Tuple resultTuple =
			runtimeDispatcher.lookupByTypes(tree, argumentTypeTuple, TRUE);
		return MethodDefinitionException.extractUniqueMethod(resultTuple);
	}

	/**
	 * Look up the definition to invoke, given an array of argument values.
	 * Use the testingTree to find the definition to invoke (answer nil if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	protected A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		final LookupTree<A_Definition, A_Tuple> tree =
			object.slot(PRIVATE_TESTING_TREE).javaObjectNotNull();
		final A_Tuple results =
			runtimeDispatcher.lookupByValues(tree, argumentList, TRUE);
		return MethodDefinitionException.extractUniqueMethod(results);
	}

	/**
	 * Look up the macro definition to invoke, given an array of argument
	 * phrases.  Use the {@linkplain ObjectSlots#MACRO_TESTING_TREE macro
	 * testing tree} to find the macro definition to invoke.  Answer the tuple
	 * of applicable macro definitions, ideally just one if there is an
	 * unambiguous macro to invoke.
	 *
	 * <p>Note that this testing tree approach is only applicable if all of the
	 * macro definitions are visible (defined in the current module or an
	 * ancestor.  That should be the <em>vast</em> majority of the use of
	 * macros, but when it isn't, other lookup approaches are necessary.</p>
	 */
	@Override @AvailMethod
	protected A_Tuple o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple)
	{
		final LookupTree<A_Definition, A_Tuple> tree =
			object.slot(MACRO_TESTING_TREE).javaObjectNotNull();
		return runtimeDispatcher.lookupByValues(
			tree, argumentPhraseTuple, TRUE);
	}

	@Override @AvailMethod
	protected A_Tuple o_MacroDefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(MACRO_DEFINITIONS_TUPLE);
		}
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// A method is always shared. Never make it immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override @AvailMethod
	protected void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		A_Set bundles = object.slot(OWNING_BUNDLES);
		bundles = bundles.setWithElementCanDestroy(bundle, false);
		bundles.makeShared();
		object.setSlot(OWNING_BUNDLES, bundles);
	}

	@Override @AvailMethod
	protected void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Generator from these changes.
		//
		// Also create definition parsing plans for each bundle.  HOWEVER, note
		// that we don't update the current module's message bundle tree here,
		// and leave that to the caller to deal with.  Other modules' parsing
		// should be unaffected (although runtime execution may change).
		try (final Auto ignored = auto(L2Chunk.invalidationLock))
		{
			final A_Type bodySignature = definition.bodySignature();
			final A_Type paramTypes = bodySignature.argsTupleType();
			if (definition.isMacroDefinition())
			{
				// Install the macro.
				final A_Tuple oldTuple = object.slot(MACRO_DEFINITIONS_TUPLE);
				final A_Tuple newTuple =
					oldTuple.appendCanDestroy(definition, true);
				object.setSlot(MACRO_DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			else
			{
				final A_Tuple oldTuple = object.slot(DEFINITIONS_TUPLE);
				final A_Tuple seals = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
				for (final A_Tuple seal : seals)
				{
					final A_Type sealType =
						tupleTypeForSizesTypesDefaultType(
							singleInt(seal.tupleSize()),
							seal,
							bottom());
					if (paramTypes.isSubtypeOf(sealType))
					{
						throw new SignatureException(
							AvailErrorCode.E_METHOD_IS_SEALED);
					}
				}
				final A_Tuple newTuple =
					oldTuple.appendCanDestroy(definition, true);
				object.setSlot(DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			for (final A_Bundle bundle : object.slot(OWNING_BUNDLES))
			{
				final A_DefinitionParsingPlan plan =
					newParsingPlan(bundle, definition);
				bundle.addDefinitionParsingPlan(plan);
			}
			membershipChanged(object);
		}
	}

	@Override
	protected A_String o_MethodName (final AvailObject object)
	{
		return object.chooseBundle(object.module()).message().atomName();
	}

	@Override @AvailMethod
	protected int o_NumArgs (final AvailObject object)
	{
		return object.slot(NUM_ARGS);
	}

	/**
	 * Remove the definition from me. Causes dependent chunks to be
	 * invalidated.
	 */
	@Override @AvailMethod
	protected void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		assert !definition.definitionModule().equalsNil();
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Generator from these changes.
		L2Chunk.invalidationLock.lock();
		try
		{
			final ObjectSlotsEnum slot =
				!definition.isMacroDefinition()
					? DEFINITIONS_TUPLE
					: MACRO_DEFINITIONS_TUPLE;
			A_Tuple definitionsTuple = object.slot(slot);
			definitionsTuple = tupleWithout(definitionsTuple, definition);
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
	protected void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		final Set<L2Chunk> chunkSet = pojo.javaObjectNotNull();
		chunkSet.remove(chunk);
	}

	@Override @AvailMethod
	protected void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		synchronized (object)
		{
			final A_Tuple oldTuple = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Tuple newTuple = tupleWithout(oldTuple, typeTuple);
			assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	protected void o_RemoveSemanticRestriction (
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
	protected A_Tuple o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		return object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	protected A_Set o_SemanticRestrictions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(SEMANTIC_RESTRICTIONS_SET);
		}
	}

	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.METHOD;
	}

	@Override
	protected void o_SetLexer (
		final AvailObject object, final A_Lexer lexer)
	{
		synchronized (object)
		{
			object.setSlot(LEXER_OR_NIL, lexer);
		}
	}

	@Override
	protected LookupTree<A_Definition, A_Tuple> o_TestingTree (
		final AvailObject object)
	{
		return object.slot(PRIVATE_TESTING_TREE).javaObjectNotNull();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
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
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
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
	 * Answer a new {@code MethodDescriptor method}. It has no name yet, but
	 * will before it gets used in a send phrase.  It gets named by virtue of it
	 * being referenced by one or more {@linkplain MessageBundleDescriptor
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
	public static AvailObject newMethod (
		final int numArgs)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH, nextHash());
		result.setSlot(NUM_ARGS, numArgs);
		result.setSlot(OWNING_BUNDLES, emptySet());
		result.setSlot(DEFINITIONS_TUPLE, emptyTuple());
		result.setSlot(SEMANTIC_RESTRICTIONS_SET, emptySet());
		result.setSlot(
			SEALED_ARGUMENTS_TYPES_TUPLE, emptyTuple());
		result.setSlot(MACRO_DEFINITIONS_TUPLE, emptyTuple());
		final Set<L2Chunk> chunkSet =
			synchronizedSet(newSetFromMap(new WeakHashMap<>()));
		result.setSlot(
			DEPENDENT_CHUNKS_WEAK_SET_POJO,
			identityPojo(chunkSet).makeShared());
		final LookupTree<A_Definition, A_Tuple> definitionsTree =
			runtimeDispatcher.createRoot(
				emptyList(), nCopiesOfAnyRestriction(numArgs), TRUE);
		result.setSlot(
			PRIVATE_TESTING_TREE,
			identityPojo(definitionsTree).makeShared());
		final LookupTree<A_Definition, A_Tuple> macrosTree =
			runtimeDispatcher.createRoot(
				emptyList(),
				nCopies(
					numArgs,
					restrictionForType(PARSE_PHRASE.mostGeneralType(), BOXED)),
				TRUE);
		result.setSlot(
			MACRO_TESTING_TREE,
			identityPojo(macrosTree).makeShared());
		result.setSlot(LEXER_OR_NIL, nil);
		result.makeShared();
		return result;
	}

	/**
	 * The number of lists to cache of N occurrences of the {@link
	 * TypeRestriction} that restricts an element to the type {@code any}.
	 */
	private static final int sizeOfListsOfAny = 10;

	/**
	 * A list of lists of increasing size consisting only of {@link
	 * TypeRestriction}s to the type {@code any}.
	 */
	private static final List<List<TypeRestriction>> listsOfAny;

	static
	{
		listsOfAny = new ArrayList<>(sizeOfListsOfAny);
		for (int i = 0; i < sizeOfListsOfAny; i++)
		{
			listsOfAny.add(nCopies(i, anyRestriction));
		}
	}

	/**
	 * Return a list of n copies of the type any.  N is required to be ≥ 0.
	 *
	 * @param n The number of elements in the desired list, all the type any.
	 * @return The list.  Do not modify it, as it may be cached and reused.
	 */
	private static List<TypeRestriction> nCopiesOfAnyRestriction (final int n)
	{
		if (n < sizeOfListsOfAny)
		{
			return listsOfAny.get(n);
		}
		return nCopies(n,anyRestriction);
	}

	/**
	 * The membership of this {@code MethodDescriptor method} has changed.
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
		final Set<L2Chunk> originalSet = pojo.javaObjectNotNull();
		for (final L2Chunk chunk : new ArrayList<>(originalSet))
		{
			chunk.invalidate(invalidationsFromMethodChange);
		}
		// The chunk invalidations should have removed all dependencies.
		assert originalSet.isEmpty();

		// Rebuild the roots of the lookup trees.
		final int numArgs = object.slot(NUM_ARGS);
		final List<TypeRestriction> initialTypes =
			nCopiesOfAnyRestriction(numArgs);
		final LookupTree<A_Definition, A_Tuple> definitionsTree =
			runtimeDispatcher.createRoot(
				TupleDescriptor.toList(object.slot(DEFINITIONS_TUPLE)),
				initialTypes,
				TRUE);
		object.setSlot(
			PRIVATE_TESTING_TREE,
			identityPojo(definitionsTree).makeShared());
		final LookupTree<A_Definition, A_Tuple> macrosTree =
			runtimeDispatcher.createRoot(
				TupleDescriptor.toList(object.slot(MACRO_DEFINITIONS_TUPLE)),
				initialTypes,
				TRUE);
		object.setSlot(
			MACRO_TESTING_TREE,
			identityPojo(macrosTree).makeShared());
	}

	/**
	 * {@link Statistic} for tracking the cost of invalidating chunks due to a
	 * change in a dependency.
	 */
	private static final Statistic invalidationsFromMethodChange =
		new Statistic(
			"(invalidation from dependent method change)",
			StatisticReport.L2_OPTIMIZATION_TIME);

	/**
	 * Construct a new {@code MethodDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.METHOD_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link MethodDescriptor}. */
	private static final MethodDescriptor mutable =
		new MethodDescriptor(Mutability.MUTABLE);

	@Override
	public MethodDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MethodDescriptor}. */
	private static final MethodDescriptor shared =
		new MethodDescriptor(Mutability.SHARED);

	@Override
	public MethodDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	public MethodDescriptor shared ()
	{
		return shared;
	}

	/**
	 * {@code SpecialMethodAtom} enumerates {@linkplain A_Atom atoms} that are
	 * known to the virtual machine and that correspond to specific primitive
	 * invocations. Multiple {@linkplain Primitive primitives} may be provided
	 * to make the associated {@linkplain A_Method method} polymorphic.
	 */
	public enum SpecialMethodAtom
	{
		/** The special atom for failing during bootstrap.  Must be first. */
		CRASH(
			"vm crash:_",
			P_EmergencyExit.INSTANCE),

		/** The special atom for defining abstract methods. */
		ABSTRACT_DEFINER(
			"vm abstract_for_",
			P_AbstractMethodDeclarationForAtom.INSTANCE),

		/** The special atom for adding to a map inside a variable. */
		ADD_TO_MAP_VARIABLE(
			"vm_↑[_]:=_",
			P_AtomicAddToMap.INSTANCE),

		/** The special atom for adding a module unload function. */
		ADD_UNLOADER(
			"vm on unload_",
			P_AddUnloadFunction.INSTANCE),

		/** The special atom for creating aliases of atoms. */
		ALIAS(
			"vm alias new name_to_",
			P_Alias.INSTANCE),

		/** The special atom for function application. */
		APPLY(
			"vm function apply_(«_‡,»)",
			P_InvokeWithTuple.INSTANCE),

		/** The special atom for adding properties to atoms. */
		ATOM_PROPERTY(
			"vm atom_at property_put_",
			P_AtomSetProperty.INSTANCE),

		/** The special atom for removing properties from atoms. */
		ATOM_REMOVE_PROPERTY(
			"vm atom_remove property_",
			P_AtomRemoveProperty.INSTANCE),

		/** The special atom for extracting the caller of a continuation. */
		CONTINUATION_CALLER(
			"vm_'s caller",
			P_ContinuationCaller.INSTANCE),

		/** The special atom for creating a literal phrase. */
		CREATE_LITERAL_PHRASE(
			"vm create literal phrase_",
			P_CreateLiteralExpression.INSTANCE),

		/** The special atom for creating a literal token. */
		CREATE_LITERAL_TOKEN(
			"vm create literal token_,_,_,_",
			P_CreateLiteralToken.INSTANCE),

		/** The special atom for declaring the stringifier atom. */
		DECLARE_STRINGIFIER(
			"vm stringifier:=_",
			P_DeclareStringificationAtom.INSTANCE),

		/** The special atom for forward-defining methods. */
		FORWARD_DEFINER(
			"vm forward_for_",
			P_ForwardMethodDeclarationForAtom.INSTANCE),

		/** The special atom for getting a variable's value. */
		GET_VARIABLE(
			"vm↓_",
			P_GetValue.INSTANCE),

		/** The special atom for adding grammatical restrictions. */
		GRAMMATICAL_RESTRICTION(
			"vm grammatical restriction_is_",
			P_GrammaticalRestrictionFromAtoms.INSTANCE),

		/** The special atom for defining lexers. */
		LEXER_DEFINER(
			"vm lexer_filter is_body is_",
			P_SimpleLexerDefinitionForAtom.INSTANCE),

		/** The special atom for defining macros. */
		MACRO_DEFINER(
			"vm macro_is«_,»_",
			P_SimpleMacroDeclaration.INSTANCE,
			P_SimpleMacroDefinitionForAtom.INSTANCE),

		/** The special atom for defining methods. */
		METHOD_DEFINER(
			"vm method_is_",
			P_SimpleMethodDeclaration.INSTANCE,
			P_MethodDeclarationFromAtom.INSTANCE),

		/** The special atom for publishing atoms. */
		PUBLISH_ATOMS(
			"vm publish atom set_(public=_)",
			P_DeclareAllExportedAtoms.INSTANCE),

		/** The special atom for recording a type's name. */
		RECORD_TYPE_NAME(
			"vm record type_name_",
			P_RecordNewTypeName.INSTANCE),

		/** The special atom for creating a module variable/constant. */
		CREATE_MODULE_VARIABLE(
			"vm in module_create_with variable type_«constant»?«stably computed»?",
			P_PrivateCreateModuleVariable.INSTANCE),

		/** The special atom for sealing methods. */
		SEAL(
			"vm seal_at_",
			P_SealMethodByAtom.INSTANCE),

		/** The special atom for adding semantic restrictions. */
		SEMANTIC_RESTRICTION(
			"vm semantic restriction_is_",
			P_AddSemanticRestrictionForAtom.INSTANCE),

		/** The special atom for resuming a continuation. */
		RESUME_CONTINUATION(
			"vm resume_",
			P_ResumeContinuation.INSTANCE),

		/** The special atom for rethrowing a Java exception in Avail. */
		GET_RETHROW_JAVA_EXCEPTION(
			"vm get rethrow in Avail hook",
			P_GetRaiseJavaExceptionInAvailFunction.INSTANCE),

		/** The special atom for parsing module headers. */
		MODULE_HEADER(
			"Module…$§"
				+ "«Versions«…$§‡,»»"
				+ '«'
					+ "«Extends|Uses»!"
					+ '«'
						+ "…$"
						+ "«(«…$§‡,»)»"
						+ "«=(««-»?…$«→…$»?‡,»,⁇«`…»?)»"
						+ "‡,"
					+ '»'
				+ '»'
				+ "«Names«…$‡,»»"
				+ "«Entries«…$‡,»»"
				+ "«Pragma«…$‡,»»"
				+ "Body",
			asList(
				P_ModuleHeaderPrefixCheckModuleName.INSTANCE,
				P_ModuleHeaderPrefixCheckModuleVersion.INSTANCE,
				P_ModuleHeaderPrefixCheckImportVersion.INSTANCE),
			P_ModuleHeaderPseudoMacro.INSTANCE);

		/** The special atom. */
		public final A_Atom atom;

		/** The special atom's message bundle. */
		public final A_Bundle bundle;

		/**
		 * Define a macro.  Note that the variadic argument is for alternative
		 * overrides, whereas the explicit list is for prefix functions.  A
		 * variant of this constructor elides that (uncommon) list.
		 *
		 * @param name
		 *        The name of the method or macro being defined.
		 * @param prefixFunctions
		 *        A {@link List} of prefix functions to provide to the macro
		 *        definition, if this is a macro being defined (or always an
		 *        empty list for non-macros).  Note that if there are multiple
		 *        primitives provided in the variadic argument below, each will
		 *        use the same list of prefix functions.
		 * @param primitives
		 *        The primitive to wrap into a method or macro definition.  Note
		 *        that multiple overrides may be provided in this variadic
		 *        argument.
		 */
		SpecialMethodAtom (
			final String name,
			final List<Primitive> prefixFunctions,
			final Primitive... primitives)
		{
			this.atom = createSpecialMethodAtom(
				name, CREATE_MACRO, prefixFunctions, primitives);
			this.bundle = atom.bundleOrNil();
		}

		/**
		 * Define a method.  Note that another variant of this constructor
		 * includes a list of prefix functions, indicating a macro should be
		 * constructed.
		 *
		 * @param name
		 *        The name of the method or macro being defined.
		 * @param primitives
		 *        The primitive to wrap into a method or macro definition.  Note
		 *        that multiple overrides may be provided in this variadic
		 *        argument.
		 */
		SpecialMethodAtom (
			final String name,
			final Primitive... primitives)
		{
			this.atom = createSpecialMethodAtom(
				name, CREATE_METHOD, emptyList(), primitives);
			this.bundle = atom.bundleOrNil();
		}

		/**
		 * Create a new special atom with the given name.  The name is not
		 * globally unique, but serves to help to visually distinguish atoms.
		 * Also create a {@linkplain MessageBundleDescriptor message bundle}
		 * within it, with its own {@linkplain MethodDescriptor method}.  Add
		 * {@linkplain MethodDefinitionDescriptor method definitions}
		 * implementing the specified primitives.
		 *
		 * @param name
		 *        A string used to help identify the new atom.
		 * @param createMethodOrMacro
		 *        Whether to create method(s) or macro(s).
		 * @param prefixFunctions
		 *        A {@link List} of prefix functions to provide to the macro
		 *        definition, if this is a macro being defined.  Note that if
		 *        there are multiple primitives provided in the variadic
		 *        argument below, each will use the same list of prefix
		 *        functions.
		 * @param primitives
		 *        The {@link Primitive}s to instantiate as method definitions in
		 *        this atom's message bundle's method.
		 * @return
		 *        The new atom, not equal to any object in use before this
		 *        method was invoked.
		 */
		private static A_Atom createSpecialMethodAtom (
			final String name,
			final CreateMethodOrMacroEnum createMethodOrMacro,
			final List<Primitive> prefixFunctions,
			final Primitive... primitives)
		{
			final A_Atom atom = createSpecialAtom(name);
			final A_Bundle bundle;
			try
			{
				bundle = atom.bundleOrCreate();
			}
			catch (final MalformedMessageException e)
			{
				assert false : "This should not happen!";
				throw new RuntimeException(
					"VM method name is invalid: " + name, e);
			}
			final A_Method method = bundle.bundleMethod();
			for (final Primitive primitive : primitives)
			{
				final A_Function function =
					createFunction(
						newPrimitiveRawFunction(primitive, nil, 0),
						emptyTuple());
				final A_Definition definition;
				switch (createMethodOrMacro)
				{
					case CREATE_METHOD:
					{
						assert prefixFunctions.isEmpty();
						definition = newMethodDefinition(
							method,
							nil,  // System definitions have no module.
							function);
						break;
					}
					case CREATE_MACRO:
					{
						definition = newMacroDefinition(
							method,
							nil,  // System definitions have no module.
							function,
							tupleFromList(
								prefixFunctions.stream()
									.map(p -> createFunction(
										newPrimitiveRawFunction(p, nil, 0),
										emptyTuple()))
									.collect(toList())));
						break;
					}
					default:
					{
						throw new RuntimeException(
							"Unrecognized special method/macro enum");
					}
				}

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
	}
}
