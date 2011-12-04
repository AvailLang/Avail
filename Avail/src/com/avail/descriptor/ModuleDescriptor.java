/**
 * descriptor/AvailModuleDescriptor.java
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.FORWARD_SIGNATURE;
import com.avail.annotations.*;
import com.avail.interpreter.levelTwo.L2Interpreter;

/**
 * A [@linkplain {@linkplain ModuleDescriptor module} is the mechanism by which Avail
 * code is organized.  Modules are parsed from files with the extension ".avail"
 * which contain information about
 * <ul>
 * <li>the module's prerequisites,</li>
 * <li>the names to be exported from the module,</li>
 * <li>methods and macros defined in this module,</li>
 * <li>negative-precedence rules to help disambiguate complex expressions,</li>
 * <li>variables and constants private to the module</li>
 * </ul>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class ModuleDescriptor
extends Descriptor
{
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
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain SignatureDescriptor signatures} which implement
		 * (or forward or declare abstract or declare as a macro} that true
		 * name.
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
		 * StringDescriptor string} to a {@linkplain ContainerDescriptor
		 * container (variable)}.  Since {@linkplain
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
		 * ImplementationSetDescriptor.ObjectSlots#TYPE_RESTRICTIONS_TUPLE type
		 * restriction} {@linkplain FunctionDescriptor functions}.  At any call
		 * site for the given message name, any applicable functions are
		 * executed to determine if the input types are acceptable, and if so
		 * the expected return type is produced.  The actual expected return
		 * type for the call site is the intersection of types provided by
		 * applicable type restriction functions and the types indicated by the
		 * applicable {@linkplain SignatureDescriptor method signatures}.
		 */
		TYPE_RESTRICTION_FUNCTIONS
	}

	@Override @AvailMethod
	void o_AddGrammaticalMessageRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		assert !object.grammaticalRestrictions().hasKey(methodName)
		: "Don't declare multiple restrictions on same message separately"
			+ " in module.";
		AvailObject grammaticalRestrictions = object.objectSlot(
			ObjectSlots.GRAMMATICAL_RESTRICTIONS);
		grammaticalRestrictions =
			grammaticalRestrictions.mapAtPuttingCanDestroy(
				methodName,
				illegalArgMsgs,
				true);
		object.objectSlotPut(
			ObjectSlots.GRAMMATICAL_RESTRICTIONS,
			grammaticalRestrictions);
	}

	@Override @AvailMethod
	void o_AddMethodImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject implementation)
	{
		AvailObject set;
		if (object.methods().hasKey(methodName))
		{
			set = object.methods().mapAt(methodName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(implementation, true);
		AvailObject methods = object.objectSlot(ObjectSlots.METHODS);
		methods = methods.mapAtPuttingCanDestroy(
			methodName,
			set,
			true);
		object.objectSlotPut(ObjectSlots.METHODS, methods);
	}

	@Override @AvailMethod
	void o_AddConstantBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding)
	{
		AvailObject constantBindings = object.objectSlot(
			ObjectSlots.CONSTANT_BINDINGS);
		constantBindings = constantBindings.mapAtPuttingCanDestroy(
			name,
			constantBinding,
			true);
		object.objectSlotPut(ObjectSlots.CONSTANT_BINDINGS, constantBindings);
	}

	@Override @AvailMethod
	void o_AddVariableBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding)
	{
		AvailObject variableBindings = object.objectSlot(
			ObjectSlots.VARIABLE_BINDINGS);
		variableBindings = variableBindings.mapAtPuttingCanDestroy(
			name,
			variableBinding,
			true);
		object.objectSlotPut(ObjectSlots.VARIABLE_BINDINGS, variableBindings);
	}

	@Override @AvailMethod
	void o_AtNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		//  Add the trueName to the current public scope.

		AvailObject names = object.objectSlot(ObjectSlots.NAMES);
		AvailObject set;
		if (names.hasKey(stringName))
		{
			set = names.mapAt(stringName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(trueName, true);
		names = names.mapAtPuttingCanDestroy(
			stringName,
			set,
			true);
		object.objectSlotPut(ObjectSlots.NAMES, names);
		AvailObject visibleNames = object.objectSlot(ObjectSlots.VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		//  Set up this true name, which is local to the module.

		if (object.newNames().hasKey(stringName))
		{
			error("Can't define a new true name twice in a module", object);
			return;
		}
		AvailObject newNames = object.objectSlot(ObjectSlots.NEW_NAMES);
		newNames = newNames.mapAtPuttingCanDestroy(stringName, trueName, true);
		object.objectSlotPut(ObjectSlots.NEW_NAMES, newNames);
		AvailObject visibleNames = object.objectSlot(ObjectSlots.VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		//  Add the trueName to the current private scope.

		AvailObject privateNames = object.objectSlot(ObjectSlots.PRIVATE_NAMES);
		AvailObject set;
		if (privateNames.hasKey(stringName))
		{
			set = privateNames.mapAt(stringName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(trueName, true);
		privateNames = privateNames.mapAtPuttingCanDestroy(
			stringName,
			set,
			true);
		object.objectSlotPut(ObjectSlots.PRIVATE_NAMES, privateNames);
		AvailObject visibleNames = object.objectSlot(ObjectSlots.VISIBLE_NAMES);
		visibleNames = visibleNames.setWithElementCanDestroy(trueName, true);
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, visibleNames);
	}

	@Override @AvailMethod
	boolean o_NameVisible (
		final @NotNull AvailObject object,
		final @NotNull AvailObject trueName)
	{
		//  Check if the given trueName is visible in this module.

		return object.visibleNames().hasElement(trueName);
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this implementation no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param object
	 *        The module.
	 * @param forwardImplementation
	 *        The {@linkplain ForwardSignatureDescriptor forward declaration
	 *        signature} to be removed.
	 * @param methodName
	 *        The {@linkplain AtomDescriptor true name} of the
	 *        {@linkplain ForwardSignatureDescriptor forward declaration
	 *        signature} being removed.
	 */
	@Override @AvailMethod
	void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject forwardImplementation,
		final @NotNull AvailObject methodName)
	{
		assert forwardImplementation.isInstanceOfKind(FORWARD_SIGNATURE.o());
		AvailObject methods = object.objectSlot(ObjectSlots.METHODS);
		assert methods.hasKey(methodName);
		AvailObject group = methods.mapAt(methodName);
		assert group.hasElement(forwardImplementation);
		group = group.setWithoutElementCanDestroy(forwardImplementation, true);
		methods = methods.mapAtPuttingCanDestroy(
			methodName,
			group,
			true);
		object.objectSlotPut(ObjectSlots.METHODS, methods);
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName)
	{
		assert stringName.isTuple();
		if (object.newNames().hasKey(stringName))
		{
			return SetDescriptor.empty().setWithElementCanDestroy(
				object.newNames().mapAt(stringName),
				false);
		}
		AvailObject publics;
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
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override @AvailMethod
	void o_Versions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VERSIONS, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ConstantBindings (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONSTANT_BINDINGS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FilteredBundleTree (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FILTERED_BUNDLE_TREE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Methods (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.METHODS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Versions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VERSIONS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Names (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAMES);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_NewNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NEW_NAMES);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PrivateNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PRIVATE_NAMES);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_GrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.GRAMMATICAL_RESTRICTIONS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_VariableBindings (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VARIABLE_BINDINGS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_VisibleNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VISIBLE_NAMES);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.NEW_NAMES
			|| e == ObjectSlots.NAMES
			|| e == ObjectSlots.PRIVATE_NAMES
			|| e == ObjectSlots.VISIBLE_NAMES
			|| e == ObjectSlots.METHODS
			|| e == ObjectSlots.GRAMMATICAL_RESTRICTIONS
			|| e == ObjectSlots.VARIABLE_BINDINGS
			|| e == ObjectSlots.CONSTANT_BINDINGS
			|| e == ObjectSlots.FILTERED_BUNDLE_TREE
			|| e == ObjectSlots.TYPE_RESTRICTION_FUNCTIONS;
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundleTree)
	{
		final AvailObject filteredBundleTree =
			MessageBundleTreeDescriptor.newPc(1);
		object.objectSlotPut(
			ObjectSlots.FILTERED_BUNDLE_TREE,
			filteredBundleTree);
		bundleTree.copyToRestrictedTo(
			filteredBundleTree,
			object.visibleNames());
	}

	@Override @AvailMethod
	void o_CleanUpAfterCompile (
		final @NotNull AvailObject object)
	{
		object.objectSlotPut(
			ObjectSlots.METHODS,
			NullDescriptor.nullObject());
		object.objectSlotPut(
			ObjectSlots.GRAMMATICAL_RESTRICTIONS,
			NullDescriptor.nullObject());
		object.objectSlotPut(
			ObjectSlots.VARIABLE_BINDINGS,
			NullDescriptor.nullObject());
		object.objectSlotPut(
			ObjectSlots.CONSTANT_BINDINGS,
			NullDescriptor.nullObject());
		object.objectSlotPut(
			ObjectSlots.FILTERED_BUNDLE_TREE,
			NullDescriptor.nullObject());
		object.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTION_FUNCTIONS,
			NullDescriptor.nullObject());
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// Compare by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.name().hash() * 173 ^ 0xDF383F8C;
	}

	@Override @AvailMethod
	void o_RemoveFrom (
		final @NotNull AvailObject object,
		final @NotNull L2Interpreter anInterpreter)
	{
		for (final MapDescriptor.Entry entry : object.methods().mapIterable())
		{
			final AvailObject methodName = entry.key;
			for (final AvailObject implementation : entry.value)
			{
				anInterpreter.removeMethodNamedImplementation(
					methodName,
					implementation);
			}
		}
		final AvailObject typeRestrictions = object.objectSlot(
			ObjectSlots.TYPE_RESTRICTION_FUNCTIONS);
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
	}

	@Override @AvailMethod
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction)
	{
		AvailObject typeRestrictions = object.objectSlot(
			ObjectSlots.TYPE_RESTRICTION_FUNCTIONS);
		AvailObject tuple;
		if (typeRestrictions.hasKey(methodNameAtom))
		{
			tuple = typeRestrictions.mapAt(methodNameAtom);
		}
		else
		{
			tuple = TupleDescriptor.empty();
		}
		tuple = TupleDescriptor.append(tuple, typeRestrictionFunction);
		typeRestrictions = typeRestrictions.mapAtPuttingCanDestroy(
			methodNameAtom,
			tuple,
			true);
		object.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTION_FUNCTIONS,
			typeRestrictions);
	}

	/**
	 * Construct a new empty {@linkplain ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        The {@linkplain StringDescriptor name} of the module.
	 * @return The new module.
	 */
	public static @NotNull AvailObject newModule (
		final AvailObject moduleName)
	{
		final AvailObject emptyMap = MapDescriptor.empty();
		final AvailObject emptySet = SetDescriptor.empty();
		final AvailObject object = mutable().create();
		object.objectSlotPut(ObjectSlots.NAME, moduleName);
		object.objectSlotPut(ObjectSlots.VERSIONS, emptySet);
		object.objectSlotPut(ObjectSlots.NEW_NAMES, emptyMap);
		object.objectSlotPut(ObjectSlots.NAMES, emptyMap);
		object.objectSlotPut(ObjectSlots.PRIVATE_NAMES, emptyMap);
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, emptySet);
		object.objectSlotPut(ObjectSlots.METHODS, emptyMap);
		object.objectSlotPut(ObjectSlots.GRAMMATICAL_RESTRICTIONS, emptyMap);
		object.objectSlotPut(ObjectSlots.VARIABLE_BINDINGS, emptyMap);
		object.objectSlotPut(ObjectSlots.CONSTANT_BINDINGS, emptyMap);
		object.objectSlotPut(
			ObjectSlots.FILTERED_BUNDLE_TREE,
			MessageBundleTreeDescriptor.newPc(1));
		object.objectSlotPut(ObjectSlots.VARIABLE_BINDINGS, emptyMap);

		object.objectSlotPut(
			ObjectSlots.TYPE_RESTRICTION_FUNCTIONS,
			MapDescriptor.empty());
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
	private final static ModuleDescriptor mutable = new ModuleDescriptor(true);

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
	private final static ModuleDescriptor immutable =
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
