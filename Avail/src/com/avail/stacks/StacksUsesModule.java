/**
 * StacksUsesModule.java
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
import com.avail.descriptor.A_Map;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.StringDescriptor;

/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being used by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksUsesModule extends StacksImportModule
{

	/**
	 * The {@linkplain MapDescriptor map} of renames ({@linkplain
	 * StringDescriptor string} → string) explicitly specified in this
	 * import declaration.  The keys are the newly introduced names and the
	 * values are the names provided by the predecessor module.
	 */
	public final A_Map renames;

	/**
	 * Construct a new {@link StacksUsesModule}.
	 *
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
	 * @param moduleNameToUsesList
	 * 		A map of module names to other {@linkplain StacksUsesModule modules}
	 * 		used by this module.
	 * @param renames
	 * 		The {@linkplain MapDescriptor map} of renames ({@linkplain
	 * 		StringDescriptor string} → string) explicitly specified in this
	 * 		import declaration.  The keys are the newly introduced names and
	 * 		the values are the names provided by the predecessor module.
	 * 	 * @param usesMethodLeafNameToModuleName
	 * 		A map keyed by visible (uses) method names with no path to the
	 * 		qualified module path it is originally named from.
	 */
	public StacksUsesModule (final String moduleImportName,
		final HashMap<String,ImplementationGroup> implementationGroups,
		final HashMap<String, StacksExtendsModule> moduleNameToExtendsList,
		final HashMap<String,String> methodLeafNameToModuleName,
		final HashMap<String,StacksUsesModule> moduleNameToUsesList,
		final HashMap<String,String> usesMethodLeafNameToModuleName,
		final A_Map renames)
	{
		super(moduleImportName,implementationGroups,moduleNameToExtendsList,
			methodLeafNameToModuleName, moduleNameToUsesList,
			usesMethodLeafNameToModuleName);

		this.renames = renames;
	}

	@Override
	public StacksUsesModule getUsesModuleForImplementationName(
		final String name)
	{
		if (implementations().containsKey(name))
		{
			return this;
		}
		else if (hasImplementationInBranch(name))
		{
			final String owningModule = extendsMethodLeafNameToModuleName().get(name);
			if (moduleNameToExtendsList().containsKey(owningModule))
			{
				return moduleNameToUsesList().get(owningModule);
			}

			for (final StacksUsesModule extendsModule :
				moduleNameToUsesList().values())
			{
				if (extendsModule.extendsMethodLeafNameToModuleName().containsKey(name))
				{
					return extendsModule
						.getUsesModuleForImplementationName(name);
				}
			}
		}

		return null;
	}

	/**
	 * Obtain the {@linkplain StacksExtendsModule module} that the method name
	 * originates from
	 * @param name
	 * 		The name of the method being searched
	 * @param owningModule
	 * 		The name of the module the method belongs to
	 * @return
	 */
	@SuppressWarnings("null")
	public StacksExtendsModule getExtendsModuleForImplementationName(
		final String name, final String owningModule)
	{
		int separaterIndex = owningModule.lastIndexOf("/");
		String parentModule = owningModule
			.substring(0,separaterIndex);

		StacksExtendsModule owningExtendsModule = null;

		while (separaterIndex >= 0 && owningExtendsModule == null)
		{
			owningExtendsModule =
				moduleNameToExtendsList().get(parentModule);

			if (owningExtendsModule == null)
			{
				parentModule = parentModule
					.substring(0,separaterIndex);
				separaterIndex = parentModule.lastIndexOf("/");
			}
		}

		return owningExtendsModule.getExtendsModuleForImplementationName(name);
	}

	/**
	 * @param name
	 * @return
	 */
	public String getExtendsModuleNameForImplementationName (
		final String name)
	{
		if (extendsMethodLeafNameToModuleName().containsKey(name))
		{
			return extendsMethodLeafNameToModuleName().get(name);
		}
		return null;
	}

	@Override
	public void addMethodImplementation (final String key,
		final MethodCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key).addMethod(implementation);
		}
		else
		{
			getUsesModuleForImplementationName(key)
				.addMethodImplementation(key, implementation);
		}
	}

	@Override
	public void addSemanticImplementation (final String key,
		final SemanticRestrictionCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key)
				.addSemanticRestriction(implementation);
		}
		else
		{
			getUsesModuleForImplementationName(key)
				.addSemanticImplementation(key, implementation);
		}
	}

	@Override
	public void addGrammaticalImplementation (final String key,
		final GrammaticalRestrictionCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key)
				.addGrammaticalRestriction(implementation);
		}
		else
		{
			getUsesModuleForImplementationName(key)
				.addGrammaticalImplementation(key,implementation);
		}
	}

	@Override
	public void addClassImplementationGroup (final String key,
		final ImplementationGroup classImplementationGroup)
	{
		if (implementations().containsKey(key))
		{
			implementations().put(key, classImplementationGroup);
		}
		else
		{
			getUsesModuleForImplementationName(key)
				.addClassImplementationGroup(key, classImplementationGroup);
		}
	}

	@Override
	public void addGlobalImplementationGroup (
		final String key,
		final ImplementationGroup globalImplementationGroup)
	{
		if (implementations().containsKey(key))
		{
			implementations().put(key, globalImplementationGroup);
		}
		else
		{
			getUsesModuleForImplementationName(key)
				.addGlobalImplementationGroup(key, globalImplementationGroup);
		}
	}

	@Override
	public void renameImplementation (final String key,
		final String newName, final String changingModuleName)
	{
		if (implementations().containsKey(key))
		{
			final String [] directory = newName.split("/");
			final String alias = directory[directory.length - 1];
			implementations().get(key).addAlias(alias);
			implementations().put(newName, implementations().get(key));
			extendsMethodLeafNameToModuleName()
				.put(newName, changingModuleName);
		}
		else
		{
			final StacksImportModule extendsModule =
				getUsesModuleForImplementationName(key);

			if (extendsModule != null)
			{
				extendsModule.renameImplementation(key, newName,
					changingModuleName);
			}
		}
	}

	@Override
	public void removeImplementation (final String key)
	{
		if (implementations().containsKey(key))
		{
			implementations().remove(key);
			extendsMethodLeafNameToModuleName().remove(key);
		}
		else
		{
			final StacksImportModule extendsModule =
				getUsesModuleForImplementationName(key);

			if (extendsModule != null)
			{
				extendsModule.removeImplementation(key);
			}
		}
	}

	/**
	 * Construct a new {@link StacksUsesModule} from a
	 * {@linkplain StacksCommentsModule}
	 * @param module
	 * 		The {@linkplain StacksCommentsModule} to be transformed
	 * @param renamesMap
	 * 		The renames {@linkplain A_Map map}
	 */
	public StacksUsesModule(
		final StacksCommentsModule module, final A_Map renamesMap)
	{
		super(module.moduleName(),
			new HashMap<String,ImplementationGroup>(
				module.namedPublicCommentImplementations()),
			new HashMap<String,StacksExtendsModule>(
				module.extendedNamesImplementations()),
			new HashMap<String,String>(
				module.extendsMethodLeafNameToModuleName()),
			module.usesNamesImplementations(),
			module.usesMethodLeafNameToModuleName());
			this.renames = renamesMap;
	}
}

