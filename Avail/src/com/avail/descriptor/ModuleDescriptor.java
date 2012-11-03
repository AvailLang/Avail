/**
 * ModuleDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
	public enum IntegerSlots implements IntegerSlotsEnum
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
	public enum ObjectSlots implements ObjectSlotsEnum
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
		 * act as true names.  The true names are identity-based identifiers
		 * that prevent or at least clarify name conflicts.  This field holds
		 * only those names that are newly added by this module.
		 */
		NEW_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor strings} to {@linkplain AtomDescriptor atoms} which
		 * act as true names.  The true names are identity-based identifiers
		 * that prevent or at least clarify name conflicts.  This field holds
		 * only those names that have been imported from other modules.
		 */
		NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor strings} to {@linkplain AtomDescriptor atoms} which
		 * act as true names.  The true names are identity-based identifiers
		 * that prevent or at least clarify name conflicts.  This field holds
		 * only those names that are neither imported from another module nor
		 * exported from the current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain AtomDescriptor true
		 * names} that are visible within this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor
		 * atoms} to {@linkplain DefinitionDescriptor definitions} which
		 * implement (or forward or declare abstract or declare as a macro} that
		 * true name.
		 */
		METHODS,

		/**
		 * A {@linkplain MapDescriptor map} from a parent {@linkplain
		 * AtomDescriptor true names} to a {@linkplain TupleDescriptor tuple} of
		 * {@linkplain SetDescriptor sets} of child true names.  An argument of
		 * a {@linkplain SendNodeDescriptor send} of the parent true name must
		 * not be a send of a child name at the corresponding argument position
		 * in the tuple.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor string} to a {@linkplain VariableDescriptor
		 * variable}.  Since {@linkplain
		 * DeclarationNodeDescriptor.DeclarationKind#MODULE_VARIABLE module
		 * variables} are never accessible outside the module in which they are
		 * defined, this slot is overwritten with {@linkplain
		 * NullDescriptor#nullObject() the null object} when module compilation
		 * is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor string} to an {@linkplain AvailObject}.  Since a
		 * {@linkplain DeclarationNodeDescriptor.DeclarationKind#MODULE_CONSTANT
		 * module constants} are never accessible outside the module in which
		 * they are defined, this slot is overwritten with {@linkplain
		 * NullDescriptor#nullObject() the null object} when module compilation
		 * is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * The {@linkplain MessageBundleTreeDescriptor bundle tree} used to
		 * parse multimethod {@linkplain SendNodeDescriptor sends} while
		 * compiling this module.  When the module has been fully compiled, this
		 * slot is overwritten with {@linkplain NullDescriptor#nullObject() the
		 * null object}.
		 */
		FILTERED_BUNDLE_TREE,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain TupleDescriptor tuples} of {@linkplain
		 * MethodDescriptor.ObjectSlots#TYPE_RESTRICTIONS_TUPLE type
		 * restriction} {@linkplain FunctionDescriptor functions}.  At any call
		 * site for the given message name, any applicable functions are
		 * executed to determine if the input types are acceptable, and if so
		 * the expected return type is produced.  The actual expected return
		 * type for the call site is the intersection of types provided by
		 * applicable semantic restrictions and the types indicated by all
		 * applicable {@linkplain DefinitionDescriptor definitions}.
		 */
		TYPE_RESTRICTION_FUNCTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain TupleDescriptor tuples} of seal points.
		 */
		SEALS;
	}


	@Override @AvailMethod
	boolean o_IsSystemModule (final AvailObject object)
	{
		return object.slot(IS_SYSTEM_MODULE) != 0;
	}

	@Override @AvailMethod
	void o_IsSystemModule (
		final AvailObject object,
		final boolean isSystemModule)
	{
		object.setSlot(
			IS_SYSTEM_MODULE,
			isSystemModule ? 1 : 0);
	}

	@Override @AvailMethod
	int o_AllocateFromCounter (final AvailObject object)
	{
		final int value = object.slot(COUNTER);
		object.setSlot(COUNTER, value + 1);
		return value;
	}

	@Override @AvailMethod
	void o_AddGrammaticalMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		assert !object.grammaticalRestrictions().hasKey(methodName)
		: "Don't declare multiple restrictions on same message separately"
			+ " in module.";
		AvailObject grammaticalRestrictions = object.slot(
			GRAMMATICAL_RESTRICTIONS);
		grammaticalRestrictions =
			grammaticalRestrictions.mapAtPuttingCanDestroy(
				methodName,
				illegalArgMsgs,
				true);
		object.setSlot(
			GRAMMATICAL_RESTRICTIONS,
			grammaticalRestrictions);
	}

	@Override @AvailMethod
	void o_AddMethodDefinition (
		final AvailObject object,
		final AvailObject definition)
	{
		final AvailObject methodName = definition.definitionMethod().name();
		AvailObject methods = object.slot(METHODS);
		AvailObject set;
		if (methods.hasKey(methodName))
		{
			set = methods.mapAt(methodName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(definition, false);
		methods = methods.mapAtPuttingCanDestroy(
			methodName,
			set,
			true);
		object.setSlot(METHODS, methods);
	}

	@Override @AvailMethod
	void o_AddConstantBinding (
		final AvailObject object,
		final AvailObject name,
		final AvailObject constantBinding)
	{
		assert constantBinding.kind().isSubtypeOf(
			VariableTypeDescriptor.mostGeneralType());
		AvailObject constantBindings = object.slot(
			CONSTANT_BINDINGS);
		constantBindings = constantBindings.mapAtPuttingCanDestroy(
			name,
			constantBinding,
			true);
		object.setSlot(CONSTANT_BINDINGS, constantBindings);
	}

	@Override @AvailMethod
	void o_AddVariableBinding (
		final AvailObject object,
		final AvailObject name,
		final AvailObject variableBinding)
	{
		assert variableBinding.kind().isSubtypeOf(
			VariableTypeDescriptor.mostGeneralType());
		AvailObject variableBindings = object.slot(
			VARIABLE_BINDINGS);
		variableBindings = variableBindings.mapAtPuttingCanDestroy(
			name,
			variableBinding,
			true);
		object.setSlot(VARIABLE_BINDINGS, variableBindings);
	}

	@Override @AvailMethod
	void o_AtNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  Add the trueName to the current public scope.

		AvailObject names = object.slot(NAMES);
		AvailObject set;
		if (names.hasKey(stringName))
		{
			set = names.mapAt(stringName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(trueName, false);
		names = names.mapAtPuttingCanDestroy(
			stringName,
			set,
			true);
		object.setSlot(NAMES, names);
		AvailObject visibleNames = object.slot(VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.setSlot(VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	void o_AtNewNamePut (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  Set up this true name, which is local to the module.

		if (object.newNames().hasKey(stringName))
		{
			error("Can't define a new true name twice in a module", object);
			return;
		}
		AvailObject newNames = object.slot(NEW_NAMES);
		newNames = newNames.mapAtPuttingCanDestroy(stringName, trueName, true);
		object.setSlot(NEW_NAMES, newNames);
		AvailObject visibleNames = object.slot(VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.setSlot(VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	void o_AtPrivateNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  Add the trueName to the current private scope.

		AvailObject privateNames = object.slot(PRIVATE_NAMES);
		AvailObject set;
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
		object.setSlot(PRIVATE_NAMES, privateNames);
		AvailObject visibleNames = object.slot(VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.setSlot(VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	boolean o_NameVisible (
		final AvailObject object,
		final AvailObject trueName)
	{
		//  Check if the given trueName is visible in this module.

		return object.visibleNames().hasElement(trueName);
	}

	/**
	 * The interpreter is in the fiber of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param object
	 *        The module.
	 * @param forwardDeclaration
	 *        The {@linkplain ForwardDefinitionDescriptor forward declaration}
	 *        to be removed.
	 * @param methodName
	 *        The {@linkplain AtomDescriptor true name} of the
	 *        {@linkplain ForwardDefinitionDescriptor forward declaration}
	 *        being removed.
	 */
	@Override @AvailMethod
	void o_ResolvedForwardWithName (
		final AvailObject object,
		final AvailObject forwardDeclaration,
		final AvailObject methodName)
	{
		assert forwardDeclaration.isInstanceOfKind(FORWARD_DEFINITION.o());
		AvailObject methods = object.slot(METHODS);
		assert methods.hasKey(methodName);
		AvailObject group = methods.mapAt(methodName);
		assert group.hasElement(forwardDeclaration);
		group = group.setWithoutElementCanDestroy(forwardDeclaration, true);
		methods = methods.mapAtPuttingCanDestroy(
			methodName,
			group,
			true);
		object.setSlot(METHODS, methods);
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
	AvailObject o_TrueNamesForStringName (
		final AvailObject object,
		final AvailObject stringName)
	{
		assert stringName.isTuple();
		if (object.newNames().hasKey(stringName))
		{
			return SetDescriptor.empty().setWithElementCanDestroy(
				object.newNames().mapAt(stringName),
				false);
		}
		final AvailObject publics;
		if (object.names().hasKey(stringName))
		{
			publics = object.names().mapAt(stringName);
		}
		else
		{
			publics = SetDescriptor.empty();
		}
		if (!object.privateNames().hasKey(stringName))
		{
			return publics;
		}
		final AvailObject privates = object.privateNames().mapAt(stringName);
		if (publics.setSize() == 0)
		{
			return privates;
		}
		return publics.setUnionCanDestroy(privates, false);
	}

	@Override @AvailMethod
	AvailObject o_Name (
		final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	void o_Versions (
		final AvailObject object,
		final AvailObject value)
	{
		object.setSlot(VERSIONS, value);
	}

	@Override @AvailMethod
	AvailObject o_ConstantBindings (
		final AvailObject object)
	{
		return object.slot(CONSTANT_BINDINGS);
	}

	@Override @AvailMethod
	AvailObject o_FilteredBundleTree (
		final AvailObject object)
	{
		return object.slot(FILTERED_BUNDLE_TREE);
	}

	@Override @AvailMethod
	AvailObject o_Methods (
		final AvailObject object)
	{
		return object.slot(METHODS);
	}

	@Override @AvailMethod
	AvailObject o_Versions (
		final AvailObject object)
	{
		return object.slot(VERSIONS);
	}

	@Override @AvailMethod
	AvailObject o_Names (
		final AvailObject object)
	{
		return object.slot(NAMES);
	}

	@Override @AvailMethod
	AvailObject o_NewNames (
		final AvailObject object)
	{
		return object.slot(NEW_NAMES);
	}

	@Override @AvailMethod
	AvailObject o_PrivateNames (
		final AvailObject object)
	{
		return object.slot(PRIVATE_NAMES);
	}

	@Override @AvailMethod
	AvailObject o_GrammaticalRestrictions (
		final AvailObject object)
	{
		return object.slot(GRAMMATICAL_RESTRICTIONS);
	}

	@Override @AvailMethod
	AvailObject o_VariableBindings (
		final AvailObject object)
	{
		return object.slot(VARIABLE_BINDINGS);
	}

	@Override @AvailMethod
	AvailObject o_VisibleNames (
		final AvailObject object)
	{
		return object.slot(VISIBLE_NAMES);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == NEW_NAMES
			|| e == NAMES
			|| e == PRIVATE_NAMES
			|| e == VISIBLE_NAMES
			|| e == METHODS
			|| e == GRAMMATICAL_RESTRICTIONS
			|| e == VARIABLE_BINDINGS
			|| e == CONSTANT_BINDINGS
			|| e == FILTERED_BUNDLE_TREE
			|| e == TYPE_RESTRICTION_FUNCTIONS
			|| e == SEALS
			|| e == FLAGS_AND_COUNTER;
	}

	/**
	 * Construct a {@linkplain MessageBundleTreeDescriptor bundle tree} that has
	 * been filtered to contain just those {@linkplain MessageBundleDescriptor
	 * message bundles} that are visible in the current module.  Store the
	 * resulting bundle tree as this module's {@linkplain
	 * ObjectSlots#FILTERED_BUNDLE_TREE filtered bundle tree}.
	 *
	 * @param object The module.
	 * @param bundleTree
	 *        The root {@linkplain MessageBundleTreeDescriptor bundle tree} for
	 *        which to make a filtered copy.
	 */
	@Override @AvailMethod
	void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree)
	{
		final AvailObject filteredBundleTree =
			MessageBundleTreeDescriptor.newPc(1);
		object.setSlot(
			FILTERED_BUNDLE_TREE,
			filteredBundleTree);
		bundleTree.copyToRestrictedTo(
			filteredBundleTree,
			object.visibleNames());
	}

	@Override @AvailMethod
	void o_CleanUpAfterCompile (
		final AvailObject object)
	{
		object.setSlot(
			METHODS,
			NullDescriptor.nullObject());
		object.setSlot(
			GRAMMATICAL_RESTRICTIONS,
			NullDescriptor.nullObject());
		object.setSlot(
			VARIABLE_BINDINGS,
			NullDescriptor.nullObject());
		object.setSlot(
			CONSTANT_BINDINGS,
			NullDescriptor.nullObject());
		object.setSlot(
			FILTERED_BUNDLE_TREE,
			NullDescriptor.nullObject());
		object.setSlot(
			TYPE_RESTRICTION_FUNCTIONS,
			NullDescriptor.nullObject());
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		// Compare by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return MODULE.o();
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return object.name().hash() * 173 ^ 0xDF383F8C;
	}

	@Override @AvailMethod
	void o_RemoveFrom (
		final AvailObject object,
		final L2Interpreter anInterpreter)
	{
		for (final MapDescriptor.Entry entry : object.methods().mapIterable())
		{
			final AvailObject methodName = entry.key;
			for (final AvailObject definition : entry.value)
			{
				anInterpreter.removeDefinition(
					methodName,
					definition);
			}
		}
		final AvailObject typeRestrictions = object.slot(
			TYPE_RESTRICTION_FUNCTIONS);
		for (final MapDescriptor.Entry entry : typeRestrictions.mapIterable())
		{
			final AvailObject methodName = entry.key;
			for (final AvailObject restriction : entry.value)
			{
				anInterpreter.runtime().removeTypeRestriction(
					methodName,
					restriction);
			}
		}
		final AvailObject seals = object.slot(SEALS);
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

	@Override @AvailMethod
	void o_AddTypeRestriction (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	{
		AvailObject typeRestrictions = object.slot(
			TYPE_RESTRICTION_FUNCTIONS);
		AvailObject tuple;
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
		object.setSlot(TYPE_RESTRICTION_FUNCTIONS, typeRestrictions);
	}

	@Override @AvailMethod
	void o_AddSeal (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject sealSignature)
	{
		AvailObject seals = object.slot(SEALS);
		AvailObject tuple;
		if (seals.hasKey(methodName))
		{
			tuple = seals.mapAt(methodName);
		}
		else
		{
			tuple = TupleDescriptor.empty();
		}
		tuple = tuple.appendCanDestroy(sealSignature, true);
		seals = seals.mapAtPuttingCanDestroy(methodName, tuple, true);
		object.setSlot(SEALS, seals);
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("Module: ");
		builder.append(object.name().toString());
	}

	/**
	 * Construct a new empty {@linkplain ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        The {@linkplain StringDescriptor name} of the module.
	 * @return The new module.
	 */
	public static AvailObject newModule (
		final AvailObject moduleName)
	{
		final AvailObject emptyMap = MapDescriptor.empty();
		final AvailObject emptySet = SetDescriptor.empty();
		final AvailObject object = mutable().create();
		object.setSlot(NAME, moduleName);
		object.setSlot(VERSIONS, emptySet);
		object.setSlot(NEW_NAMES, emptyMap);
		object.setSlot(NAMES, emptyMap);
		object.setSlot(PRIVATE_NAMES, emptyMap);
		object.setSlot(VISIBLE_NAMES, emptySet);
		object.setSlot(METHODS, emptyMap);
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
		return object;
	}

	/**
	 * Construct a new {@link ModuleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ModuleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ModuleDescriptor}.
	 */
	private static final ModuleDescriptor mutable = new ModuleDescriptor(true);

	/**
	 * Answer the mutable {@link ModuleDescriptor}.
	 *
	 * @return The mutable {@link ModuleDescriptor}.
	 */
	public static ModuleDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ModuleDescriptor}.
	 */
	private static final ModuleDescriptor immutable =
		new ModuleDescriptor(false);

	/**
	 * Answer the immutable {@link ModuleDescriptor}.
	 *
	 * @return The immutable {@link ModuleDescriptor}.
	 */
	public static ModuleDescriptor immutable ()
	{
		return immutable;
	}
}
