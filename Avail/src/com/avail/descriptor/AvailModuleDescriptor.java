/**
 * descriptor/AvailModuleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.UnexpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

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
public class AvailModuleDescriptor extends Descriptor
{


	// accessing

	void ObjectAtAddMessageRestrictions (
			final AvailObject object, 
			final AvailObject methodName, 
			final AvailObject illegalArgMsgs)
	{
		assert (! object.restrictions().hasKey(methodName)) : "Don't declare multiple restrictions on same message separately in module.";
		object.restrictions(object.restrictions().mapAtPuttingCanDestroy(
			methodName,
			illegalArgMsgs,
			true));
	}

	void ObjectAtAddMethodImplementation (
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

	void ObjectAtNameAdd (
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

	void ObjectAtNewNamePut (
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

	void ObjectAtPrivateNameAdd (
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

	boolean ObjectNameVisible (
			final AvailObject object, 
			final AvailObject trueName)
	{
		//  Check if the given trueName is visible in this module.

		return object.visibleNames().hasElement(trueName);
	}

	void ObjectResolvedForwardWithName (
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

	AvailObject ObjectTrueNamesForStringName (
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
		if (! object.privateNames().hasKey(stringName))
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

	void ObjectConstantBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-36, value);
	}

	void ObjectFilteredBundleTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-40, value);
	}

	void ObjectMethods (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-24, value);
	}

	void ObjectName (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-12, value);
	}

	void ObjectNewNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectPrivateNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-16, value);
	}

	void ObjectRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-28, value);
	}

	void ObjectVariableBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-32, value);
	}

	void ObjectVisibleNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-20, value);
	}

	AvailObject ObjectConstantBindings (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-36);
	}

	AvailObject ObjectFilteredBundleTree (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-40);
	}

	AvailObject ObjectMethods (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-24);
	}

	AvailObject ObjectName (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	AvailObject ObjectNames (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-12);
	}

	AvailObject ObjectNewNames (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	AvailObject ObjectPrivateNames (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-16);
	}

	AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-28);
	}

	AvailObject ObjectVariableBindings (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-32);
	}

	AvailObject ObjectVisibleNames (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-20);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == -8))
		{
			return true;
		}
		if ((index == -12))
		{
			return true;
		}
		if ((index == -16))
		{
			return true;
		}
		if ((index == -20))
		{
			return true;
		}
		if ((index == -24))
		{
			return true;
		}
		if ((index == -28))
		{
			return true;
		}
		if ((index == -32))
		{
			return true;
		}
		if ((index == -36))
		{
			return true;
		}
		if ((index == -40))
		{
			return true;
		}
		return false;
	}



	// initialization

	void ObjectBuildFilteredBundleTreeFrom (
			final AvailObject object, 
			final AvailObject bundleTree)
	{
		//  Construct a bundle tree that has been prefiltered to contain just method bundles
		//  that are visible to the current module.

		object.filteredBundleTree(UnexpandedMessageBundleTreeDescriptor.newDepth(1));
		bundleTree.copyToRestrictedTo(object.filteredBundleTree(), object.visibleNames());
	}

	void ObjectCleanUpAfterCompile (
			final AvailObject object)
	{
		object.variableBindings(VoidDescriptor.voidObject());
		object.constantBindings(VoidDescriptor.voidObject());
		object.filteredBundleTree(VoidDescriptor.voidObject());
	}

	void ObjectClearModule (
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

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Compare by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		return ((object.name().hash() * 13) & HashMask);
	}



	// removing

	void ObjectRemoveFrom (
			final AvailObject object, 
			final AvailInterpreter anInterpreter)
	{

		final List<AvailObject> restrictionKeys = object.restrictions().keysAsArray();
		for (int i = 1, _end1 = restrictionKeys.size(); i <= _end1; i++)
		{
			final AvailObject messageName = restrictionKeys.get((i - 1));
			anInterpreter.atRemoveRestrictions(messageName, object.restrictions().mapAt(messageName));
		}
		final List<AvailObject> methodKeys = object.methods().keysAsArray();
		for (int i = 1, _end2 = methodKeys.size(); i <= _end2; i++)
		{
			final AvailObject methodName = methodKeys.get((i - 1));
			final AvailObject methodsTuple = object.methods().mapAt(methodName).asTuple();
			for (int k = 1, _end3 = methodsTuple.tupleSize(); k <= _end3; k++)
			{
				anInterpreter.removeMethodNamedImplementation(methodName, methodsTuple.tupleAt(k));
			}
		}
	}





	/* Object creation */

	public static AvailObject newModule ()
	{
		AvailObject emptyMap = MapDescriptor.empty();
		AvailObject unexpanded = UnexpandedMessageBundleTreeDescriptor.newDepth(1);
		AvailObject object = AvailObject.newIndexedDescriptor(0, AvailModuleDescriptor.mutableDescriptor());
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


	/* Descriptor lookup */
	public static AvailModuleDescriptor mutableDescriptor()
	{
		return (AvailModuleDescriptor) AllDescriptors [4];
	};
	public static AvailModuleDescriptor immutableDescriptor()
	{
		return (AvailModuleDescriptor) AllDescriptors [5];
	};

}
