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
import com.avail.annotations.NotNull;
import com.avail.compiler.node.*;
import com.avail.interpreter.Interpreter;

/**
 * A [@linkplain {@link ModuleDescriptor module} is the mechanism by which Avail
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
 * @author Mark van Gulik&lt;ghoul137@gmail.com&gt;
 */
public class ModuleDescriptor extends Descriptor
{

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * A {@link ByteStringDescriptor string} that names the {@linkplain
		 * ModuleDescriptor module}.
		 */
		NAME,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * ByteStringDescriptor strings} to {@link CyclicTypeDescriptor cyclic
		 * types} which act as true names.  The true names are identity-based
		 * identifiers that prevent or at least clarify name conflicts.  This
		 * field holds only those names that are newly added by this module.
		 */
		NEW_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * ByteStringDescriptor strings} to {@link CyclicTypeDescriptor cyclic
		 * types} which act as true names.  The true names are identity-based
		 * identifiers that prevent or at least clarify name conflicts.  This
		 * field holds only those names that have been imported from other
		 * modules.
		 */
		NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * ByteStringDescriptor strings} to {@link CyclicTypeDescriptor cyclic
		 * types} which act as true names.  The true names are identity-based
		 * identifiers that prevent or at least clarify name conflicts.  This
		 * field holds only those names that are neither imported from another
		 * module nor exported from the current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain CyclicTypeDescriptor
		 * true names} that are visible within this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * CyclicTypeDescriptor true names} to {@linkplain SignatureDescriptor
		 * signatures} which implement (or forward or declare abstract} that
		 * true name.
		 */
		METHODS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * CyclicTypeDescriptor true names} to {@linkplain
		 * MacroSignatureDescriptor macro signatures} which have that true name.
		 */
		MACROS,

		/**
		 * A {@linkplain MapDescriptor map} from a parent {@linkplain
		 * CyclicTypeDescriptor true names} to a {@linkplain
		 * TupleDescriptor tuple} of {@linkplain SetDescriptor sets} of child
		 * true names.  An argument of a {@linkplain SendNodeDescriptor send}
		 * of the parent true name must not be a send of a child name at the
		 * corresponding argument position in the tuple.
		 */
		RESTRICTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * ByteStringDescriptor string} to a {@linkplain ContainerDescriptor
		 * container (variable)}.  Since {@linkplain
		 * DeclarationNodeDescriptor.DeclarationKind#MODULE_VARIABLE module
		 * variables} are never accessible outside the module in which they are
		 * defined, this slot is overwritten with {@linkplain
		 * VoidDescriptor#voidObject() the void object} when module compilation
		 * is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * ByteStringDescriptor string} to an {@linkplain AvailObject}.  Since a
		 * {@linkplain DeclarationNodeDescriptor.DeclarationKind#MODULE_CONSTANT
		 * module constants} are never accessible outside the module in which
		 * they are defined, this slot is overwritten with {@linkplain
		 * VoidDescriptor#voidObject() the void object} when module compilation
		 * is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * The {@linkplain MessageBundleTreeDescriptor bundle tree} used to
		 * parse multimethod {@linkplain SendNodeDescriptor sends} while
		 * compiling this module.  When the module has been fully compiled, this
		 * slot is overwritten with {@linkplain VoidDescriptor#voidObject() the
		 * void object}.
		 */
		FILTERED_BUNDLE_TREE
	}

	@Override
	public void o_AtAddMessageRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		assert !object.restrictions().hasKey(methodName)
		: "Don't declare multiple restrictions on same message separately"
			+ " in module.";
		object.restrictions(
			object.restrictions().mapAtPuttingCanDestroy(
				methodName,
				illegalArgMsgs,
				true));
	}

	@Override
	public void o_AtAddMethodImplementation (
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
		object.methods(
			object.methods().mapAtPuttingCanDestroy(
				methodName,
				set,
				true));
	}

	@Override
	public void o_AtNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		//  Add the trueName to the current public scope.

		AvailObject set;
		if (object.names().hasKey(stringName))
		{
			set = object.names().mapAt(stringName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(trueName, true);
		object.names(object.names().mapAtPuttingCanDestroy(
			stringName,
			set,
			true));
		object.visibleNames(
			object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public void o_AtNewNamePut (
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
		object.newNames(object.newNames().mapAtPuttingCanDestroy(
			stringName,
			trueName,
			true));
		object.visibleNames(
			object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		//  Add the trueName to the current private scope.

		AvailObject set;
		if (object.privateNames().hasKey(stringName))
		{
			set = object.privateNames().mapAt(stringName);
		}
		else
		{
			set = SetDescriptor.empty();
		}
		set = set.setWithElementCanDestroy(trueName, true);
		object.privateNames(
			object.privateNames().mapAtPuttingCanDestroy(
				stringName,
				set,
				true));
		object.visibleNames(
			object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public boolean o_NameVisible (
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
	 *        The {@linkplain CyclicTypeDescriptor true name} of the {@linkplain
	 *        ForwardSignatureDescriptor forward declaration signature} being
	 *        removed.
	 */
	@Override
	public void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject forwardImplementation,
		final @NotNull AvailObject methodName)
	{
		assert forwardImplementation.isInstanceOfSubtypeOf(
			FORWARD_SIGNATURE.o());
		assert object.methods().hasKey(methodName);
		AvailObject group = object.methods().mapAt(methodName);
		assert group.hasElement(forwardImplementation);
		group = group.setWithoutElementCanDestroy(forwardImplementation, true);
		object.methods(
			object.methods().mapAtPuttingCanDestroy(
				methodName,
				group,
				true));
	}

	/**
	 * Check what true names are visible in this module under the given string
	 * name.
	 *
	 * @param object The module.
	 * @param stringName
	 *        A string whose corresponding {@linkplain CyclicTypeDescriptor true
	 *        names} are to be looked up in this module.
	 * @return The {@linkplain SetDescriptor set} of {@linkplain
	 *         CyclicTypeDescriptor true names} that have the given stringName
	 *         and are visible in this module.
	 */
	@Override
	public AvailObject o_TrueNamesForStringName (
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

	@Override
	public void o_ConstantBindings (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONSTANT_BINDINGS, value);
	}

	@Override
	public void o_FilteredBundleTree (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.FILTERED_BUNDLE_TREE, value);
	}

	@Override
	public void o_Methods (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.METHODS, value);
	}

	@Override
	public void o_Macros (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MACROS, value);
	}

	@Override
	public void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	@Override
	public void o_Names (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAMES, value);
	}

	@Override
	public void o_NewNames (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NEW_NAMES, value);
	}

	@Override
	public void o_PrivateNames (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PRIVATE_NAMES, value);
	}

	@Override
	public void o_Restrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RESTRICTIONS, value);
	}

	@Override
	public void o_VariableBindings (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VARIABLE_BINDINGS, value);
	}

	@Override
	public void o_VisibleNames (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, value);
	}

	@Override
	public @NotNull AvailObject o_ConstantBindings (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONSTANT_BINDINGS);
	}

	@Override
	public @NotNull AvailObject o_FilteredBundleTree (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FILTERED_BUNDLE_TREE);
	}

	@Override
	public @NotNull AvailObject o_Methods (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.METHODS);
	}

	@Override
	public @NotNull AvailObject o_Macros (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MACROS);
	}

	@Override
	public @NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override
	public @NotNull AvailObject o_Names (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAMES);
	}

	@Override
	public @NotNull AvailObject o_NewNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NEW_NAMES);
	}

	@Override
	public @NotNull AvailObject o_PrivateNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PRIVATE_NAMES);
	}

	@Override
	public @NotNull AvailObject o_Restrictions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RESTRICTIONS);
	}

	@Override
	public @NotNull AvailObject o_VariableBindings (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VARIABLE_BINDINGS);
	}

	@Override
	public @NotNull AvailObject o_VisibleNames (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VISIBLE_NAMES);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.NEW_NAMES
			|| e == ObjectSlots.NAMES
			|| e == ObjectSlots.PRIVATE_NAMES
			|| e == ObjectSlots.VISIBLE_NAMES
			|| e == ObjectSlots.METHODS
			|| e == ObjectSlots.MACROS
			|| e == ObjectSlots.RESTRICTIONS
			|| e == ObjectSlots.VARIABLE_BINDINGS
			|| e == ObjectSlots.CONSTANT_BINDINGS
			|| e == ObjectSlots.FILTERED_BUNDLE_TREE;
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
	@Override
	public void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundleTree)
	{
		object.filteredBundleTree(
			MessageBundleTreeDescriptor.newPc(1));
		bundleTree.copyToRestrictedTo(
			object.filteredBundleTree(), object.visibleNames());
	}

	@Override
	public void o_CleanUpAfterCompile (
		final @NotNull AvailObject object)
	{
		object.variableBindings(VoidDescriptor.voidObject());
		object.constantBindings(VoidDescriptor.voidObject());
		object.filteredBundleTree(VoidDescriptor.voidObject());
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// Compare by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.name().hash() * 173 ^ 0xDF383F8C;
	}

	@Override
	public void o_RemoveFrom (
		final @NotNull AvailObject object,
		final @NotNull Interpreter anInterpreter)
	{
		for (final AvailObject methodName : object.methods().keysAsArray())
		{
			for (final AvailObject implementation :
				object.methods().mapAt(methodName).asTuple())
			{
				anInterpreter.removeMethodNamedImplementation(
					methodName, implementation);
			}
		}
	}

	/**
	 * Construct a new empty {@linkplain ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        The {@linkplain ByteStringDescriptor name} of the module.
	 * @return The new module.
	 */
	public static @NotNull AvailObject newModule (
		final AvailObject moduleName)
	{
		AvailObject emptyMap = MapDescriptor.empty();
		AvailObject object = mutable().create();
		object.name(moduleName);
		object.newNames(emptyMap);
		object.names(emptyMap);
		object.privateNames(emptyMap);
		object.visibleNames(SetDescriptor.empty());
		object.methods(emptyMap);
		object.restrictions(emptyMap);
		object.variableBindings(emptyMap);
		object.constantBindings(emptyMap);
		object.filteredBundleTree(MessageBundleTreeDescriptor.newPc(1));
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
	private final static ModuleDescriptor immutable = new ModuleDescriptor(false);

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
