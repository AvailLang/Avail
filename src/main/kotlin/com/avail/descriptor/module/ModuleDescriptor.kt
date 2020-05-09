/*
 * ModuleDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.module

import com.avail.annotations.AvailMethod
import com.avail.descriptor.Descriptor
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.Companion.newBundleTree
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.*
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.representation.*
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.execution.AvailLoader.LexicalScanner
import com.avail.serialization.SerializerOperation
import com.avail.utility.evaluation.Continuation0
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.function.BinaryOperator

/**
 * A [module][ModuleDescriptor] is the mechanism by which Avail code is
 * organized.  Modules are parsed from files with the extension ".avail" which
 * contain information about:
 *
 *  * the module's name,
 *  * the set of version strings for which this module claims to be compatible,
 *  * the module's prerequisites,
 *  * the names to be exported from the module,
 *  * methods and macros defined in this module,
 *  * negative-precedence rules to help disambiguate complex expressions,
 *  * variables and constants private to the module.
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ModuleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ModuleDescriptor private constructor(mutability: Mutability)
	: Descriptor(mutability, TypeTag.MODULE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [string][StringDescriptor] that names the [module][ModuleDescriptor].
		 */
		NAME,

		/**
		 * The [set][SetDescriptor] of all ancestor modules of this module.  A
		 * module's ancestor set includes the module itself.  While this may
		 * seem like mutual recursion: (1) modules are allowed to mutate this
		 * field after construction, (2) this field is not exposed via
		 * primitives.
		 */
		ALL_ANCESTORS,

		/**
		 * The [set][SetDescriptor] of [versions][StringDescriptor] that this
		 * module alleges to support.
		 */
		VERSIONS,

		/**
		 * A [map][MapDescriptor] from [strings][StringDescriptor] to
		 * [atoms][AtomDescriptor] which act as true names. The true names are
		 * identity-based identifiers that prevent or at least clarify name
		 * conflicts. This field holds only those names that are newly added by
		 * this module.
		 */
		NEW_NAMES,

		/**
		 * A [map][MapDescriptor] from [strings][StringDescriptor] to 
		 * [atoms][AtomDescriptor] which act as true names. The true names are 
		 * identity-based identifiers that prevent or at least clarify name 
		 * conflicts. This field holds only those names that have been imported 
		 * from other modules.
		 */
		IMPORTED_NAMES,

		/**
		 * A [map][MapDescriptor] from [strings][StringDescriptor] to
		 * [atoms][AtomDescriptor] which act as true names. The true names are
		 * identity-based identifiers that prevent or at least clarify name
		 * conflicts. This field holds only those names that are neither
		 * imported from another module nor exported from the current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A [set][SetDescriptor] of [true names][AtomDescriptor] that are 
		 * visible within this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A redundant cached [A_Set] of [A_Atom]s that have been
		 * exported.  These are precisely the [IMPORTED_NAMES] minus the
		 * [PRIVATE_NAMES].
		 */
		EXPORTED_NAMES,

		/**
		 * A [set][SetDescriptor] of [definitions][DefinitionDescriptor] which
		 * implement methods and macros (and forward declarations, abstract
		 * declarations, etc.).
		 */
		METHOD_DEFINITIONS_SET,

		/**
		 * A [set][SetDescriptor] of
		 * [grammatical restrictions][GrammaticalRestrictionDescriptor] defined
		 * within this module.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A [map][MapDescriptor] from [string][StringDescriptor] to a
		 * [variable][VariableDescriptor]. Since [module
		 * variables][DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE]
		 * are never accessible outside the module in which they are defined,
		 * this slot is overwritten with [nil][NilDescriptor.nil] when module
		 * compilation is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A [map][MapDescriptor] from [string][StringDescriptor] to an
		 * [AvailObject]. Since a
		 * [module constants][DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT]
		 * are never accessible outside the module in which they are defined,
		 * this slot is overwritten with [nil][NilDescriptor.nil] when module
		 * compilation is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * A [set][SetDescriptor] of
		 * [semantic restrictions][SemanticRestrictionDescriptor] defined within
		 * this module.
		 */
		SEMANTIC_RESTRICTIONS,

		/**
		 * A [map][MapDescriptor] from [true names][AtomDescriptor] to
		 * [tuples][TupleDescriptor] of seal points.
		 */
		SEALS,

		/**
		 * A [map][MapDescriptor] from the [textual names][StringDescriptor] of
		 * entry point [methods][MethodDescriptor] to their [true
		 * names][AtomDescriptor].
		 */
		ENTRY_POINTS,

		/**
		 * A [tuple][TupleDescriptor] of [functions][FunctionDescriptor] that
		 * should be applied when this [module][ModuleDescriptor] is unloaded.
		 */
		UNLOAD_FUNCTIONS,

		/**
		 * The [A_Set] of [A_Lexer]s defined by this module.
		 */
		LEXERS
	}

	override fun allowsImmutableToMutableReferenceInField(e: AbstractSlotsEnum)
		: Boolean =
			e === ObjectSlots.ALL_ANCESTORS
				|| e === ObjectSlots.VERSIONS
				|| e === ObjectSlots.NEW_NAMES
				|| e === ObjectSlots.IMPORTED_NAMES
				|| e === ObjectSlots.PRIVATE_NAMES
				|| e === ObjectSlots.EXPORTED_NAMES
				|| e === ObjectSlots.VISIBLE_NAMES
				|| e === ObjectSlots.METHOD_DEFINITIONS_SET
				|| e === ObjectSlots.GRAMMATICAL_RESTRICTIONS
				|| e === ObjectSlots.VARIABLE_BINDINGS
				|| e === ObjectSlots.CONSTANT_BINDINGS
				|| e === ObjectSlots.SEMANTIC_RESTRICTIONS
				|| e === ObjectSlots.SEALS
				|| e === ObjectSlots.ENTRY_POINTS
				|| e === ObjectSlots.UNLOAD_FUNCTIONS
				|| e === ObjectSlots.LEXERS

	override fun printObjectOnAvoidingIndent(
		`object`: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("Module: ")
		builder.append(`object`.moduleName())
	}

	override fun o_NameForDebugger(`object`: AvailObject): String =
		(super.o_NameForDebugger(`object`) + " = "
			+ `object`.moduleName().asNativeString())

	@AvailMethod
	override fun o_ModuleName(`object`: AvailObject): A_String =
		`object`.slot(ObjectSlots.NAME)

	@AvailMethod
	override fun o_Versions(`object`: AvailObject): A_Set =
		synchronized(`object`) { return `object`.slot(ObjectSlots.VERSIONS) }

	@AvailMethod
	override fun o_Versions(`object`: AvailObject, versionStrings: A_Set)
	{
		synchronized(`object`) {
			`object`.setSlot(
				ObjectSlots.VERSIONS, versionStrings.traversed().makeShared())
		}
	}

	@AvailMethod
	override fun o_NewNames(`object`: AvailObject): A_Map =
		synchronized(`object`) { return `object`.slot(ObjectSlots.NEW_NAMES) }

	@AvailMethod
	override fun o_ImportedNames(`object`: AvailObject): A_Map =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.IMPORTED_NAMES)
		}

	@AvailMethod
	override fun o_PrivateNames(`object`: AvailObject): A_Map =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.PRIVATE_NAMES)
		}

	@AvailMethod
	override fun o_EntryPoints(`object`: AvailObject): A_Map =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.ENTRY_POINTS)
		}

	@AvailMethod
	override fun o_VisibleNames(`object`: AvailObject): A_Set =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.VISIBLE_NAMES)
		}

	@AvailMethod
	override fun o_ExportedNames(`object`: AvailObject): A_Set
	{
		//TODO MvG Cleanup.
		if (false)
		{
			return `object`.slot(ObjectSlots.EXPORTED_NAMES)
		}
		var exportedNames = SetDescriptor.emptySet()
		synchronized(`object`) {
			for ((_, value) in
				`object`.slot(ObjectSlots.IMPORTED_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setUnionCanDestroy(
					value.makeShared(), true)
			}
			for ((_, value) in
				`object`.slot(ObjectSlots.PRIVATE_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setMinusCanDestroy(
					value.makeShared(), true)
			}
		}
		return exportedNames
	}

	@AvailMethod
	override fun o_MethodDefinitions(`object`: AvailObject): A_Set =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.METHOD_DEFINITIONS_SET)
		}

	@AvailMethod
	override fun o_VariableBindings(`object`: AvailObject): A_Map =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.VARIABLE_BINDINGS)
		}

	@AvailMethod
	override fun o_ConstantBindings(`object`: AvailObject): A_Map =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.CONSTANT_BINDINGS)
		}

	@AvailMethod
	override fun o_AddConstantBinding(
		`object`: AvailObject,
		name: A_String,
		constantBinding: A_Variable)
	{
		synchronized(`object`) {
			assert(constantBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralVariableType()))
			var constantBindings: A_Map =
				`object`.slot(ObjectSlots.CONSTANT_BINDINGS)
			constantBindings = constantBindings.mapAtPuttingCanDestroy(
				name,
				constantBinding,
				true)
			`object`.setSlot(
				ObjectSlots.CONSTANT_BINDINGS, constantBindings.makeShared())
		}
	}

	@AvailMethod
	override fun o_ModuleAddDefinition(
		`object`: AvailObject, definition: A_Definition)
	{
		synchronized(`object`) {
			var methods: A_Set =
				`object`.slot(ObjectSlots.METHOD_DEFINITIONS_SET)
			methods = methods.setWithElementCanDestroy(definition, false)
			`object`.setSlot(
				ObjectSlots.METHOD_DEFINITIONS_SET, methods.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddSeal(
		`object`: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple)
	{
		synchronized(`object`) {
			var seals: A_Map = `object`.slot(ObjectSlots.SEALS)
			var tuple: A_Tuple
			tuple = if (seals.hasKey(methodName))
				{
					seals.mapAt(methodName)
				}
				else
				{
					TupleDescriptor.emptyTuple()
				}
			tuple = tuple.appendCanDestroy(argumentTypes, true)
			seals = seals.mapAtPuttingCanDestroy(methodName, tuple, true)
			`object`.setSlot(ObjectSlots.SEALS, seals.makeShared())
		}
	}

	@AvailMethod
	override fun o_ModuleAddSemanticRestriction(
		`object`: AvailObject,
		semanticRestriction: A_SemanticRestriction)
	{
		synchronized(`object`) {
			var restrictions: A_Set =
				`object`.slot(ObjectSlots.SEMANTIC_RESTRICTIONS)
			restrictions = restrictions.setWithElementCanDestroy(
				semanticRestriction, true)
			restrictions = restrictions.makeShared()
			`object`.setSlot(ObjectSlots.SEMANTIC_RESTRICTIONS, restrictions)
		}
	}

	@AvailMethod
	override fun o_AddVariableBinding(
		`object`: AvailObject,
		name: A_String,
		variableBinding: A_Variable)
	{
		synchronized(`object`) {
			assert(variableBinding.kind().isSubtypeOf(
				VariableTypeDescriptor.mostGeneralVariableType()))
			var variableBindings: A_Map =
				`object`.slot(ObjectSlots.VARIABLE_BINDINGS)
			variableBindings = variableBindings.mapAtPuttingCanDestroy(
				name,
				variableBinding,
				true)
			`object`.setSlot(
				ObjectSlots.VARIABLE_BINDINGS, variableBindings.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddImportedName( `object`: AvailObject, trueName: A_Atom)
	{
		// Add the atom to the current public scope.
		synchronized(`object`) {
			val string: A_String = trueName.atomName()
			var importedNames: A_Map = `object`.slot(ObjectSlots.IMPORTED_NAMES)
			importedNames = importedNames.mapAtReplacingCanDestroy(
				string,
				SetDescriptor.emptySet(),
				BinaryOperator { _: A_BasicObject, set: A_BasicObject ->
					(set as A_Set).setWithElementCanDestroy(
						trueName, true)
				},
				true)
			`object`.setSlot(
				ObjectSlots.IMPORTED_NAMES, importedNames.makeShared())
			var privateNames: A_Map = `object`.slot(ObjectSlots.PRIVATE_NAMES)
			if (privateNames.hasKey(string)
				&& privateNames.mapAt(string).hasElement(trueName))
			{
				// Inclusion has priority over exclusion, even along a
				// different chain of modules.
				val set: A_Set = privateNames.mapAt(string)
				privateNames = if (set.setSize() == 1)
				{
					privateNames.mapWithoutKeyCanDestroy(string, true)
				}
				else
				{
					privateNames.mapAtPuttingCanDestroy(
						string,
						set.setWithoutElementCanDestroy(trueName, true),
						true)
				}
				`object`.setSlot(
					ObjectSlots.PRIVATE_NAMES, privateNames.makeShared())
			}
			var exportedNames: A_Set =
				`object`.slot(ObjectSlots.EXPORTED_NAMES)
			exportedNames =
				exportedNames.setWithElementCanDestroy(trueName, true)
			`object`.setSlot(
				ObjectSlots.EXPORTED_NAMES, exportedNames.makeShared())
			var visibleNames: A_Set = `object`.slot(ObjectSlots.VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
			`object`.setSlot(
				ObjectSlots.VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddImportedNames(`object`: AvailObject, trueNames: A_Set)
	{
		// Add the set of atoms to the current public scope.
		synchronized(`object`) {
			var importedNames: A_Map = `object`.slot(ObjectSlots.IMPORTED_NAMES)
			var privateNames: A_Map = `object`.slot(ObjectSlots.PRIVATE_NAMES)
			for (trueName in trueNames)
			{
				val string: A_String = trueName.atomName()
				importedNames = importedNames.mapAtReplacingCanDestroy(
					string,
					SetDescriptor.emptySet(),
					BinaryOperator { _: A_BasicObject, set: A_BasicObject ->
						(set as A_Set).setWithElementCanDestroy(
							trueName, true)
					},
					true)
				if (privateNames.hasKey(string)
					&& privateNames.mapAt(string).hasElement(trueName!!))
				{
					// Inclusion has priority over exclusion, even along a
					// different chain of modules.
					val set: A_Set = privateNames.mapAt(string)
					privateNames = if (set.setSize() == 1)
					{
						privateNames.mapWithoutKeyCanDestroy(
							string, true)
					}
					else
					{
						privateNames.mapAtPuttingCanDestroy(
							string,
							set.setWithoutElementCanDestroy(trueName, true),
							true)
					}
				}
			}
			`object`.setSlot(
				ObjectSlots.IMPORTED_NAMES, importedNames.makeShared())
			`object`.setSlot(
				ObjectSlots.PRIVATE_NAMES, privateNames.makeShared())
			var exportedNames: A_Set = `object`.slot(ObjectSlots.EXPORTED_NAMES)
			exportedNames = exportedNames.setUnionCanDestroy(trueNames, true)
			`object`.setSlot(
				ObjectSlots.EXPORTED_NAMES, exportedNames.makeShared())
			var visibleNames: A_Set = `object`.slot(ObjectSlots.VISIBLE_NAMES)
			visibleNames = visibleNames.setUnionCanDestroy(trueNames, true)
			`object`.setSlot(ObjectSlots.VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	@AvailMethod
	override fun o_IntroduceNewName(`object`: AvailObject, trueName: A_Atom)
	{
		// Set up this true name, which is local to the module.
		synchronized(`object`) {
			val string: A_String = trueName.atomName()
			var newNames: A_Map = `object`.slot(ObjectSlots.NEW_NAMES)
			assert(!newNames.hasKey(string)) {
				"Can't define a new true name twice in a module"
			}
			newNames = newNames.mapAtPuttingCanDestroy(string, trueName, true)
			`object`.setSlot(ObjectSlots.NEW_NAMES, newNames.makeShared())
			var visibleNames: A_Set = `object`.slot(ObjectSlots.VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
			`object`.setSlot(ObjectSlots.VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddPrivateName(`object`: AvailObject, trueName: A_Atom)
	{
		// Add the atom to the current private scope.
		synchronized(`object`) {
			val string: A_String = trueName.atomName()
			var privateNames: A_Map = `object`.slot(ObjectSlots.PRIVATE_NAMES)
			privateNames = privateNames.mapAtReplacingCanDestroy(
				string,
				SetDescriptor.emptySet(),
				BinaryOperator { _: A_BasicObject, set: A_BasicObject ->
					(set as A_Set).setWithElementCanDestroy(
						trueName, true)
				},
				true)
			`object`.setSlot(
				ObjectSlots.PRIVATE_NAMES, privateNames.makeShared())
			var visibleNames: A_Set = `object`.slot(ObjectSlots.VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(
				trueName, true)
			`object`.setSlot(
				ObjectSlots.VISIBLE_NAMES, visibleNames.makeShared())
			val importedNames: A_Map = `object`.slot(ObjectSlots.IMPORTED_NAMES)
			if (!importedNames.hasKey(string)
				|| !importedNames.mapAt(string).hasElement(trueName))
			{
				var exportedNames: A_Set =
					`object`.slot(ObjectSlots.EXPORTED_NAMES)
				exportedNames = exportedNames.setWithoutElementCanDestroy(
					trueName, true)
				`object`.setSlot(ObjectSlots.EXPORTED_NAMES, exportedNames.makeShared())
			}
		}
	}

	override fun o_AddPrivateNames(`object`: AvailObject, trueNames: A_Set)
	{
		// Add the set of atoms to the current private scope.
		synchronized(`object`) {
			var privateNames: A_Map = `object`.slot(ObjectSlots.PRIVATE_NAMES)
			var visibleNames: A_Set = `object`.slot(ObjectSlots.VISIBLE_NAMES)
			var exportedNames: A_Set = `object`.slot(ObjectSlots.EXPORTED_NAMES)
			val importedNames: A_Map = `object`.slot(ObjectSlots.IMPORTED_NAMES)
			for (trueName in trueNames)
			{
				val string: A_String = trueName.atomName()
				privateNames = privateNames.mapAtReplacingCanDestroy(
					string,
					SetDescriptor.emptySet(),
					BinaryOperator { _: A_BasicObject, set: A_BasicObject ->
						(set as A_Set).setWithElementCanDestroy(
							trueName, true)
					},
					true)
				visibleNames = visibleNames.setWithElementCanDestroy(
					trueName, true)
				if (!importedNames.hasKey(string)
					|| !importedNames.mapAt(string).hasElement(trueName!!))
				{
					exportedNames = exportedNames.setWithoutElementCanDestroy(
						trueName, true)
				}
			}
			`object`.setSlot(
				ObjectSlots.PRIVATE_NAMES, privateNames.makeShared())
			`object`.setSlot(
				ObjectSlots.VISIBLE_NAMES, visibleNames.makeShared())
			`object`.setSlot(
				ObjectSlots.EXPORTED_NAMES, exportedNames.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddEntryPoint(
		`object`: AvailObject,
		stringName: A_String,
		trueName: A_Atom)
	{
		synchronized(`object`) {
			var entryPoints: A_Map = `object`.slot(ObjectSlots.ENTRY_POINTS)
			entryPoints = entryPoints.mapAtPuttingCanDestroy(
				stringName,
				trueName,
				true)
			`object`.setSlot(
				ObjectSlots.ENTRY_POINTS, entryPoints.traversed().makeShared())
		}
	}

	override fun o_AddLexer(`object`: AvailObject, lexer: A_Lexer)
	{
		synchronized(`object`) {
			var lexers: A_Set = `object`.slot(ObjectSlots.LEXERS)
			lexers = lexers.setWithElementCanDestroy(lexer, false)
			`object`.setSlot(ObjectSlots.LEXERS, lexers.makeShared())
		}
	}

	@AvailMethod
	override fun o_AddUnloadFunction(
		`object`: AvailObject, unloadFunction: A_Function)
	{
		synchronized(`object`) {
			var unloadFunctions: A_Tuple =
				`object`.slot(ObjectSlots.UNLOAD_FUNCTIONS)
			unloadFunctions = unloadFunctions.appendCanDestroy(
				unloadFunction, true)
			`object`.setSlot(
				ObjectSlots.UNLOAD_FUNCTIONS, unloadFunctions.makeShared())
		}
	}

	@AvailMethod
	override fun o_Equals(
		`object`: AvailObject, another: A_BasicObject): Boolean =
			// Compare by address (identity).
			another.traversed().sameAddressAs(`object`)

	@AvailMethod
	override fun o_Hash(`object`: AvailObject): Int =
		`object`.slot(ObjectSlots.NAME).hash() * 173 xor -0x20c7c074

	@AvailMethod
	override fun o_Kind(`object`: AvailObject): A_Type =
		TypeDescriptor.Types.MODULE.o()

	override fun o_MakeImmutable(`object`: AvailObject): AvailObject =
		if (isMutable)
		{
			// Modules are always shared, never immutable.
			`object`.makeShared()
		}
		else
		{
			`object`
		}

	override fun o_ModuleAddGrammaticalRestriction(
		`object`: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction)
	{
		synchronized(`object`) {
			var grammaticalRestrictions: A_Set =
				`object`.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS)
			grammaticalRestrictions =
				grammaticalRestrictions.setWithElementCanDestroy(
					grammaticalRestriction, true)
			`object`.setSlot(
				ObjectSlots.GRAMMATICAL_RESTRICTIONS,
				grammaticalRestrictions.makeShared())
		}
	}

	override fun o_ModuleGrammaticalRestrictions(`object`: AvailObject): A_Set =
		`object`.slot(ObjectSlots.GRAMMATICAL_RESTRICTIONS)

	override fun o_ModuleSemanticRestrictions(`object`: AvailObject): A_Set =
		`object`.slot(ObjectSlots.SEMANTIC_RESTRICTIONS)

	@AvailMethod
	override fun o_NameVisible(`object`: AvailObject, trueName: A_Atom): Boolean =
		// Check if the given trueName is visible in this module.
		`object`.visibleNames().hasElement(trueName)

	@AvailMethod
	override fun o_RemoveFrom(
		`object`: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit)
	{
		synchronized(`object`) {
			val unloadFunctions =
				`object`.slot(ObjectSlots.UNLOAD_FUNCTIONS).tupleReverse()
			`object`.setSlot(ObjectSlots.UNLOAD_FUNCTIONS, NilDescriptor.nil)
			// Run unload functions, asynchronously but serially, in reverse
			// order.
			loader.runUnloadFunctions(
				unloadFunctions,
				Continuation0 {
					finishUnloading(`object`, loader)
					afterRemoval()
				})
		}
	}

	/**
	 * Now that the unload functions have completed, perform any other unloading
	 * actions necessary for this module.
	 *
	 * @param object
	 *   The module being unloaded.
	 * @param loader
	 *   The [AvailLoader] through which the module is being unloaded.
	 */
	@Synchronized
	private fun finishUnloading(`object`: AvailObject, loader: AvailLoader)
	{
		val runtime = loader.runtime()
		// Remove method definitions.
		for (definition in `object`.methodDefinitions())
		{
			loader.removeDefinition(definition)
		}
		// Remove semantic restrictions.
		for (restriction in `object`.moduleSemanticRestrictions())
		{
			runtime.removeTypeRestriction(restriction)
		}
		for (restriction in `object`.moduleGrammaticalRestrictions())
		{
			runtime.removeGrammaticalRestriction(restriction)
		}
		// Remove seals.
		val seals: A_Map = `object`.slot(ObjectSlots.SEALS)
		for ((methodName, values) in seals.mapIterable())
		{
			for (seal in values)
			{
				try
				{
					runtime.removeSeal(methodName, seal)
				}
				catch (e: MalformedMessageException)
				{
					assert(false) { "This should not happen!" }
					throw AvailRuntimeException(e.errorCode)
				}
			}
		}
		// Remove lexers.  Don't bother adjusting the
		// loader, since it's not going to parse anything
		// again.  Don't even bother removing it from the
		// module, since that's being unloaded.
		for (lexer in `object`.slot(ObjectSlots.LEXERS))
		{
			lexer.lexerMethod().setLexer(NilDescriptor.nil)
		}
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param object
	 *   The module.
	 * @param forwardDeclaration
	 *   The [forward declaration][ForwardDefinitionDescriptor] to be removed.
	 */
	@AvailMethod
	override fun o_ResolveForward(
		`object`: AvailObject, forwardDeclaration: A_BasicObject)
	{
		synchronized(`object`) {
			assert(forwardDeclaration.isInstanceOfKind(
				TypeDescriptor.Types.FORWARD_DEFINITION.o()))
			var methods: A_Set =
				`object`.slot(ObjectSlots.METHOD_DEFINITIONS_SET)
			assert(methods.hasElement(forwardDeclaration))
			methods = methods.setWithoutElementCanDestroy(
				forwardDeclaration, false)
			`object`.setSlot(
				ObjectSlots.METHOD_DEFINITIONS_SET, methods.makeShared())
		}
	}

	override fun o_ShowValueInNameForDebugger(
		`object`: AvailObject): Boolean = false

	/**
	 * Check what true names are visible in this module under the given string
	 * name.
	 *
	 * @param object
	 *   The module.
	 * @param stringName
	 *   A string whose corresponding [true names][AtomDescriptor] are to be
	 *   looked up in this module.
	 * @return
	 *   The [set][SetDescriptor] of [true names][AtomDescriptor] that have the
	 *   given stringName and are visible in this module.
	 */
	@AvailMethod
	override fun o_TrueNamesForStringName(
		`object`: AvailObject, stringName: A_String): A_Set =
		synchronized(`object`) {
			assert(stringName.isTuple)
			if (`object`.slot(ObjectSlots.NEW_NAMES).hasKey(stringName))
			{
				return SetDescriptor.emptySet().setWithElementCanDestroy(
					`object`.slot(ObjectSlots.NEW_NAMES).mapAt(stringName),
					false)
			}
			val publicNames: A_Set =
				if (`object`.slot(ObjectSlots.IMPORTED_NAMES).hasKey(stringName))
				{
					`object`.slot(ObjectSlots.IMPORTED_NAMES).mapAt(stringName)
				}
				else
				{
					SetDescriptor.emptySet()
				}
			if (!`object`.slot(ObjectSlots.PRIVATE_NAMES).hasKey(stringName))
			{
				return publicNames
			}
			val privates: A_Set =
				`object`.slot(ObjectSlots.PRIVATE_NAMES).mapAt(stringName)
			return if (publicNames.setSize() == 0)
				{
					privates
				}
				else
				{
					publicNames.setUnionCanDestroy(privates, false)
				}
	}

	/**
	 * Create a [bundle tree][MessageBundleTreeDescriptor] to have the
	 * [message bundles][MessageBundleDescriptor] that are visible in
	 * the current module.
	 *
	 * @param object
	 *   The module.
	 */
	@AvailMethod
	override fun o_BuildFilteredBundleTree(`object`: AvailObject): A_BundleTree
	{
		val filteredBundleTree = newBundleTree(NilDescriptor.nil)
		synchronized(`object`) {
			val ancestors: A_Set = `object`.slot(ObjectSlots.ALL_ANCESTORS)
			for (visibleName in `object`.visibleNames())
			{
				val bundle: A_Bundle = visibleName.bundleOrNil()
				if (!bundle.equalsNil())
				{
					for ((key, plan) in
						bundle.definitionParsingPlans().mapIterable())
					{
						if (ancestors.hasElement(key.definitionModule()))
						{
							val planInProgress =
								newPlanInProgress(plan, 1)
							filteredBundleTree.addPlanInProgress(planInProgress)
						}
					}
				}
			}
		}
		return filteredBundleTree
	}

	/**
	 * Create a [LexicalScanner] to have the [lexers][A_Lexer] that are visible
	 * in the current module.
	 *
	 * As a nicety, since the bundle name isn't actually used during lexing,
	 * and since lexers can interfere with each other, de-duplicate lexers that
	 * might be from different bundles but the same method.
	 *
	 * @param object
	 *   The module.
	 */
	@AvailMethod
	override fun o_CreateLexicalScanner(`object`: AvailObject): LexicalScanner
	{
		val lexers: MutableSet<A_Lexer> = HashSet()
		synchronized(`object`) {
			for (visibleName in `object`.visibleNames())
			{
				val bundle: A_Bundle = visibleName.bundleOrNil()
				if (!bundle.equalsNil())
				{
					val method: A_Method = bundle.bundleMethod()
					val lexer = method.lexer()
					if (!lexer.equalsNil())
					{
						lexers.add(lexer)
					}
				}
			}
		}
		val lexicalScanner = LexicalScanner()
		for (lexer in lexers)
		{
			lexicalScanner.addLexer(lexer)
		}
		return lexicalScanner
	}

	override fun o_AllAncestors(`object`: AvailObject): A_Set =
		synchronized(`object`) {
			return `object`.slot(ObjectSlots.ALL_ANCESTORS)
		}

	override fun o_AddAncestors(`object`: AvailObject, moreAncestors: A_Set)
	{
		synchronized(`object`) {
			val union =
				`object`.slot(ObjectSlots.ALL_ANCESTORS).setUnionCanDestroy(
					moreAncestors, true)
			`object`.setSlot(ObjectSlots.ALL_ANCESTORS, union.makeShared())
		}
	}

	override fun o_SerializerOperation(
		`object`: AvailObject): SerializerOperation = SerializerOperation.MODULE

	override fun o_WriteTo(`object`: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("module")
		writer.write("name")
		`object`.slot(ObjectSlots.NAME).writeTo(writer)
		writer.write("versions")
		`object`.slot(ObjectSlots.VERSIONS).writeTo(writer)
		writer.write("entry points")
		`object`.entryPoints().writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): ModuleDescriptor = mutable

	// There is no immutable descriptor. Use the shared one.
	override fun immutable(): ModuleDescriptor = shared

	override fun shared(): ModuleDescriptor = shared

	companion object
	{
		/**
		 * Construct a new empty `module`.  Pre-add the module itself to its
		 * [set][SetDescriptor] of ancestor modules.
		 *
		 * @param moduleName
		 *   The fully qualified [name][StringDescriptor] of the module.
		 * @return
		 *   The new module.
		 */
		@JvmStatic
		fun newModule(moduleName: A_String): A_Module
		{
			val module = mutable.create()
			module.setSlot(
				ObjectSlots.NAME, moduleName)
			module.setSlot(
				ObjectSlots.ALL_ANCESTORS, NilDescriptor.nil)
			module.setSlot(
				ObjectSlots.VERSIONS, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.NEW_NAMES, emptyMap())
			module.setSlot(
				ObjectSlots.IMPORTED_NAMES, emptyMap())
			module.setSlot(
				ObjectSlots.PRIVATE_NAMES, emptyMap())
			module.setSlot(
				ObjectSlots.VISIBLE_NAMES, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.EXPORTED_NAMES, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.METHOD_DEFINITIONS_SET, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.GRAMMATICAL_RESTRICTIONS, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.VARIABLE_BINDINGS, emptyMap())
			module.setSlot(
				ObjectSlots.CONSTANT_BINDINGS, emptyMap())
			module.setSlot(
				ObjectSlots.VARIABLE_BINDINGS, emptyMap())
			module.setSlot(
				ObjectSlots.SEMANTIC_RESTRICTIONS, SetDescriptor.emptySet())
			module.setSlot(
				ObjectSlots.SEALS, emptyMap())
			module.setSlot(
				ObjectSlots.ENTRY_POINTS, emptyMap())
			module.setSlot(
				ObjectSlots.UNLOAD_FUNCTIONS, TupleDescriptor.emptyTuple())
			module.setSlot(
				ObjectSlots.LEXERS, SetDescriptor.emptySet())
			// Adding the module to its ancestors set will cause recursive scanning
			// to mark everything as shared, so it's essential that all fields have
			// been initialized to *something* by now.
			module.makeShared()
			module.setSlot(
				ObjectSlots.ALL_ANCESTORS, SetDescriptor.set(module).makeShared())
			return module
		}

		/** The mutable [ModuleDescriptor].  */
		private val mutable = ModuleDescriptor(Mutability.MUTABLE)

		/** The shared [ModuleDescriptor].  */
		private val shared = ModuleDescriptor(Mutability.SHARED)

		/**
		 * Answer the `ModuleDescriptor module` currently undergoing
		 * [loading][AvailLoader] on the
		 * [current fiber][FiberDescriptor.currentFiber].
		 *
		 * @return
		 *   The module currently undergoing loading, or
		 *   [nil][NilDescriptor.nil] if the current fiber is not a loader fiber.
		 */
		fun currentModule(): A_Module
		{
			val fiber = FiberDescriptor.currentFiber()
			val loader = fiber.availLoader() ?: return NilDescriptor.nil
			return loader.module()
		}
	}
}