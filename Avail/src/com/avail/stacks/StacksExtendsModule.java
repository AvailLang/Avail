/**
 * StacksExtendsModule.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.util.HashMap;
import com.avail.descriptor.A_String;

/**
 * A grouping of all implementationGroups originating from the names section of this
 * module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksExtendsModule
{
	/**
	 * The name of this module.
	 */
	public final A_String moduleName;

	/**
	 * The a map of {@linkplain ImplementationGroup implementationGroups} keyed
	 * by the implementation name.
	 */
	private HashMap<String,ImplementationGroup> implementationGroups;

	/**
	 * @return the implementationGroups
	 */
	public HashMap<String,ImplementationGroup> implementations ()
	{
		return implementationGroups;
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the method implementation to add.
	 */
	public void addMethodImplementation (final A_String key,
		final MethodCommentImplementation implementation)
	{
		implementationGroups.get(key).addMethod(implementation);
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the semantic restriction implementation to
	 * add.
	 */
	public void addSemanticImplementation (final A_String key,
		final SemanticRestrictionCommentImplementation implementation)
	{
		implementationGroups.get(key).addSemanticRestriction(implementation);
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the grammatical restriction implementation to add.
	 */
	public void addGrammaticalImplementation (final String key,
		final GrammaticalRestrictionCommentImplementation implementation)
	{
		implementationGroups.get(key).addGrammaticalRestriction(implementation);
	}

	/**
	 * @param key The name of the class
	 * @param classImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a class that is added to implementationGroups.
	 */
	public void addClassImplementationGroup (final String key,
		final ImplementationGroup classImplementationGroup)
	{
		implementationGroups.put(key, classImplementationGroup);
	}

	/**
	 * @param key The name of the module global variable
	 * @param globalImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a module global variable that is added to
	 * 		implementationGroups.
	 */
	public void addGlobalImplementationGroup (final String key,
		final ImplementationGroup globalImplementationGroup)
	{
		implementationGroups.put(key, globalImplementationGroup);
	}

	/**
	 * Rename an implementation in this Module.
	 * @param key the key and original name of the implementation
	 * @param newName the new name to rename the implementation to
	 */
	public void renameImplementation (final String key,
		final String newName)
	{
		implementationGroups.put(newName, implementationGroups.get(key));
		implementationGroups.remove(key);
	}

	/**
	 * Construct a new {@link StacksExtendsModule}.
	 * @param moduleName The name of the module
	 *
	 */
	public StacksExtendsModule (final A_String moduleName)
	{
		this.moduleName = moduleName;
	}

	/**
	 * Construct a new {@link StacksExtendsModule}.
	 * @param moduleName The name of the module
	 * @param implementationGroups
	 * 		The a map of {@linkplain ImplementationGroup implementationGroups} keyed
	 * 		by the implementation name.
	 */
	public StacksExtendsModule (final A_String moduleName,
		final HashMap<String,ImplementationGroup> implementationGroups)
	{
		this.moduleName = moduleName;
		this.implementationGroups = implementationGroups;
	}
}
