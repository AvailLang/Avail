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
import com.avail.descriptor.StringDescriptor;
import com.avail.utility.Pair;

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
	public final String moduleName;

	/**
	 * The map of {@linkplain ImplementationGroup ImplementationGroups} keyed
	 * by the implementation name as exported by this module.
	 */
	private final HashMap<A_String,ImplementationGroup> implementationGroups;

	/**
	 * @return the implementationGroups
	 */
	public HashMap<A_String,ImplementationGroup> implementations ()
	{
		return implementationGroups;
	}

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.
	 */
	private final HashMap<A_String,String> methodLeafNameToModuleName;

	/**
	 * @return the methodLeafNameToModuleName
	 */
	public HashMap<A_String,String> methodLeafNameToModuleName ()
	{
		return methodLeafNameToModuleName;
	}

	/**
	 * A map of module names to other {@linkplain StacksExtendsModule modules}
	 * extended by this module.
	 */
	private final HashMap<String,StacksExtendsModule> moduleNameToExtendsList;

	/**
	 * @return the moduleNameToExtendsList
	 */
	public HashMap<String,StacksExtendsModule> moduleNameToExtendsList ()
	{
		return moduleNameToExtendsList;
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the method implementation to add.
	 */
	public void addMethodImplementation (final A_String key,
		final MethodCommentImplementation implementation)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.get(key).addMethod(implementation);
		}
		else
		{
			getExtendsModuleForImplementationName(key)
				.addMethodImplementation(key, implementation);
		}
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
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.get(key)
				.addSemanticRestriction(implementation);
		}
		else
		{
			getExtendsModuleForImplementationName(key)
				.addSemanticImplementation(key, implementation);
		}
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the grammatical restriction implementation to add.
	 */
	public void addGrammaticalImplementation (final A_String key,
		final GrammaticalRestrictionCommentImplementation implementation)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.get(key)
				.addGrammaticalRestriction(implementation);
		}
		else
		{
			getExtendsModuleForImplementationName(key)
				.addGrammaticalImplementation(key,implementation);
		}
	}

	/**
	 * @param key The name of the class
	 * @param classImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a class that is added to implementationGroups.
	 */
	public void addClassImplementationGroup (final A_String key,
		final ImplementationGroup classImplementationGroup)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.put(key, classImplementationGroup);
		}
		else
		{
			getExtendsModuleForImplementationName(key)
				.addClassImplementationGroup(key, classImplementationGroup);
		}
	}

	/**
	 * @param key The name of the module global variable
	 * @param globalImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a module global variable that is added to
	 * 		implementationGroups.
	 */
	public void addGlobalImplementationGroup (final A_String key,
		final ImplementationGroup globalImplementationGroup)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.put(key, globalImplementationGroup);
		}
		else
		{
			getExtendsModuleForImplementationName(key)
				.addGlobalImplementationGroup(key, globalImplementationGroup);
		}
	}

	/**
	 * Rename an implementation in this Module.
	 * @param key the key and original name of the implementation
	 * @param newName the new name to rename the implementation to
	 */
	public void renameImplementation (final A_String key,
		final A_String newName)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.put(newName, implementationGroups.get(key));
			methodLeafNameToModuleName
				.put(newName, methodLeafNameToModuleName.get(key));
		}
		else
		{
			final StacksExtendsModule extendsModule =
				getExtendsModuleForImplementationName(key);

			if (extendsModule != null)
			{
				extendsModule.renameImplementation(key, newName);
			}
		}
	}

	/**
	 * @param key
	 * 		The implementation to remove
	 */
	public void removeImplementation (final A_String key)
	{
		if (implementationGroups.containsKey(key))
		{
			implementationGroups.remove(key);
			methodLeafNameToModuleName.remove(key);
		}
		else
		{
			final StacksExtendsModule extendsModule =
				getExtendsModuleForImplementationName(key);

			if (extendsModule != null)
			{
				extendsModule.removeImplementation(key);
			}
		}
	}

	/**
	 * Construct a new {@link StacksExtendsModule}.
	 * @param moduleImportName The name of the module
	 * @param implementationGroups
	 * 		The a map of {@linkplain ImplementationGroup implementationGroups}
	 * 		keyed by the implementation name.
	 * @param moduleNameToExtendsList
	 * 		A map of module names to other {@linkplain StacksExtendsModule
	 * 		modules} extended by this module.
	 * @param methodLeafNameToModuleName
	 * 		A map keyed by a method name with no path to the qualified module
	 * 		path it is originally named from.
	 */
	public StacksExtendsModule (final String moduleImportName,
		final HashMap<A_String,ImplementationGroup> implementationGroups,
		final HashMap<String,StacksExtendsModule> moduleNameToExtendsList,
		final HashMap<A_String,String> methodLeafNameToModuleName)
	{
		this.moduleName = moduleImportName;
		this.implementationGroups = implementationGroups;
		this.moduleNameToExtendsList = moduleNameToExtendsList;
		this.methodLeafNameToModuleName = methodLeafNameToModuleName;
	}



	/**
	 * @param name
	 * 		The implementation to search for
	 * @return
	 * 		The {@linkplain StacksExtendsModule} the implementation belongs to.
	 */
	public StacksExtendsModule getExtendsModuleForImplementationName(
		final A_String name)
	{
		if (implementationGroups.containsKey(name))
		{
			return this;
		}
		else if (hasImplementationInBranch(name))
		{
			final String owningModule = methodLeafNameToModuleName.get(name);
			if (moduleNameToExtendsList.containsKey(owningModule))
			{
				return moduleNameToExtendsList.get(owningModule);
			}

			for (final StacksExtendsModule extendsModule :
				moduleNameToExtendsList.values())
			{
				if (extendsModule.methodLeafNameToModuleName.containsKey(name))
				{
					return extendsModule
						.getExtendsModuleForImplementationName(name);
				}
			}
		}

		return null;
	}

	/**
	 * @param name
	 * 		The implementation to search for
	 * @return
	 * 		The boolean indicating whether or not the implementation is present
	 * 		in this branch.
	 */
	public boolean hasImplementationInBranch (final A_String name)
	{
		return methodLeafNameToModuleName.containsKey(name);
	}

	/**
	 * Create a new map from implementationGroups with new keys using the
	 * method qualified name
	 * @return
	 * 		A map with keyed by the method qualified name to the implementation.
	 */
	public Pair<HashMap<String,ImplementationGroup>,HashMap<String,String>>
		qualifiedImplementationNameToImplementation()
	{
			final HashMap<String,ImplementationGroup> newMap =
				new HashMap<String,ImplementationGroup>();

			final HashMap<A_String,Integer> newHashNameMap =
				new HashMap<A_String,Integer>();

			final HashMap<String,String> nameToLinkMap =
				new HashMap<String,String>();

			for (final A_String name : implementationGroups.keySet())
			{
				A_String nameToBeHashed = name;
				if (newHashNameMap.containsKey(name))
				{
					newHashNameMap.put(name, newHashNameMap.get(name) + 1);
					nameToBeHashed =
						StringDescriptor.from(name.asNativeString()
							+ newHashNameMap.get(name));
				}
				else
				{
					newHashNameMap.put(nameToBeHashed, 0);
				}
				final String qualifiedName = moduleName + "/"
					+ String.valueOf(nameToBeHashed.hash()) + ".html";
				newMap.put(qualifiedName, implementationGroups.get(name));
				nameToLinkMap.put(name.asNativeString(), qualifiedName);
			}
			return new Pair<HashMap<String, ImplementationGroup>,
				HashMap<String, String>>(newMap,nameToLinkMap);
	}

	/**
	 * Flatten out moduleNameToExtendsList map so that all modules in tree
	 * are in one flat map keyed by the qualified method name to the
	 * implementation.
	 * @return
	 * 		A map keyed by the qualified method name to the implementation.
	 */
	public Pair<HashMap<String,ImplementationGroup>,HashMap<String,String>>
		flattenImplementationGroups()
	{
		final HashMap<String,ImplementationGroup> newMap =
			new HashMap<String,ImplementationGroup>();

		final HashMap<String,String> nameToLinkMap =
			new HashMap<String,String>();

		for (final StacksExtendsModule extendsModule :
			moduleNameToExtendsList.values())
		{
			final Pair<HashMap<String, ImplementationGroup>,
				HashMap<String, String>> pair =
					extendsModule.flattenImplementationGroups();
			newMap.putAll(pair.first());
			nameToLinkMap.putAll(pair.second());
		}

		final Pair<HashMap<String, ImplementationGroup>,
			HashMap<String, String>> aPair =
				qualifiedImplementationNameToImplementation();
		newMap.putAll(aPair.first());
		nameToLinkMap.putAll(aPair.second());

		return new Pair<HashMap<String, ImplementationGroup>,
			HashMap<String, String>>(newMap,nameToLinkMap);
	}

	@Override
	public String toString()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("<h4>")
			.append(moduleName).append("</h4>")
			.append("<ol>");
		for (final A_String key : implementationGroups.keySet())
		{
			stringBuilder.append("<li>").append(key.asNativeString())
				.append("</li>");
		}
		stringBuilder.append("</ol>");

		for (final StacksExtendsModule extendsModule :
			moduleNameToExtendsList.values())
		{
			stringBuilder.append(extendsModule.toString());
		}

		return stringBuilder.toString();
	}
}
