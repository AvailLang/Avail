/**
 * ModuleDescriptor.java
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

import static com.avail.descriptor.ModuleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ModuleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.interpreter.levelTwo.L2Interpreter;

/**
 * A {@linkplain ModuleDescriptor module} is the mechanism by which Avail code
 * is organized.  Modules are parsed from files with the extension ".avail"
 * which contain information about:
 * <ul>
 * <li>the module's name,</li>
 * <li>the set of version strings for which this module claims to be
 *     compatible,</li>
 * <li>the module's prerequisites,</li>
 * <li>the names to be exported from the module,</li>
 * <li>methods and macros defined in this module,</li>
 * <li>negative-precedence rules to help disambiguate complex expressions,</li>
 * <li>variables and constants private to the module.</li>
 * </ul>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class ModuleDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A composite field containing a {@link #IS_SYSTEM_MODULE} flag
		 * indicating whether this is a system module, and a 31-bit {@link
		 * #COUNTER} for generating integers unique to this module.
		 */
		FLAGS_AND_COUNTER;

		/**
		 * A flag indicating if this is a system module.
		 */
		static BitField IS_SYSTEM_MODULE = bitField(
			FLAGS_AND_COUNTER,
			31,
			1);

		/**
		 * A counter for generating numbers unique to a module.
		 */
		static BitField COUNTER = bitField(
			FLAGS_AND_COUNTER,
			0,
			30);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain StringDescriptor string} that names the {@linkplain
		 * ModuleDescriptor module}.
		 */
		NAME,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * StringDescriptor versions} that this module alleges to support.
		 */
		VERSIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor strings} to {@linkplain AtomDescriptor atoms} which
		 * act as true names. The true names are identity-based identifiers
		 * that prevent or at least clarify name conflicts. This field holds
		 * only those names that are newly added by this module.
		 */
		NEW_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * strings} to {@linkplain AtomDescriptor atoms} which act as true
		 * names. The true names are identity-based identifiers that prevent or
		 * at least clarify name conflicts. This field holds only those names
		 * that have been imported from other modules.
		 */
		IMPORTED_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * strings} to {@linkplain AtomDescriptor atoms} which act as true
		 * names. The true names are identity-based identifiers that prevent or
		 * at least clarify name conflicts. This field holds only those names
		 * that are neither imported from another module nor exported from the
		 * current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain AtomDescriptor true
		 * names} that are visible within this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain DefinitionDescriptor
		 * definitions} which implement methods and macros (and forward
		 * declarations, abstract declarations, etc.).
		 */
		METHOD_DEFINITIONS_SET,

		/**
		 * A {@linkplain MapDescriptor map} from a parent {@linkplain
		 * AtomDescriptor true names} to a {@linkplain TupleDescriptor tuple} of
		 * {@linkplain SetDescriptor sets} of child true names. An argument of
		 * a {@linkplain SendNodeDescriptor send} of the parent true name must
		 * not be a send of a child name at the corresponding argument position
		 * in the tuple.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * string} to a {@linkplain VariableDescriptor variable}. Since
		 * {@linkplain DeclarationNodeDescriptor.DeclarationKind#MODULE_VARIABLE
		 * module variables} are never accessible outside the module in which
		 * they are defined, this slot is overwritten with {@linkplain
		 * NilDescriptor#nil() nil} when module compilation is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * string} to an {@linkplain AvailObject}. Since a {@linkplain
		 * DeclarationNodeDescriptor.DeclarationKind#MODULE_CONSTANT module
		 * constants} are never accessible outside the module in which they are
		 * defined, this slot is overwritten with {@linkplain
		 * NilDescriptor#nil() nil} when module compilation is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * The {@linkplain MessageBundleTreeDescriptor bundle tree} used to
		 * parse multimethod {@linkplain SendNodeDescriptor sends} while
		 * compiling this module. When the module has been fully compiled, this
		 * slot is overwritten with {@linkplain NilDescriptor#nil() nil}.
		 */
		FILTERED_BUNDLE_TREE,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain TupleDescriptor tuples} of {@linkplain
		 * MethodDescriptor.ObjectSlots#TYPE_RESTRICTIONS_TUPLE type
		 * restriction} {@linkplain FunctionDescriptor functions}. At any call
		 * site for the given message name, any applicable functions are
		 * executed to determine if the input types are acceptable, and if so
		 * the expected return type is produced. The actual expected return
		 * type for the call site is the intersection of types provided by
		 * applicable semantic restrictions and the types indicated by all
		 * applicable {@linkplain DefinitionDescriptor definitions}.
		 */
		TYPE_RESTRICTION_FUNCTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain TupleDescriptor tuples} of seal points.
		 */
		SEALS
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == NEW_NAMES
			|| e == IMPORTED_NAMES
			|| e == PRIVATE_NAMES
			|| e == VISIBLE_NAMES
			|| e == METHOD_DEFINITIONS_SET
			|| e == GRAMMATICAL_RESTRICTIONS
			|| e == VARIABLE_BINDINGS
			|| e == CONSTANT_BINDINGS
			|| e == FILTERED_BUNDLE_TREE
			|| e == TYPE_RESTRICTION_FUNCTIONS
			|| e == SEALS
			|| e == FLAGS_AND_COUNTER
			|| e == VERSIONS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("Module: ");
		builder.append(object.moduleName().toString());
	}

	@Override @AvailMethod
	boolean o_IsSystemModule (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(IS_SYSTEM_MODULE) != 0;
		}
	}

	@Override @AvailMethod
	void o_IsSystemModule (
		final AvailObject object,
		final boolean isSystemModule)
	{
		assert isShared();
		synchronized (object)
		{
			object.setSlot(
				IS_SYSTEM_MODULE,
				isSystemModule ? 1 : 0);
		}
	}

	@Override @AvailMethod
	A_String o_ModuleName (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	A_Set o_Versions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VERSIONS);
		}
	}

	@Override @AvailMethod
	void o_Versions (final AvailObject object, final A_BasicObject value)
	{
		synchronized (object)
		{
			object.setSlot(VERSIONS, value.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	A_Map o_NewNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(NEW_NAMES);
		}
	}

	@Override @AvailMethod
	A_Map o_ImportedNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(IMPORTED_NAMES);
		}
	}

	@Override @AvailMethod
	A_Map o_PrivateNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(PRIVATE_NAMES);
		}
	}

	@Override @AvailMethod
	A_Set o_VisibleNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VISIBLE_NAMES);
		}
	}

	@Override @AvailMethod
	A_Set o_MethodDefinitions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(METHOD_DEFINITIONS_SET);
		}
	}

