/**
 * StacksImportModule.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being imported by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class StacksImportModule
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
	 * path it is originally named from.  These are all the extends method
	 * names.
	 */
	private final HashMap<A_String, HashMap<String, ImplementationGroup>>
		extendsMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<A_String, HashMap<String, ImplementationGroup>>
		extendsMethodLeafNameToModuleName ()
	{
		return extendsMethodLeafNameToModuleName;
	}

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  These are all the uses method
	 * names in scope in this module.
	 */
	private final HashMap<A_String, HashMap<String, ImplementationGroup>>
		usesMethodLeafNameToModuleName;

	/**
	 * @return the usesMethodLeafNameToModuleName
	 */
	public HashMap<A_String, HashMap<String, ImplementationGroup>>
		usesMethodLeafNameToModuleName ()
	{
		return usesMethodLeafNameToModuleName;
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
	 * A map of module names to other {@linkplain StacksUsesModule modules}
	 * used by this module.
	 */
	private final HashMap<String,StacksUsesModule> moduleNameToUsesList;

	/**
	 * @return the moduleNameToExtendsList
	 */
	public HashMap<String,StacksUsesModule> moduleNameToUsesList ()
	{
		return moduleNameToUsesList;
	}

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the method implementation to add.
	 */
	public abstract void addMethodImplementation (final A_String key,
		final MethodCommentImplementation implementation);

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the method implementation to add.
	 */
	public abstract void addMacroImplementation (final A_String key,
		final MacroCommentImplementation implementation);

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the semantic restriction implementation to
	 * add.
	 */
	public abstract void addSemanticImplementation (final A_String key,
		final SemanticRestrictionCommentImplementation implementation);

	/**
	 * @param key the name of the method used as a key to look up
	 * ImplementationGroup in the map.
	 * @param implementation the grammatical restriction implementation to add.
	 */
	public abstract void addGrammaticalImplementation (final A_String key,
		final GrammaticalRestrictionCommentImplementation implementation);

	/**
	 * @param key The name of the class
	 * @param classImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a class that is added to implementationGroups.
	 */
	public abstract void addClassImplementationGroup (final A_String key,
		final ImplementationGroup classImplementationGroup);

	/**
	 * @param key The name of the module global variable
	 * @param globalImplementationGroup The {@linkPlain ImplementationGroup}
	 * 		holding a module global variable that is added to
	 * 		implementationGroups.
	 */
	public abstract void addGlobalImplementationGroup (final A_String key,
		final ImplementationGroup globalImplementationGroup);

	/**
	 * Rename an implementation in this Module.
	 * @param key the key and original name of the implementation
	 * @param newName the new name to rename the implementation to
	 * @param newlyDefinedModule
	 * 		The {@linkplain StacksCommentsModule module} where the rename takes
	 * 		place
	 * @param newFileName The filename for this new group
	 * @param deleteOriginal
	 * 		Is the original name to be deleted?
	 */
	public abstract void renameImplementation (final A_String key,
		final A_String newName, final StacksCommentsModule newlyDefinedModule,
		final StacksFilename newFileName, boolean deleteOriginal);

	/**
	 * @param key
	 * 		The implementation to remove
	 */
	public void removeImplementation (final A_String key)
	{
		if (extendsMethodLeafNameToModuleName().containsKey(key))
		{
			extendsMethodLeafNameToModuleName().remove(key);
		}
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
		return extendsMethodLeafNameToModuleName.containsKey(name);
	}

	/**
	 * Create a new map from implementationGroups with new keys using the
	 * method qualified name
	 * @return
	 * 		A map with keyed by the method qualified name to the implementation.
	 */
	public Pair<HashMap<String,Pair<String,ImplementationGroup>>,
		HashMap<String,String>> qualifiedImplementationNameToImplementation()
	{
			final HashMap<String,Pair<String,ImplementationGroup>> newMap =
				new HashMap<>();

			final HashMap<A_String,Integer> newHashNameMap =
				new HashMap<>();

			final HashMap<String,String> nameToLinkMap =
				new HashMap<>();

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

				long hashedName = nameToBeHashed.hash();
				hashedName &= 0xFFFFFFFFL;

				final String qualifiedName = moduleName + "/"
					+ String.valueOf(hashedName) + ".json";
				newMap.put(qualifiedName,
					new Pair<>(
						name.asNativeString(), implementationGroups.get(name)));
				nameToLinkMap.put(name.asNativeString(), qualifiedName);
			}
			return new Pair<>(newMap, nameToLinkMap);
	}

	/**
	 * Flatten out moduleNameToExtendsList map so that all modules in tree
	 * are in one flat map keyed by the qualified method name to the
	 * implementation.  Pair this with a map of method name to html link.
	 * @return
	 * 		A pair with both maps.
	 */
	public Pair<HashMap<String,Pair<String,ImplementationGroup>>,
		HashMap<String,String>> flattenImplementationGroups()
	{
		final HashMap<String,Pair<String,ImplementationGroup>> newMap =
			new HashMap<>();

		final HashMap<String,String> nameToLinkMap =
			new HashMap<>();

		for (final StacksExtendsModule extendsModule :
			moduleNameToExtendsList.values())
		{
			final Pair<HashMap<String, Pair<String,ImplementationGroup>>,
				HashMap<String, String>> pair =
					extendsModule.flattenImplementationGroups();
			newMap.putAll(pair.first());
			nameToLinkMap.putAll(pair.second());
		}

		final Pair<HashMap<String,Pair<String,ImplementationGroup>>,
			HashMap<String,String>> aPair =
				qualifiedImplementationNameToImplementation();
		newMap.putAll(aPair.first());
		nameToLinkMap.putAll(aPair.second());

		return new Pair<>(newMap, nameToLinkMap);
	}

	/**
	 * Construct a new {@link StacksImportModule}.
	 *
	 * @param moduleImportName The name of the module
	 * @param implementationGroups
	 * 		The a map of {@linkplain ImplementationGroup implementationGroups}
	 * 		keyed by the implementation name.
	 * @param moduleNameToExtendsList
	 * 		A map of module names to other {@linkplain StacksExtendsModule
	 * 		modules} extended by this module.
	 * @param extendsMethodLeafNameToModuleName
	 * 		A map keyed by exported method names with no path to the qualified
	 * 		module path it is originally named from.
	 * @param moduleNameToUsesList
	 * 		A map of module names to other {@linkplain StacksUsesModule modules}
	 * 		used by this module.
	 * @param usesMethodLeafNameToModuleName
	 * 		A map keyed by visible (uses) method names with no path to the
	 * 		qualified module path it is originally named from.
	 */
	public StacksImportModule (final String moduleImportName,
		final HashMap<A_String,ImplementationGroup> implementationGroups,
		final HashMap<String,StacksExtendsModule> moduleNameToExtendsList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			extendsMethodLeafNameToModuleName,
		final HashMap<String,StacksUsesModule> moduleNameToUsesList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			usesMethodLeafNameToModuleName)
	{
		this.moduleName = moduleImportName;
		this.implementationGroups = implementationGroups;
		this.moduleNameToExtendsList = moduleNameToExtendsList;
		this.extendsMethodLeafNameToModuleName =
			extendsMethodLeafNameToModuleName;
		this.moduleNameToUsesList = moduleNameToUsesList;
		this.usesMethodLeafNameToModuleName = usesMethodLeafNameToModuleName;
	}
}
