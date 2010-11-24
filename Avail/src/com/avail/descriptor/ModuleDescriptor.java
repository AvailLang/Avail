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
import com.avail.interpreter.AvailInterpreter;

@ObjectSlots({
	"name",
	"newNames",
	"names",
	"privateNames",
	"visibleNames",
	"methods",
	"restrictions",
	"variableBindings",
	"constantBindings",
	"filteredBundleTree"
})
public class ModuleDescriptor extends Descriptor
{


	// accessing

	@Override
	public void ObjectAtAddMessageRestrictions (
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
	public void ObjectAtAddMethodImplementation (
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
	public void ObjectAtNameAdd (
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
	public void ObjectAtNewNamePut (
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
	public void ObjectAtPrivateNameAdd (
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
	public boolean ObjectNameVisible (
		final AvailObject object,
		final AvailObject trueName)
	{
		//  Check if the given trueName is visible in this module.

		return object.visibleNames().hasElement(trueName);
	}

	@Override
	public void ObjectResolvedForwardWithName (
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
	public AvailObject ObjectTrueNamesForStringName (
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
	public void ObjectConstantBindings (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-36, value);
	}

	/**
	 * Setter for field filteredBundleTree.
	 */
	@Override
	public void ObjectFilteredBundleTree (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-40, value);
	}

	/**
	 * Setter for field methods.
	 */
	@Override
	public void ObjectMethods (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-24, value);
	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void ObjectName (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field names.
	 */
	@Override
	public void ObjectNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Setter for field newNames.
	 */
	@Override
	public void ObjectNewNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field privateNames.
	 */
	@Override
	public void ObjectPrivateNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-16, value);
	}

	/**
	 * Setter for field restrictions.
	 */
	@Override
	public void ObjectRestrictions (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-28, value);
	}

	/**
	 * Setter for field variableBindings.
	 */
	@Override
	public void ObjectVariableBindings (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-32, value);
	}

	/**
	 * Setter for field visibleNames.
	 */
	@Override
	public void ObjectVisibleNames (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-20, value);
	}

	/**
	 * Getter for field constantBindings.
	 */
	@Override
	public AvailObject ObjectConstantBindings (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-36);
	}

	/**
	 * Getter for field filteredBundleTree.
	 */
	@Override
	public AvailObject ObjectFilteredBundleTree (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-40);
	}

	/**
	 * Getter for field methods.
	 */
	@Override
	public AvailObject ObjectMethods (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-24);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject ObjectName (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field names.
	 */
	@Override
	public AvailObject ObjectNames (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}

	/**
	 * Getter for field newNames.
	 */
	@Override
	public AvailObject ObjectNewNames (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field privateNames.
	 */
	@Override
	public AvailObject ObjectPrivateNames (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-16);
	}

	/**
	 * Getter for field restrictions.
	 */
	@Override
	public AvailObject ObjectRestrictions (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-28);
	}

	/**
	 * Getter for field variableBindings.
	 */
	@Override
	public AvailObject ObjectVariableBindings (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-32);
	}

	/**
	 * Getter for field visibleNames.
	 */
	@Override
	public AvailObject ObjectVisibleNames (
		final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-20);
	}



	// GENERATED special mutable slots

	@Override
	public boolean allowsImmutableToMutableReferenceAtByteIndex (
		final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -8)
		{
			return true;
		}
		if (index == -12)
		{
			return true;
		}
		if (index == -16)
		{
			return true;
		}
		if (index == -20)
		{
			return true;
		}
		if (index == -24)
		{
			return true;
		}
		if (index == -28)
		{
			return true;
		}
		if (index == -32)
		{
			return true;
		}
		if (index == -36)
		{
			return true;
		}
		if (index == -40)
		{
			return true;
		}
		return false;
	}



	// initialization

	@Override
	public void ObjectBuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree)
	{
		//  Construct a bundle tree that has been prefiltered to contain just method bundles
		//  that are visible to the current module.

		object.filteredBundleTree(UnexpandedMessageBundleTreeDescriptor.newDepth(1));
		bundleTree.copyToRestrictedTo(object.filteredBundleTree(), object.visibleNames());
	}

	@Override
	public void ObjectCleanUpAfterCompile (
		final AvailObject object)
	{
		object.variableBindings(VoidDescriptor.voidObject());
		object.constantBindings(VoidDescriptor.voidObject());
		object.filteredBundleTree(VoidDescriptor.voidObject());
	}

	@Override
	public void ObjectClearModule (
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
		object.filteredBundleTree(UnexpandedMessageBundleTreeDescriptor.newDepth(1));
	}



	// operations

	@Override
	public boolean ObjectEquals (
		final AvailObject object,
		final AvailObject another)
	{
		//  Compare by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override
	public int ObjectHash (
		final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		return (object.name().hash() * 13);
	}



	// removing

	@Override
	public void ObjectRemoveFrom (
		final @NotNull AvailObject object,
		final @NotNull AvailInterpreter anInterpreter)
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
		AvailObject unexpanded = UnexpandedMessageBundleTreeDescriptor.newDepth(1);
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