//	@Override @AvailMethod
//	A_Tuple o_GrammaticalRestrictions (final AvailObject object)
//	{
//		synchronized (object)
//		{
//			return object.slot(GRAMMATICAL_RESTRICTIONS);
//		}
//	}

	@Override @AvailMethod
	A_Map o_VariableBindings (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VARIABLE_BINDINGS);
		}
	}

	@Override @AvailMethod
	A_Map o_ConstantBindings (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(CONSTANT_BINDINGS);
		}
	}

	@Override @AvailMethod
	A_BasicObject o_FilteredBundleTree (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(FILTERED_BUNDLE_TREE);
		}
	}

	@Override @AvailMethod
	void o_AddConstantBinding (
		final AvailObject object,
		final A_String name,
		final AvailObject constantBinding)
	{
		synchronized (object)
		{
			assert constantBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralType());
			A_Map constantBindings = object.slot(CONSTANT_BINDINGS);
			constantBindings = constantBindings.mapAtPuttingCanDestroy(
				name,
				constantBinding,
				true);
			object.setSlot(CONSTANT_BINDINGS, constantBindings.makeShared());
		}
	}


	@Override @AvailMethod
	void o_AddGrammaticalMessageRestrictions (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple exclusions)
	{
		synchronized (object)
		{
			A_Map grammaticalRestrictions =
				object.slot(GRAMMATICAL_RESTRICTIONS);
			A_Tuple fullExclusions;
			if (!grammaticalRestrictions.hasKey(methodName))
			{
				fullExclusions = exclusions;
			}
			else
			{
				fullExclusions = grammaticalRestrictions.mapAt(methodName);
				assert fullExclusions.descriptor().isShared();
				for (int i = fullExclusions.tupleSize(); i >= 1; i--)
				{
					final A_Set union =
						exclusions.tupleAt(i).setUnionCanDestroy(
							fullExclusions.tupleAt(i),
							false);
					fullExclusions = fullExclusions.tupleAtPuttingCanDestroy(
						i,
						union,
						true);
				}
			}
			grammaticalRestrictions =
				grammaticalRestrictions.mapAtPuttingCanDestroy(
					methodName,
					fullExclusions,
					true);
			object.setSlot(
				GRAMMATICAL_RESTRICTIONS,
				grammaticalRestrictions.makeShared());
		}
	}

	@Override @AvailMethod
	void o_ModuleAddDefinition (
		final AvailObject object,
		final A_BasicObject definition)
	{
		synchronized (object)
		{
			A_Set methods = object.slot(METHOD_DEFINITIONS_SET);
			methods = methods.setWithElementCanDestroy(definition, false);
			object.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes)
	{
		synchronized (object)
		{
			A_Map seals = object.slot(SEALS);
			A_Tuple tuple;
			if (seals.hasKey(methodName))
			{
				tuple = seals.mapAt(methodName);
			}
			else
			{
				tuple = TupleDescriptor.empty();
			}
			tuple = tuple.appendCanDestroy(argumentTypes, true);
			seals = seals.mapAtPuttingCanDestroy(methodName, tuple, true);
			object.setSlot(SEALS, seals.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddTypeRestriction (
		final AvailObject object,
		final A_Atom methodName,
		final A_Function typeRestrictionFunction)
	{
		synchronized (object)
		{
			A_Map typeRestrictions = object.slot(TYPE_RESTRICTION_FUNCTIONS);
			A_Tuple tuple;
			if (typeRestrictions.hasKey(methodName))
			{
				tuple = typeRestrictions.mapAt(methodName);
			}
			else
			{
				tuple = TupleDescriptor.empty();
			}
			tuple = tuple.appendCanDestroy(typeRestrictionFunction, true);
			typeRestrictions = typeRestrictions.mapAtPuttingCanDestroy(
				methodName,
				tuple,
				true);
			object.setSlot(
				TYPE_RESTRICTION_FUNCTIONS,
				typeRestrictions.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddVariableBinding (
		final AvailObject object,
		final A_String name,
		final AvailObject variableBinding)
	{
		synchronized (object)
		{
			assert variableBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralType());
			A_Map variableBindings = object.slot(VARIABLE_BINDINGS);
			variableBindings = variableBindings.mapAtPuttingCanDestroy(
				name,
				variableBinding,
				true);
			object.setSlot(VARIABLE_BINDINGS, variableBindings.makeShared());
		}
	}

	@Override @AvailMethod
	int o_AllocateFromCounter (final AvailObject object)
	{
		synchronized (object)
		{
			final int value = object.slot(COUNTER);
			object.setSlot(COUNTER, value + 1);
			return value;
		}
	}

	@Override @AvailMethod
	void o_AddImportedName (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		// Add the trueName to the current public scope.
		synchronized (object)
		{
			A_Map names = object.slot(IMPORTED_NAMES);
			A_Set set;
			if (names.hasKey(stringName))
			{
				set = names.mapAt(stringName);
			}
			else
			{
				set = SetDescriptor.empty();
			}
			set = set.setWithElementCanDestroy(trueName, false);
			names = names.mapAtPuttingCanDestroy(stringName, set, true);
			object.setSlot(IMPORTED_NAMES, names.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_IntroduceNewName (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		// Set up this true name, which is local to the module.
		synchronized (object)
		{
			if (object.newNames().hasKey(stringName))
			{
				error("Can't define a new true name twice in a module", object);
				return;
			}
			A_Map newNames = object.slot(NEW_NAMES);
			newNames = newNames.mapAtPuttingCanDestroy(
				stringName, trueName, true);
			object.setSlot(NEW_NAMES, newNames.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddPrivateName (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		// Add the trueName to the current private scope.
		synchronized (object)
		{
			A_Map privateNames = object.slot(PRIVATE_NAMES);
			A_Set set;
			if (privateNames.hasKey(stringName))
			{
				set = privateNames.mapAt(stringName);
			}
			else
			{
				set = SetDescriptor.empty();
			}
			set = set.setWithElementCanDestroy(trueName, false);
			privateNames = privateNames.mapAtPuttingCanDestroy(
				stringName,
				set,
				true);
			object.setSlot(PRIVATE_NAMES, privateNames.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_CleanUpAfterCompile (
		final AvailObject object)
	{
		synchronized (object)
		{
			object.setSlot(METHOD_DEFINITIONS_SET, NilDescriptor.nil());
			object.setSlot(GRAMMATICAL_RESTRICTIONS, NilDescriptor.nil());
			object.setSlot(VARIABLE_BINDINGS, NilDescriptor.nil());
			object.setSlot(CONSTANT_BINDINGS, NilDescriptor.nil());
			object.setSlot(FILTERED_BUNDLE_TREE, NilDescriptor.nil());
			object.setSlot(TYPE_RESTRICTION_FUNCTIONS, NilDescriptor.nil());
		}
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		// Compare by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(NAME).hash() * 173 ^ 0xDF383F8C;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MODULE.o();
	}

	@Override
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Modules are always shared, never immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override @AvailMethod
	boolean o_NameVisible (final AvailObject object, final AvailObject trueName)
	{
		// Check if the given trueName is visible in this module.
		return object.visibleNames().hasElement(trueName);
	}

	@Override @AvailMethod
	void o_RemoveFrom (
		final AvailObject object,
		final L2Interpreter anInterpreter)
	{
		synchronized (object)
		{
			for (final A_BasicObject definition : object.methodDefinitions())
			{
				anInterpreter.removeDefinition(definition);
			}
			final A_BasicObject typeRestrictions = object.slot(
				TYPE_RESTRICTION_FUNCTIONS);
			for (final MapDescriptor.Entry entry
				: typeRestrictions.mapIterable())
			{
				final AvailObject methodName = entry.key;
				for (final AvailObject restriction : entry.value)
				{
					anInterpreter.runtime().removeTypeRestriction(
						methodName,
						restriction);
				}
			}
			final A_BasicObject seals = object.slot(SEALS);
			for (final MapDescriptor.Entry entry : seals.mapIterable())
			{
				final AvailObject methodName = entry.key;
				for (final AvailObject seal : entry.value)
				{
					anInterpreter.runtime().removeSeal(
						methodName,
						seal);
				}
			}
		}
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param object
	 *        The module.
	 * @param forwardDeclaration
	 *        The {@linkplain ForwardDefinitionDescriptor forward declaration}
	 *        to be removed.
	 */
	@Override @AvailMethod
	void o_ResolveForward (
		final AvailObject object,
		final AvailObject forwardDeclaration)
	{
		synchronized (object)
		{
			assert forwardDeclaration.isInstanceOfKind(FORWARD_DEFINITION.o());
			A_Set methods = object.slot(METHOD_DEFINITIONS_SET);
			assert methods.hasElement(forwardDeclaration);
			methods = methods.setWithoutElementCanDestroy(
				forwardDeclaration,
				false);
			object.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared());
		}
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final A_BasicObject object)
	{
		return false;
	}

	/**
	 * Check what true names are visible in this module under the given string
	 * name.
	 *
	 * @param object The module.
	 * @param stringName
	 *        A string whose corresponding {@linkplain AtomDescriptor
	 *        true names} are to be looked up in this module.
	 * @return The {@linkplain SetDescriptor set} of {@linkplain
	 *         AtomDescriptor true names} that have the given stringName
	 *         and are visible in this module.
	 */
	@Override @AvailMethod
	A_Set o_TrueNamesForStringName (
		final AvailObject object,
		final A_String stringName)
	{
		synchronized (object)
		{
			assert stringName.isTuple();
			if (object.slot(NEW_NAMES).hasKey(stringName))
			{
				return SetDescriptor.empty().setWithElementCanDestroy(
					object.slot(NEW_NAMES).mapAt(stringName),
					false);
			}
			final A_Set publics;
			if (object.slot(IMPORTED_NAMES).hasKey(stringName))
			{
				publics = object.slot(IMPORTED_NAMES).mapAt(stringName);
			}
			else
			{
				publics = SetDescriptor.empty();
			}
			if (!object.slot(PRIVATE_NAMES).hasKey(stringName))
			{
				return publics;
			}
			final A_Set privates = object.slot(PRIVATE_NAMES).mapAt(stringName);
			if (publics.setSize() == 0)
			{
				return privates;
			}
			return publics.setUnionCanDestroy(privates, false);
		}
	}

	/**
	 * Populate my {@linkplain MessageBundleTreeDescriptor bundle tree} to have
	 * the {@linkplain MessageBundleDescriptor message bundles} that are visible
	 * in the current module.
	 *
	 * @param object The module.
	 * @param bundleMap
	 *        A {@linkplain MapDescriptor map} from bundle name ({@linkplain
	 *        AtomDescriptor atom}) to the corresponding message bundle.
	 */
	@Override @AvailMethod
	void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final A_Map bundleMap)
	{
		synchronized (object)
		{
			final A_BasicObject filteredBundleTree =
				object.slot(FILTERED_BUNDLE_TREE);
			for (final A_Atom visibleName : object.visibleNames())
			{
				if (bundleMap.hasKey(visibleName))
				{
					filteredBundleTree.addBundle(bundleMap.mapAt(visibleName));
				}
			}
		}
	}

	/**
	 * Construct a new empty {@linkplain ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        The {@linkplain StringDescriptor name} of the module.
	 * @return The new module.
	 */
	public static AvailObject newModule (final A_String moduleName)
	{
		final A_Map emptyMap = MapDescriptor.empty();
		final A_Set emptySet = SetDescriptor.empty();
		final AvailObject object = mutable.create();
		object.setSlot(NAME, moduleName);
		object.setSlot(VERSIONS, emptySet);
		object.setSlot(NEW_NAMES, emptyMap);
		object.setSlot(IMPORTED_NAMES, emptyMap);
		object.setSlot(PRIVATE_NAMES, emptyMap);
		object.setSlot(VISIBLE_NAMES, emptySet);
		object.setSlot(METHOD_DEFINITIONS_SET, emptySet);
		object.setSlot(GRAMMATICAL_RESTRICTIONS, emptyMap);
		object.setSlot(VARIABLE_BINDINGS, emptyMap);
		object.setSlot(CONSTANT_BINDINGS, emptyMap);
		object.setSlot(
			FILTERED_BUNDLE_TREE,
			MessageBundleTreeDescriptor.newPc(1));
		object.setSlot(VARIABLE_BINDINGS, emptyMap);
		object.setSlot(TYPE_RESTRICTION_FUNCTIONS, MapDescriptor.empty());
		object.setSlot(SEALS, MapDescriptor.empty());
		object.setSlot(COUNTER, 0);
		object.makeShared();
		return object;
	}

	/**
	 * Construct a new {@link ModuleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ModuleDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link ModuleDescriptor}. */
	private static final ModuleDescriptor mutable =
		new ModuleDescriptor(Mutability.MUTABLE);

	@Override
	ModuleDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ModuleDescriptor}. */
	private static final ModuleDescriptor shared =
		new ModuleDescriptor(Mutability.SHARED);

	@Override
	ModuleDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	ModuleDescriptor shared ()
	{
		return shared;
	}
}
