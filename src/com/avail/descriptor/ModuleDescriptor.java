/**
 * ModuleDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.ModuleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.json.JSONWriter;

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
public final class ModuleDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain StringDescriptor string} that names the {@linkplain
		 * ModuleDescriptor module}.
		 */
		NAME,

		/**
		 * The {@linkplain SetDescriptor set} of all ancestor modules of this
		 * module.  A module's ancestor set includes the module itself.  While
		 * this may seem like mutual recursion: (1) modules are allowed to
		 * mutate this field after construction, (2) this field is not exposed
		 * via primitives.
		 */
		ALL_ANCESTORS,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * StringDescriptor versions} that this module alleges to support.
		 */
		VERSIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain
		 * StringDescriptor strings} to {@linkplain AtomDescriptor atoms} which
		 * act as true names. The true names are identity-based identifiers
		 * that prevent or at least clarify name conflicts. This field holds
		 * only those names that are newly added by this module.
		 */
		NEW_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * strings} to {@linkplain AtomDescriptor atoms} which act as true
		 * names. The true names are identity-based identifiers that prevent or
		 * at least clarify name conflicts. This field holds only those names
		 * that have been imported from other modules.
		 */
		IMPORTED_NAMES,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * strings} to {@linkplain AtomDescriptor atoms} which act as true
		 * names. The true names are identity-based identifiers that prevent or
		 * at least clarify name conflicts. This field holds only those names
		 * that are neither imported from another module nor exported from the
		 * current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain AtomDescriptor true
		 * names} that are visible within this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain DefinitionDescriptor
		 * definitions} which implement methods and macros (and forward
		 * declarations, abstract declarations, etc.).
		 */
		METHOD_DEFINITIONS_SET,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * GrammaticalRestrictionDescriptor grammatical restrictions} defined
		 * within this module.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * string} to a {@linkplain VariableDescriptor variable}. Since
		 * {@linkplain DeclarationKind#MODULE_VARIABLE
		 * module variables} are never accessible outside the module in which
		 * they are defined, this slot is overwritten with {@linkplain
		 * NilDescriptor#nil() nil} when module compilation is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain StringDescriptor
		 * string} to an {@linkplain AvailObject}. Since a {@linkplain
		 * DeclarationKind#MODULE_CONSTANT module
		 * constants} are never accessible outside the module in which they are
		 * defined, this slot is overwritten with {@linkplain
		 * NilDescriptor#nil() nil} when module compilation is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * SemanticRestrictionDescriptor semantic restrictions} defined within
		 * this module.
		 */
		SEMANTIC_RESTRICTIONS,

		/**
		 * A {@linkplain MapDescriptor map} from {@linkplain AtomDescriptor true
		 * names} to {@linkplain TupleDescriptor tuples} of seal points.
		 */
		SEALS,

		/**
		 * A {@linkplain MapDescriptor map} from the {@linkplain
		 * StringDescriptor textual names} of entry point {@linkplain
		 * MethodDescriptor methods} to their {@linkplain AtomDescriptor true
		 * names}.
		 */
		ENTRY_POINTS,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * FunctionDescriptor functions} that should be applied when this
		 * {@linkplain ModuleDescriptor module} is unloaded.
		 */
		UNLOAD_FUNCTIONS,

		/**
		 * The {@link A_Set} of {@link A_Lexer}s defined by this module.
		 */
		LEXERS;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == ALL_ANCESTORS
			|| e == VERSIONS
			|| e == NEW_NAMES
			|| e == IMPORTED_NAMES
			|| e == PRIVATE_NAMES
			|| e == VISIBLE_NAMES
			|| e == METHOD_DEFINITIONS_SET
			|| e == GRAMMATICAL_RESTRICTIONS
			|| e == VARIABLE_BINDINGS
			|| e == CONSTANT_BINDINGS
			|| e == SEMANTIC_RESTRICTIONS
			|| e == SEALS
			|| e == ENTRY_POINTS
			|| e == UNLOAD_FUNCTIONS
			|| e == LEXERS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("Module: ");
		builder.append(object.moduleName());
	}

	@Override @AvailMethod
	A_String o_ModuleName (final AvailObject object)
	{
		return object.slot(NAME);
	}

	@Override @AvailMethod
	A_Set o_Versions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VERSIONS);
		}
	}

	@Override @AvailMethod
	void o_Versions (final AvailObject object, final A_Set versionStrings)
	{
		synchronized (object)
		{
			object.setSlot(VERSIONS, versionStrings.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	A_Map o_NewNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(NEW_NAMES);
		}
	}

	@Override @AvailMethod
	A_Map o_ImportedNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(IMPORTED_NAMES);
		}
	}

	@Override @AvailMethod
	A_Map o_PrivateNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(PRIVATE_NAMES);
		}
	}

	@Override @AvailMethod
	A_Map o_EntryPoints (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(ENTRY_POINTS);
		}
	}

	@Override @AvailMethod
	A_Set o_VisibleNames (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VISIBLE_NAMES);
		}
	}

	@Override @AvailMethod
	A_Set o_ExportedNames (final AvailObject object)
	{
		A_Set exportedNames = SetDescriptor.emptySet();
		synchronized (object)
		{
			for (final Entry entry
				: object.slot(IMPORTED_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setUnionCanDestroy(
					entry.value().makeShared(), true);
			}
			for (final Entry entry
				: object.slot(PRIVATE_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setMinusCanDestroy(
					entry.value().makeShared(), true);
			}
		}
		return exportedNames;
	}

	@Override @AvailMethod
	A_Set o_MethodDefinitions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(METHOD_DEFINITIONS_SET);
		}
	}

	@Override @AvailMethod
	A_Map o_VariableBindings (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(VARIABLE_BINDINGS);
		}
	}

	@Override @AvailMethod
	A_Map o_ConstantBindings (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(CONSTANT_BINDINGS);
		}
	}

	@Override @AvailMethod
	void o_AddConstantBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable constantBinding)
	{
		synchronized (object)
		{
			assert constantBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralVariableType());
			A_Map constantBindings = object.slot(CONSTANT_BINDINGS);
			constantBindings = constantBindings.mapAtPuttingCanDestroy(
				name,
				constantBinding,
				true);
			object.setSlot(CONSTANT_BINDINGS, constantBindings.makeShared());
		}
	}

	@Override @AvailMethod
	void o_ModuleAddDefinition (
		final AvailObject object,
		final A_BasicObject definition)
	{
		synchronized (object)
		{
			A_Set methods = object.slot(METHOD_DEFINITIONS_SET);
			methods = methods.setWithElementCanDestroy(definition, false);
			object.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes)
	{
		synchronized (object)
		{
			A_Map seals = object.slot(SEALS);
			A_Tuple tuple;
			if (seals.hasKey(methodName))
			{
				tuple = seals.mapAt(methodName);
			}
			else
			{
				tuple = TupleDescriptor.emptyTuple();
			}
			tuple = tuple.appendCanDestroy(argumentTypes, true);
			seals = seals.mapAtPuttingCanDestroy(methodName, tuple, true);
			object.setSlot(SEALS, seals.makeShared());
		}
	}

	@Override @AvailMethod
	void o_ModuleAddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction semanticRestriction)
	{
		synchronized (object)
		{
			A_Set restrictions = object.slot(SEMANTIC_RESTRICTIONS);
			restrictions = restrictions.setWithElementCanDestroy(
				semanticRestriction,
				true);
			restrictions = restrictions.makeShared();
			object.setSlot(SEMANTIC_RESTRICTIONS, restrictions);
		}
	}

	@Override @AvailMethod
	void o_AddVariableBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable variableBinding)
	{
		synchronized (object)
		{
			assert variableBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralVariableType());
			A_Map variableBindings = object.slot(VARIABLE_BINDINGS);
			variableBindings = variableBindings.mapAtPuttingCanDestroy(
				name,
				variableBinding,
				true);
			object.setSlot(VARIABLE_BINDINGS, variableBindings.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddImportedName (
		final AvailObject object,
		final A_Atom trueName)
	{
		// Add the trueName to the current public scope.
		synchronized (object)
		{
			final A_String string = trueName.atomName();
			A_Map names = object.slot(IMPORTED_NAMES);
			A_Set set;
			if (names.hasKey(string))
			{
				set = names.mapAt(string);
			}
			else
			{
				set = SetDescriptor.emptySet();
			}
			set = set.setWithElementCanDestroy(trueName, false);
			names = names.mapAtPuttingCanDestroy(string, set, true);
			object.setSlot(IMPORTED_NAMES, names.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddImportedNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		// Add the trueName to the current public scope.
		synchronized (object)
		{
			A_Map names = object.slot(IMPORTED_NAMES);
			for (final A_Atom trueName : trueNames)
			{
				final A_String string = trueName.atomName();
				final A_Set set = names.hasKey(string)
					? names.mapAt(string)
					: SetDescriptor.emptySet();
				names = names.mapAtPuttingCanDestroy(
					string,
					set.setWithElementCanDestroy(trueName, true),
					true);
			}
			object.setSlot(IMPORTED_NAMES, names.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setUnionCanDestroy(trueNames, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_IntroduceNewName (
		final AvailObject object,
		final A_Atom trueName)
	{
		// Set up this true name, which is local to the module.
		synchronized (object)
		{
			final A_String string = trueName.atomName();
			A_Map newNames = object.slot(NEW_NAMES);
			assert !newNames.hasKey(string)
				: "Can't define a new true name twice in a module";
			newNames = newNames.mapAtPuttingCanDestroy(string, trueName, true);
			object.setSlot(NEW_NAMES, newNames.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddPrivateName (
		final AvailObject object,
		final A_Atom trueName)
	{
		// Add the trueName to the current private scope.
		synchronized (object)
		{
			final A_String string = trueName.atomName();
			A_Map privateNames = object.slot(PRIVATE_NAMES);
			A_Set set;
			if (privateNames.hasKey(string))
			{
				set = privateNames.mapAt(string);
			}
			else
			{
				set = SetDescriptor.emptySet();
			}
			set = set.setWithElementCanDestroy(trueName, false);
			privateNames = privateNames.mapAtPuttingCanDestroy(
				string,
				set,
				true);
			object.setSlot(PRIVATE_NAMES, privateNames.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override
	void o_AddPrivateNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		// Add the set of trueName atoms to the current private scope.
		synchronized (object)
		{
			A_Map privateNames = object.slot(PRIVATE_NAMES);
			for (final A_Atom trueName : trueNames)
			{
				final A_String string = trueName.atomName();
				A_Set set = privateNames.hasKey(string)
					? privateNames.mapAt(string)
					: SetDescriptor.emptySet();
				set = set.setWithElementCanDestroy(trueName, true);
				privateNames = privateNames.mapAtPuttingCanDestroy(
					string,
					set,
					true);
			}
			object.setSlot(PRIVATE_NAMES, privateNames.makeShared());
			A_Set visibleNames = object.slot(VISIBLE_NAMES);
			visibleNames = visibleNames.setUnionCanDestroy(trueNames, true);
			object.setSlot(VISIBLE_NAMES, visibleNames.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddEntryPoint (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		synchronized (object)
		{
			A_Map entryPoints = object.slot(ENTRY_POINTS);
			entryPoints = entryPoints.mapAtPuttingCanDestroy(
				stringName,
				trueName,
				true);
			object.setSlot(ENTRY_POINTS, entryPoints.traversed().makeShared());
		}
	}

	@Override
	void o_AddLexer (
		final AvailObject object,
		final A_Lexer lexer)
	{
		synchronized (object)
		{
			A_Set lexers = object.slot(LEXERS);
			lexers = lexers.setWithElementCanDestroy(lexer, false);
			object.setSlot(LEXERS, lexers.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddUnloadFunction (
		final AvailObject object,
		final A_Function unloadFunction)
	{
		synchronized (object)
		{
			A_Tuple unloadFunctions = object.slot(UNLOAD_FUNCTIONS);
			unloadFunctions = unloadFunctions.appendCanDestroy(
				unloadFunction, true);
			object.setSlot(UNLOAD_FUNCTIONS, unloadFunctions.makeShared());
		}
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		// Compare by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(NAME).hash() * 173 ^ 0xDF383F8C;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return MODULE.o();
	}

	@Override
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Modules are always shared, never immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override
	void o_ModuleAddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		synchronized (object)
		{
			A_Set grammaticalRestrictions =
				object.slot(GRAMMATICAL_RESTRICTIONS);
			grammaticalRestrictions =
				grammaticalRestrictions.setWithElementCanDestroy(
					grammaticalRestriction, true);
			object.setSlot(
				GRAMMATICAL_RESTRICTIONS, grammaticalRestrictions.makeShared());
		}
	}

	@Override
	A_Set o_ModuleGrammaticalRestrictions (final AvailObject object)
	{
		return object.slot(GRAMMATICAL_RESTRICTIONS);
	}

	@Override
	A_Set o_ModuleSemanticRestrictions (final AvailObject object)
	{
		return object.slot(SEMANTIC_RESTRICTIONS);
	}

	@Override @AvailMethod
	boolean o_NameVisible (final AvailObject object, final A_Atom trueName)
	{
		// Check if the given trueName is visible in this module.
		return object.visibleNames().hasElement(trueName);
	}

	@Override @AvailMethod
	void o_RemoveFrom (
		final AvailObject object,
		final AvailLoader aLoader,
		final Continuation0 afterRemoval)
	{
		synchronized (object)
		{
			final AvailRuntime runtime = aLoader.runtime();
			final A_Tuple unloadFunctions =
				object.slot(UNLOAD_FUNCTIONS).tupleReverse();
			object.setSlot(UNLOAD_FUNCTIONS, NilDescriptor.nil());
			// Run unload functions, asynchronously but serially, in reverse
			// order.
			aLoader.runUnloadFunctions(
				unloadFunctions,
				() ->
				{
					synchronized (object)
					{
						// Remove method definitions.
						for (final A_Definition definition
							: object.methodDefinitions())
						{
							aLoader.removeDefinition(definition);
						}
						// Remove semantic restrictions.
						for (final A_SemanticRestriction restriction :
							object.moduleSemanticRestrictions())
						{
							runtime.removeTypeRestriction(restriction);
						}
						for (final A_GrammaticalRestriction restriction :
							object.moduleGrammaticalRestrictions())
						{
							runtime.removeGrammaticalRestriction(
								restriction);
						}
						// Remove seals.
						final A_Map seals = object.slot(SEALS);
						for (final Entry entry :
							seals.mapIterable())
						{
							final A_Atom methodName = entry.key();
							for (final A_Tuple seal : entry.value())
							{
								try
								{
									runtime.removeSeal(methodName, seal);
								}
								catch (final MalformedMessageException e)
								{
									assert false
										: "This should not happen!";
									throw new AvailRuntimeException(
										e.errorCode());
								}
							}
						}
						// Remove lexers.  Don't bother adjusting the
						// loader, since it's not going to parse anything
						// again.  Don't even bother removing it from the
						// module, since that's being unloaded.
						for (final A_Lexer lexer : object.slot(LEXERS))
						{
							lexer.lexerMethod().setLexer(
								NilDescriptor.nil());
						}
					}
					afterRemoval.value();
				});
		}
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param object
	 *        The module.
	 * @param forwardDeclaration
	 *        The {@linkplain ForwardDefinitionDescriptor forward declaration}
	 *        to be removed.
	 */
	@Override @AvailMethod
	void o_ResolveForward (
		final AvailObject object,
		final A_BasicObject forwardDeclaration)
	{
		synchronized (object)
		{
			assert forwardDeclaration.isInstanceOfKind(FORWARD_DEFINITION.o());
			A_Set methods = object.slot(METHOD_DEFINITIONS_SET);
			assert methods.hasElement(forwardDeclaration);
			methods = methods.setWithoutElementCanDestroy(
				forwardDeclaration, false);
			object.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared());
		}
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
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
	A_Set o_TrueNamesForStringName (
		final AvailObject object,
		final A_String stringName)
	{
		synchronized (object)
		{
			assert stringName.isTuple();
			if (object.slot(NEW_NAMES).hasKey(stringName))
			{
				return SetDescriptor.emptySet().setWithElementCanDestroy(
					object.slot(NEW_NAMES).mapAt(stringName),
					false);
			}
			final A_Set publics;
			if (object.slot(IMPORTED_NAMES).hasKey(stringName))
			{
				publics = object.slot(IMPORTED_NAMES).mapAt(stringName);
			}
			else
			{
				publics = SetDescriptor.emptySet();
			}
			if (!object.slot(PRIVATE_NAMES).hasKey(stringName))
			{
				return publics;
			}
			final A_Set privates = object.slot(PRIVATE_NAMES).mapAt(stringName);
			if (publics.setSize() == 0)
			{
				return privates;
			}
			return publics.setUnionCanDestroy(privates, false);
		}
	}

	/**
	 * Create a {@linkplain MessageBundleTreeDescriptor bundle tree} to have the
	 * {@linkplain MessageBundleDescriptor message bundles} that are visible in
	 * the current module.
	 *
	 * @param object The module.
	 */
	@Override @AvailMethod
	A_BundleTree o_BuildFilteredBundleTree (
		final AvailObject object)
	{
		final A_BundleTree filteredBundleTree =
			MessageBundleTreeDescriptor.createEmpty();
		synchronized (object)
		{
			final A_Set ancestors = object.slot(ALL_ANCESTORS);
			for (final A_Atom visibleName : object.visibleNames())
			{
				final A_Bundle bundle = visibleName.bundleOrNil();
				if (!bundle.equalsNil())
				{
					for (final Entry definitionEntry
						: bundle.definitionParsingPlans().mapIterable())
					{
						if (ancestors.hasElement(
							definitionEntry.key().definitionModule()))
						{
							final A_DefinitionParsingPlan plan =
								definitionEntry.value();
							final A_ParsingPlanInProgress planInProgress =
								ParsingPlanInProgressDescriptor.create(
									plan, 1);
							filteredBundleTree.addPlanInProgress(
								planInProgress);
						}
					}
				}
			}
		}
		return filteredBundleTree;
	}

	/**
	 * Create a {@link LexicalScanner} to have the {@link A_Lexer lexers} that
	 * are isible in the current module.
	 *
	 * <p>As a nicety, since the bundle name isn't actually used during lexing,
	 * and since lexers can interfere with each other, de-duplicate lexers that
	 * might be from different bundles but the same method.</p>
	 *
	 * @param object The module.
	 */
	@Override @AvailMethod
	LexicalScanner o_CreateLexicalScanner (
		final AvailObject object)
	{
		final Set<A_Lexer> lexers = new HashSet<>();
		synchronized (object)
		{
			for (final A_Atom visibleName : object.visibleNames())
			{
				final A_Bundle bundle = visibleName.bundleOrNil();
				if (!bundle.equalsNil())
				{
					final A_Method method = bundle.bundleMethod();
					final A_Lexer lexer = method.lexer();
					if (!lexer.equalsNil())
					{
						lexers.add(lexer);
					}
				}
			}
		}
		final LexicalScanner lexicalScanner = new LexicalScanner();
		for (final A_Lexer lexer : lexers)
		{
			lexicalScanner.addLexer(lexer);
		}
		return lexicalScanner;
	}

	@Override
	A_Set o_AllAncestors (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(ALL_ANCESTORS);
		}
	}

	@Override
	void o_AddAncestors (final AvailObject object, final A_Set moreAncestors)
	{
		synchronized (object)
		{
			final A_Set union = object.slot(ALL_ANCESTORS).setUnionCanDestroy(
				moreAncestors,
				true);
			object.setSlot(ALL_ANCESTORS, union.makeShared());
		}
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MODULE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("module");
		writer.write("name");
		object.slot(NAME).writeTo(writer);
		writer.write("versions");
		object.slot(VERSIONS).writeTo(writer);
		writer.write("entry points");
		object.entryPoints().writeTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new empty anonymous {@linkplain ModuleDescriptor module}.
	 * Pre-add the module itself to its {@linkplain SetDescriptor set} of
	 * ancestor modules.
	 *
	 * @return The new module.
	 */
	public static A_Module anonymousModule ()
	{
		return newModule(StringDescriptor.stringFrom("/«fake-root»/«anonymous»"));
	}

	/**
	 * Construct a new empty {@linkplain ModuleDescriptor module}.  Pre-add
	 * the module itself to its {@linkplain SetDescriptor set} of ancestor
	 * modules.
	 *
	 * @param moduleName
	 *        The fully qualified {@linkplain StringDescriptor name} of the
	 *        module.
	 * @return The new module.
	 */
	public static A_Module newModule (final A_String moduleName)
	{
		final A_Map emptyMap = MapDescriptor.emptyMap();
		final A_Set emptySet = SetDescriptor.emptySet();
		final A_Tuple emptyTuple = TupleDescriptor.emptyTuple();
		final AvailObject module = mutable.create();
		module.setSlot(NAME, moduleName);
		module.setSlot(ALL_ANCESTORS, NilDescriptor.nil());
		module.setSlot(VERSIONS, emptySet);
		module.setSlot(NEW_NAMES, emptyMap);
		module.setSlot(IMPORTED_NAMES, emptyMap);
		module.setSlot(PRIVATE_NAMES, emptyMap);
		module.setSlot(VISIBLE_NAMES, emptySet);
		module.setSlot(METHOD_DEFINITIONS_SET, emptySet);
		module.setSlot(GRAMMATICAL_RESTRICTIONS, emptySet);
		module.setSlot(VARIABLE_BINDINGS, emptyMap);
		module.setSlot(CONSTANT_BINDINGS, emptyMap);
		module.setSlot(VARIABLE_BINDINGS, emptyMap);
		module.setSlot(SEMANTIC_RESTRICTIONS, emptySet);
		module.setSlot(SEALS, emptyMap);
		module.setSlot(ENTRY_POINTS, emptyMap);
		module.setSlot(UNLOAD_FUNCTIONS, emptyTuple);
		module.setSlot(LEXERS, emptySet);
		// Adding the module to its ancestors set will cause recursive scanning
		// to mark everything as shared, so it's essential that all fields have
		// been initialized to *something* by now.
		module.makeShared();
		module.setSlot(
			ALL_ANCESTORS,
			emptySet.setWithElementCanDestroy(module, true).makeShared());
		return module;
	}

	/**
	 * Construct a new {@link ModuleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ModuleDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.MODULE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ModuleDescriptor}. */
	private static final ModuleDescriptor mutable =
		new ModuleDescriptor(Mutability.MUTABLE);

	@Override
	ModuleDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ModuleDescriptor}. */
	private static final ModuleDescriptor shared =
		new ModuleDescriptor(Mutability.SHARED);

	@Override
	ModuleDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	ModuleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Answer the {@linkplain ModuleDescriptor module} currently undergoing
	 * {@linkplain AvailLoader loading} on the {@linkplain
	 * FiberDescriptor#currentFiber() current} {@linkplain FiberDescriptor fiber}.
	 *
	 * @return The module currently undergoing loading, or {@linkplain
	 *         NilDescriptor#nil() nil} if the current fiber is not a loader
	 *         fiber.
	 */
	public static A_Module current ()
	{
		final A_Fiber fiber = FiberDescriptor.currentFiber();
		final AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return NilDescriptor.nil();
		}
		return loader.module();
	}
}
