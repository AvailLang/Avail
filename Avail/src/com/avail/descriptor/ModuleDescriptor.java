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
import com.avail.annotations.NotNull;
import com.avail.interpreter.Interpreter;

public class ModuleDescriptor extends Descriptor
{

	public enum ObjectSlots
	{
		NAME,
		NEW_NAMES,
		NAMES,
		PRIVATE_NAMES,
		VISIBLE_NAMES,
		METHODS,
		RESTRICTIONS,
		VARIABLE_BINDINGS,
		CONSTANT_BINDINGS,
		FILTERED_BUNDLE_TREE
	}


	// accessing

	@Override
	public void o_AtAddMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		assert !object.restrictions().hasKey(methodName) : "Don't declare multiple restrictions on same message separately in module.";
		object.restrictions(object.restrictions().mapAtPuttingCanDestroy(
			methodName,
			illegalArgMsgs,
			true));
	}

	@Override
	public void o_AtAddMethodImplementation (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation)
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
		object.methods(object.methods().mapAtPuttingCanDestroy(
			methodName,
			set,
			true));
	}

	@Override
	public void o_AtNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
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
		object.visibleNames(object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public void o_AtNewNamePut (
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
		object.newNames(object.newNames().mapAtPuttingCanDestroy(
			stringName,
			trueName,
			true));
		object.visibleNames(object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public void o_AtPrivateNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
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
		object.privateNames(object.privateNames().mapAtPuttingCanDestroy(
			stringName,
			set,
			true));
		object.visibleNames(object.visibleNames().setWithElementCanDestroy(trueName, true));
	}

	@Override
	public boolean o_NameVisible (
		final AvailObject object,
		final AvailObject trueName)
	{
		//  Check if the given trueName is visible in this module.

		return object.visibleNames().hasElement(trueName);
	}

	@Override
	public void o_ResolvedForwardWithName (
		final AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		//  The interpreter is in the process of resolving this forward declaration.  Record the
		//  fact that this implementation no longer needs to be cleaned up if the rest of the
		//  module compilation fails.
		//
		//  [forwardImplementation smalltalkDescriptor isKindOf: ForwardSignatureDescriptor] assert.

		assert object.methods().hasKey(methodName);
		AvailObject group = object.methods().mapAt(methodName);
		assert group.hasElement(forwardImplementation);
		group = group.setWithoutElementCanDestroy(forwardImplementation, true);
		object.methods(object.methods().mapAtPuttingCanDestroy(
			methodName,
			group,
			true));
	}

	@Override
	public AvailObject o_TrueNamesForStringName (
		final AvailObject object,
		final AvailObject stringName)
	{
		//  Check what true names are visible in this module under the given string name.

		assert stringName.isTuple();
		if (object.newNames().hasKey(stringName))
		{
			return SetDescriptor.empty().setWithElementCanDestroy(object.newNames().mapAt(stringName), false);
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
		if ((publics.setSize() == 0))
		{
			return privates;
		}
		return publics.setUnionCanDestroy(privates, false);
	}



	// GENERATED accessors

	/**
	 * Setter for field constantBindings.
	 */
	@Override
	public void o_ConstantBindings (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONSTANT_BINDINGS, value);
	}

	/**
	 * Setter for field filteredBundleTree.
	 */
	@Override
	public void o_FilteredBundleTree (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.FILTERED_BUNDLE_TREE, value);
	}

	/**
	 * Setter for field methods.
	 */
	@Override
	public void o_Methods (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.METHODS, value);
	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void o_Name (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	/**
	 * Setter for field names.
	 */
	@Override
	public void o_Names (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAMES, value);
	}

	/**
	 * Setter for field newNames.
	 */
	@Override
	public void o_NewNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NEW_NAMES, value);
	}

	/**
	 * Setter for field privateNames.
	 */
	@Override
	public void o_PrivateNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PRIVATE_NAMES, value);
	}

	/**
	 * Setter for field restrictions.
	 */
	@Override
	public void o_Restrictions (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RESTRICTIONS, value);
	}

	/**
	 * Setter for field variableBindings.
	 */
	@Override
	public void o_VariableBindings (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VARIABLE_BINDINGS, value);
	}

	/**
	 * Setter for field visibleNames.
	 */
	@Override
	public void o_VisibleNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VISIBLE_NAMES, value);
	}

	/**
	 * Getter for field constantBindings.
	 */
	@Override
	public AvailObject o_ConstantBindings (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONSTANT_BINDINGS);
	}

	/**
	 * Getter for field filteredBundleTree.
	 */
	@Override
	public AvailObject o_FilteredBundleTree (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.FILTERED_BUNDLE_TREE);
	}

	/**
	 * Getter for field methods.
	 */
	@Override
	public AvailObject o_Methods (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.METHODS);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject o_Name (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	/**
	 * Getter for field names.
	 */
	@Override
	public AvailObject o_Names (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAMES);
	}

	/**
	 * Getter for field newNames.
	 */
	@Override
	public AvailObject o_NewNames (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NEW_NAMES);
	}

	/**
	 * Getter for field privateNames.
	 */
	@Override
	public AvailObject o_PrivateNames (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PRIVATE_NAMES);
	}

	/**
	 * Getter for field restrictions.
	 */
	@Override
	public AvailObject o_Restrictions (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RESTRICTIONS);
	}

	/**
	 * Getter for field variableBindings.
	 */
	@Override
	public AvailObject o_VariableBindings (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VARIABLE_BINDINGS);
	}

	/**
	 * Getter for field visibleNames.
	 */
	@Override
	public AvailObject o_VisibleNames (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VISIBLE_NAMES);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final Enum<?> e)
	{
		if (e == ObjectSlots.NEW_NAMES)
		{
			return true;
		}
		if (e == ObjectSlots.NAMES)
		{
			return true;
		}
		if (e == ObjectSlots.PRIVATE_NAMES)
		{
			return true;
		}
		if (e == ObjectSlots.VISIBLE_NAMES)
		{
			return true;
		}
		if (e == ObjectSlots.METHODS)
		{
			return true;
		}
		if (e == ObjectSlots.RESTRICTIONS)
		{
			return true;
		}
		if (e == ObjectSlots.VARIABLE_BINDINGS)
		{
			return true;
		}
		if (e == ObjectSlots.CONSTANT_BINDINGS)
		{
			return true;
		}
		if (e == ObjectSlots.FILTERED_BUNDLE_TREE)
		{
			return true;
		}
		return false;
	}



	// initialization

	@Override
	public void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree)
	{
		//  Construct a bundle tree that has been prefiltered to contain just method bundles
		//  that are visible to the current module.

		object.filteredBundleTree(UnexpandedMessageBundleTreeDescriptor.newPc(1));
		bundleTree.copyToRestrictedTo(object.filteredBundleTree(), object.visibleNames());
	}

	@Override
	public void o_CleanUpAfterCompile (
		final AvailObject object)
	{
		object.variableBindings(VoidDescriptor.voidObject());
		object.constantBindings(VoidDescriptor.voidObject());
		object.filteredBundleTree(VoidDescriptor.voidObject());
	}

	@Override
	public void o_ClearModule (
		final AvailObject object)
	{
		object.newNames(MapDescriptor.empty());
		object.names(MapDescriptor.empty());
		object.privateNames(MapDescriptor.empty());
		object.visibleNames(SetDescriptor.empty());
		object.methods(MapDescriptor.empty());
		object.restrictions(MapDescriptor.empty());
		object.variableBindings(MapDescriptor.empty());
		object.constantBindings(MapDescriptor.empty());
		object.filteredBundleTree(UnexpandedMessageBundleTreeDescriptor.newPc(1));
	}



	// operations

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		//  Compare by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override
	public int o_Hash (
		final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		return (object.name().hash() * 13);
	}



	// removing

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





	/* Object creation */

	public static AvailObject newModule ()
	{
		AvailObject emptyMap = MapDescriptor.empty();
		AvailObject unexpanded = UnexpandedMessageBundleTreeDescriptor.newPc(1);
		AvailObject object = AvailObject.newIndexedDescriptor(0, ModuleDescriptor.mutableDescriptor());
		object.newNames(emptyMap);
		object.names(emptyMap);
		object.privateNames(emptyMap);
		object.visibleNames(SetDescriptor.empty());
		object.methods(emptyMap);
		object.restrictions(emptyMap);
		object.variableBindings(emptyMap);
		object.constantBindings(emptyMap);
		object.filteredBundleTree(unexpanded);
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
	private final static ModuleDescriptor mutableDescriptor = new ModuleDescriptor(true);

	/**
	 * Answer the mutable {@link ModuleDescriptor}.
	 *
	 * @return The mutable {@link ModuleDescriptor}.
	 */
	public static ModuleDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ModuleDescriptor}.
	 */
	private final static ModuleDescriptor immutableDescriptor = new ModuleDescriptor(false);

	/**
	 * Answer the immutable {@link ModuleDescriptor}.
	 *
	 * @return The immutable {@link ModuleDescriptor}.
	 */
	public static ModuleDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}

}
