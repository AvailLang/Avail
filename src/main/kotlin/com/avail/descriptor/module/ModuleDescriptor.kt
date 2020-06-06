/*
 * ModuleDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
 import com.avail.descriptor.methods.A_Definition
 import com.avail.descriptor.methods.A_GrammaticalRestriction
 import com.avail.descriptor.methods.A_Method
 import com.avail.descriptor.methods.A_SemanticRestriction
 import com.avail.descriptor.methods.DefinitionDescriptor
 import com.avail.descriptor.methods.ForwardDefinitionDescriptor
 import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor
 import com.avail.descriptor.methods.MethodDescriptor
 import com.avail.descriptor.methods.SemanticRestrictionDescriptor
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.ALL_ANCESTORS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.CONSTANT_BINDINGS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.ENTRY_POINTS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.EXPORTED_NAMES
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.GRAMMATICAL_RESTRICTIONS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.IMPORTED_NAMES
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.LEXERS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.METHOD_DEFINITIONS_SET
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.NAME
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.NEW_NAMES
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.PRIVATE_NAMES
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.SEALS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.SEMANTIC_RESTRICTIONS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.UNLOAD_FUNCTIONS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.VARIABLE_BINDINGS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.VERSIONS
 import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.VISIBLE_NAMES
 import com.avail.descriptor.parsing.A_Lexer
 import com.avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
 import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.AbstractSlotsEnum
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.Descriptor
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.NilDescriptor
 import com.avail.descriptor.representation.NilDescriptor.Companion.nil
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.sets.A_Set
 import com.avail.descriptor.sets.SetDescriptor
 import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
 import com.avail.descriptor.sets.SetDescriptor.Companion.set
 import com.avail.descriptor.tuples.A_String
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.tuples.StringDescriptor
 import com.avail.descriptor.tuples.TupleDescriptor
 import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.TypeDescriptor.Types
 import com.avail.descriptor.types.TypeTag
 import com.avail.descriptor.types.VariableTypeDescriptor.mostGeneralVariableType
 import com.avail.descriptor.variables.A_Variable
 import com.avail.descriptor.variables.VariableDescriptor
 import com.avail.exceptions.AvailRuntimeException
 import com.avail.exceptions.MalformedMessageException
 import com.avail.interpreter.execution.AvailLoader
 import com.avail.interpreter.execution.AvailLoader.LexicalScanner
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.json.JSONWriter
 import java.util.*

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
		 * A [string][StringDescriptor] that names the
		 * [module][ModuleDescriptor].
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
		 * A [set][SetDescriptor] of [true&#32;names][AtomDescriptor] that are
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
		 * [grammatical&#32;restrictions][GrammaticalRestrictionDescriptor]
		 * defined within this module.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * A [map][MapDescriptor] from [string][StringDescriptor] to a
		 * [variable][VariableDescriptor]. Since
		 * [module&#32;variables][DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE]
		 * are never accessible outside the module in which they are defined,
		 * this slot is overwritten with [nil][NilDescriptor.nil] when module
		 * compilation is complete.
		 */
		VARIABLE_BINDINGS,

		/**
		 * A [map][MapDescriptor] from [string][StringDescriptor] to an
		 * [AvailObject]. Since a
		 * [module&#32;constants][DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT]
		 * are never accessible outside the module in which they are defined,
		 * this slot is overwritten with [nil][NilDescriptor.nil] when module
		 * compilation is complete.
		 */
		CONSTANT_BINDINGS,

		/**
		 * A [set][SetDescriptor] of
		 * [semantic&#32;restrictions][SemanticRestrictionDescriptor] defined
		 * within this module.
		 */
		SEMANTIC_RESTRICTIONS,

		/**
		 * A [map][MapDescriptor] from [true&#32;names][AtomDescriptor] to
		 * [tuples][TupleDescriptor] of seal points.
		 */
		SEALS,

		/**
		 * A [map][MapDescriptor] from the [textual&#32;names][StringDescriptor]
		 * of entry point [methods][MethodDescriptor] to their
		 * [true&#32;names][AtomDescriptor].
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
			e === ALL_ANCESTORS
				|| e === VERSIONS
				|| e === NEW_NAMES
				|| e === IMPORTED_NAMES
				|| e === PRIVATE_NAMES
				|| e === EXPORTED_NAMES
				|| e === VISIBLE_NAMES
				|| e === METHOD_DEFINITIONS_SET
				|| e === GRAMMATICAL_RESTRICTIONS
				|| e === VARIABLE_BINDINGS
				|| e === CONSTANT_BINDINGS
				|| e === SEMANTIC_RESTRICTIONS
				|| e === SEALS
				|| e === ENTRY_POINTS
				|| e === UNLOAD_FUNCTIONS
				|| e === LEXERS

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("Module: ")
		builder.append(self.moduleName())
	}

	override fun o_NameForDebugger(self: AvailObject): String =
		(super.o_NameForDebugger(self) + " = "
			+ self.moduleName().asNativeString())

	override fun o_ModuleName(self: AvailObject): A_String =
		self.slot(NAME)

	override fun o_Versions(self: AvailObject): A_Set =
		synchronized(self) { return self.slot(VERSIONS) }

	override fun o_SetVersions(self: AvailObject, versionStrings: A_Set)
	{
		synchronized(self) {
			self.setSlot(VERSIONS, versionStrings.traversed().makeShared())
		}
	}

	override fun o_NewNames(self: AvailObject): A_Map =
		synchronized(self) { return self.slot(NEW_NAMES) }

	override fun o_ImportedNames(self: AvailObject): A_Map =
		synchronized(self) {
			return self.slot(IMPORTED_NAMES)
		}

	override fun o_PrivateNames(self: AvailObject): A_Map =
		synchronized(self) {
			return self.slot(PRIVATE_NAMES)
		}

	override fun o_EntryPoints(self: AvailObject): A_Map =
		synchronized(self) {
			return self.slot(ENTRY_POINTS)
		}

	override fun o_VisibleNames(self: AvailObject): A_Set =
		synchronized(self) {
			return self.slot(VISIBLE_NAMES)
		}

	override fun o_ExportedNames(self: AvailObject): A_Set
	{
		var exportedNames = emptySet()
		synchronized(self) {
			for ((_, value) in
				self.slot(IMPORTED_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setUnionCanDestroy(
					value.makeShared(), true)
			}
			for ((_, value) in
				self.slot(PRIVATE_NAMES).mapIterable())
			{
				exportedNames = exportedNames.setMinusCanDestroy(
					value.makeShared(), true)
			}
		}
		return exportedNames
	}

	override fun o_MethodDefinitions(self: AvailObject): A_Set =
		synchronized(self) {
			return self.slot(METHOD_DEFINITIONS_SET)
		}

	override fun o_VariableBindings(self: AvailObject): A_Map =
		synchronized(self) {
			return self.slot(VARIABLE_BINDINGS)
		}

	override fun o_ConstantBindings(self: AvailObject): A_Map =
		synchronized(self) {
			return self.slot(CONSTANT_BINDINGS)
		}

	override fun o_AddConstantBinding(
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable)
	{
		synchronized(self) {
			assert(constantBinding.kind().isSubtypeOf(
				mostGeneralVariableType()))
			var constantBindings: A_Map =
				self.slot(CONSTANT_BINDINGS)
			constantBindings = constantBindings.mapAtPuttingCanDestroy(
				name, constantBinding, true)
			self.setSlot(CONSTANT_BINDINGS, constantBindings.makeShared())
		}
	}

	override fun o_ModuleAddDefinition(
		self: AvailObject, definition: A_Definition)
	{
		synchronized(self) {
			var methods: A_Set = self.slot(METHOD_DEFINITIONS_SET)
			methods = methods.setWithElementCanDestroy(definition, false)
			self.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared())
		}
	}

	override fun o_AddSeal(
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple)
	{
		synchronized(self) {
			var seals: A_Map = self.slot(SEALS)
			var tuple: A_Tuple
			tuple = when {
				seals.hasKey(methodName) -> seals.mapAt(methodName)
				else -> emptyTuple()
			}
			tuple = tuple.appendCanDestroy(argumentTypes, true)
			seals = seals.mapAtPuttingCanDestroy(methodName, tuple, true)
			self.setSlot(SEALS, seals.makeShared())
		}
	}

	override fun o_ModuleAddSemanticRestriction(
		self: AvailObject,
		semanticRestriction: A_SemanticRestriction)
	{
		synchronized(self) {
			var restrictions: A_Set = self.slot(SEMANTIC_RESTRICTIONS)
			restrictions = restrictions.setWithElementCanDestroy(
				semanticRestriction, true)
			restrictions = restrictions.makeShared()
			self.setSlot(SEMANTIC_RESTRICTIONS, restrictions)
		}
	}

	override fun o_AddVariableBinding(
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable)
	{
		synchronized(self) {
			assert(variableBinding.kind().isSubtypeOf(
				mostGeneralVariableType()))
			var variableBindings: A_Map = self.slot(VARIABLE_BINDINGS)
			variableBindings = variableBindings.mapAtPuttingCanDestroy(
				name, variableBinding, true)
			self.setSlot(VARIABLE_BINDINGS, variableBindings.makeShared())
		}
	}

	override fun o_AddImportedName( self: AvailObject, trueName: A_Atom)
	{
		// Add the atom to the current public scope.
		synchronized(self) {
			val string: A_String = trueName.atomName()
			var importedNames: A_Map = self.slot(IMPORTED_NAMES)
			importedNames = importedNames.mapAtReplacingCanDestroy(
				string,
				emptySet(),
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
			self.setSlot(
				IMPORTED_NAMES, importedNames.makeShared())
			var privateNames: A_Map = self.slot(PRIVATE_NAMES)
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
				self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
			}
			var exportedNames: A_Set = self.slot(EXPORTED_NAMES)
			exportedNames =
				exportedNames.setWithElementCanDestroy(trueName, true)
			self.setSlot(EXPORTED_NAMES, exportedNames.makeShared())
			var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
			self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	override fun o_AddImportedNames(self: AvailObject, trueNames: A_Set)
	{
		// Add the set of atoms to the current public scope.
		synchronized(self) {
			var importedNames: A_Map = self.slot(IMPORTED_NAMES)
			var privateNames: A_Map = self.slot(PRIVATE_NAMES)
			for (trueName in trueNames)
			{
				val string: A_String = trueName.atomName()
				importedNames = importedNames.mapAtReplacingCanDestroy(
					string,
					emptySet(),
					{ _, set: A_Set ->
						set.setWithElementCanDestroy(trueName, true)
					},
					true)
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
				}
			}
			self.setSlot(IMPORTED_NAMES, importedNames.makeShared())
			self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
			var exportedNames: A_Set = self.slot(EXPORTED_NAMES)
			exportedNames = exportedNames.setUnionCanDestroy(trueNames, true)
			self.setSlot(EXPORTED_NAMES, exportedNames.makeShared())
			var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
			visibleNames = visibleNames.setUnionCanDestroy(trueNames, true)
			self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	override fun o_IntroduceNewName(self: AvailObject, trueName: A_Atom)
	{
		// Set up this true name, which is local to the module.
		synchronized(self) {
			val string: A_String = trueName.atomName()
			var newNames: A_Map = self.slot(NEW_NAMES)
			assert(!newNames.hasKey(string)) {
				"Can't define a new true name twice in a module"
			}
			newNames = newNames.mapAtPuttingCanDestroy(string, trueName, true)
			self.setSlot(NEW_NAMES, newNames.makeShared())
			var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
			self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
		}
	}

	override fun o_AddPrivateName(self: AvailObject, trueName: A_Atom)
	{
		// Add the atom to the current private scope.
		synchronized(self) {
			val string: A_String = trueName.atomName()
			var privateNames: A_Map = self.slot(PRIVATE_NAMES)
			privateNames = privateNames.mapAtReplacingCanDestroy(
				string,
				emptySet(),
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
			self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
			var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
			self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
			val importedNames: A_Map = self.slot(IMPORTED_NAMES)
			if (!importedNames.hasKey(string)
				|| !importedNames.mapAt(string).hasElement(trueName))
			{
				var exportedNames: A_Set = self.slot(EXPORTED_NAMES)
				exportedNames = exportedNames.setWithoutElementCanDestroy(
					trueName, true)
				self.setSlot(EXPORTED_NAMES, exportedNames.makeShared())
			}
		}
	}

	override fun o_AddPrivateNames(self: AvailObject, trueNames: A_Set)
	{
		// Add the set of atoms to the current private scope.
		synchronized(self) {
			var privateNames: A_Map = self.slot(PRIVATE_NAMES)
			var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
			var exportedNames: A_Set = self.slot(EXPORTED_NAMES)
			val importedNames: A_Map = self.slot(IMPORTED_NAMES)
			for (trueName in trueNames)
			{
				val string: A_String = trueName.atomName()
				privateNames = privateNames.mapAtReplacingCanDestroy(
					string,
					emptySet(),
					{ _, set: A_Set ->
						set.setWithElementCanDestroy(trueName, true)
					},
					true)
				visibleNames = visibleNames.setWithElementCanDestroy(
					trueName, true)
				if (!importedNames.hasKey(string)
					|| !importedNames.mapAt(string).hasElement(trueName))
				{
					exportedNames = exportedNames.setWithoutElementCanDestroy(
						trueName, true)
				}
			}
			self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
			self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
			self.setSlot(EXPORTED_NAMES, exportedNames.makeShared())
		}
	}

	override fun o_AddEntryPoint(
		self: AvailObject,
		stringName: A_String,
		trueName: A_Atom)
	{
		synchronized(self) {
			var entryPoints: A_Map = self.slot(ENTRY_POINTS)
			entryPoints = entryPoints.mapAtPuttingCanDestroy(
				stringName,
				trueName,
				true)
			self.setSlot(ENTRY_POINTS, entryPoints.traversed().makeShared())
		}
	}

	override fun o_AddLexer(self: AvailObject, lexer: A_Lexer)
	{
		synchronized(self) {
			var lexers: A_Set = self.slot(LEXERS)
			lexers = lexers.setWithElementCanDestroy(lexer, false)
			self.setSlot(LEXERS, lexers.makeShared())
		}
	}

	override fun o_AddUnloadFunction(
		self: AvailObject,
		unloadFunction: A_Function)
	{
		synchronized(self) {
			var unloadFunctions: A_Tuple = self.slot(UNLOAD_FUNCTIONS)
			unloadFunctions = unloadFunctions.appendCanDestroy(
				unloadFunction, true)
			self.setSlot(UNLOAD_FUNCTIONS, unloadFunctions.makeShared())
		}
	}

	override fun o_Equals(
		self: AvailObject, another: A_BasicObject): Boolean =
			// Compare by address (identity).
			another.traversed().sameAddressAs(self)

	override fun o_Hash(self: AvailObject): Int =
		self.slot(NAME).hash() * 173 xor -0x20c7c074

	override fun o_Kind(self: AvailObject): A_Type = Types.MODULE.o()

	// Modules are always shared, never immutable.
	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		when {
			isMutable -> self.makeShared()
			else -> self
		}

	override fun o_ModuleAddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction)
	{
		synchronized(self) {
			var grammaticalRestrictions: A_Set =
				self.slot(GRAMMATICAL_RESTRICTIONS)
			grammaticalRestrictions =
				grammaticalRestrictions.setWithElementCanDestroy(
					grammaticalRestriction, true)
			self.setSlot(
				GRAMMATICAL_RESTRICTIONS, grammaticalRestrictions.makeShared())
		}
	}

	override fun o_ModuleGrammaticalRestrictions(self: AvailObject): A_Set =
		self.slot(GRAMMATICAL_RESTRICTIONS)

	override fun o_ModuleSemanticRestrictions(self: AvailObject): A_Set =
		self.slot(SEMANTIC_RESTRICTIONS)

	override fun o_NameVisible(self: AvailObject, trueName: A_Atom): Boolean =
		// Check if the given trueName is visible in this module.
		self.visibleNames().hasElement(trueName)

	override fun o_RemoveFrom(
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit)
	{
		synchronized(self) {
			val unloadFunctions = self.slot(UNLOAD_FUNCTIONS).tupleReverse()
			self.setSlot(UNLOAD_FUNCTIONS, nil)
			// Run unload functions, asynchronously but serially, in reverse
			// order.
			loader.runUnloadFunctions(unloadFunctions) {
				finishUnloading(self, loader)
				afterRemoval()
			}
		}
	}

	/**
	 * Now that the unload functions have completed, perform any other unloading
	 * actions necessary for this module.
	 *
	 * @param self
	 *   The module being unloaded.
	 * @param loader
	 *   The [AvailLoader] through which the module is being unloaded.
	 */
	@Synchronized
	private fun finishUnloading(self: AvailObject, loader: AvailLoader)
	{
		val runtime = loader.runtime()
		// Remove method definitions.
		for (definition in self.methodDefinitions())
		{
			loader.removeDefinition(definition)
		}
		// Remove semantic restrictions.
		for (restriction in self.moduleSemanticRestrictions())
		{
			runtime.removeTypeRestriction(restriction)
		}
		for (restriction in self.moduleGrammaticalRestrictions())
		{
			runtime.removeGrammaticalRestriction(restriction)
		}
		// Remove seals.
		val seals: A_Map = self.slot(SEALS)
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
		for (lexer in self.slot(LEXERS))
		{
			lexer.lexerMethod().setLexer(nil)
		}
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param self
	 *   The module.
	 * @param forwardDefinition
	 *   The [forward&#32;declaration][ForwardDefinitionDescriptor] to be
	 *   removed.
	 */
	override fun o_ResolveForward(
		self: AvailObject,
		forwardDefinition: A_BasicObject)
	{
		synchronized(self) {
			assert(forwardDefinition.isInstanceOfKind(
				Types.FORWARD_DEFINITION.o()))
			var methods: A_Set = self.slot(METHOD_DEFINITIONS_SET)
			assert(methods.hasElement(forwardDefinition))
			methods = methods.setWithoutElementCanDestroy(
				forwardDefinition, false)
			self.setSlot(METHOD_DEFINITIONS_SET, methods.makeShared())
		}
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	/**
	 * Check what true names are visible in this module under the given string
	 * name.
	 *
	 * @param self
	 *   The module.
	 * @param stringName
	 *   A string whose corresponding [true&#32;names][AtomDescriptor] are to be
	 *   looked up in this module.
	 * @return
	 *   The [set][SetDescriptor] of [true&#32;names][AtomDescriptor] that have
	 *   the given stringName and are visible in this module.
	 */
	override fun o_TrueNamesForStringName(
		self: AvailObject, stringName: A_String): A_Set =
		synchronized(self) {
			assert(stringName.isTuple)
			if (self.slot(NEW_NAMES).hasKey(stringName))
			{
				return emptySet().setWithElementCanDestroy(
					self.slot(NEW_NAMES).mapAt(stringName),
					false)
			}
			val publicNames: A_Set =
				if (self.slot(IMPORTED_NAMES).hasKey(stringName))
				{
					self.slot(IMPORTED_NAMES).mapAt(stringName)
				}
				else
				{
					emptySet()
				}
			if (!self.slot(PRIVATE_NAMES).hasKey(stringName))
			{
				return publicNames
			}
			val privates: A_Set = self.slot(PRIVATE_NAMES).mapAt(stringName)
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
	 * Create a [bundle&#32;tree][MessageBundleTreeDescriptor] to have the
	 * [message&#32;bundles][MessageBundleDescriptor] that are visible in the
	 * current module.
	 *
	 * @param self
	 *   The module.
	 */
	override fun o_BuildFilteredBundleTree(self: AvailObject): A_BundleTree
	{
		val filteredBundleTree = newBundleTree(nil)
		synchronized(self) {
			val ancestors: A_Set = self.slot(ALL_ANCESTORS)
			for (visibleName in self.visibleNames())
			{
				val bundle: A_Bundle = visibleName.bundleOrNil()
				if (!bundle.equalsNil())
				{
					for ((key, plan) in
						bundle.definitionParsingPlans().mapIterable())
					{
						if (ancestors.hasElement(key.definitionModule()))
						{
							val planInProgress = newPlanInProgress(plan, 1)
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
	 * @param self
	 *   The module.
	 */
	override fun o_CreateLexicalScanner(self: AvailObject): LexicalScanner
	{
		val lexers: MutableSet<A_Lexer> = HashSet()
		synchronized(self) {
			for (visibleName in self.visibleNames())
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
		lexers.forEach(lexicalScanner::addLexer)
		return lexicalScanner
	}

	override fun o_AllAncestors(self: AvailObject): A_Set =
		synchronized(self) {
			self.slot(ALL_ANCESTORS)
		}

	override fun o_AddAncestors(self: AvailObject, moreAncestors: A_Set)
	{
		synchronized(self) {
			val union =
				self.slot(ALL_ANCESTORS).setUnionCanDestroy(moreAncestors, true)
			self.setSlot(ALL_ANCESTORS, union.makeShared())
		}
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MODULE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("module") }
			at("name") { self.slot(NAME).writeTo(writer) }
			at("versions") { self.slot(VERSIONS).writeTo(writer) }
			at("entry points") { self.entryPoints().writeTo(writer) }
		}

	override fun mutable() = mutable

	// There is no immutable descriptor. Use the shared one.
	override fun immutable() = shared

	override fun shared() = shared

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
			module.setSlot(NAME, moduleName)
			module.setSlot(ALL_ANCESTORS, nil)
			module.setSlot(VERSIONS, emptySet())
			module.setSlot(NEW_NAMES, emptyMap())
			module.setSlot(IMPORTED_NAMES, emptyMap())
			module.setSlot(PRIVATE_NAMES, emptyMap())
			module.setSlot(VISIBLE_NAMES, emptySet())
			module.setSlot(EXPORTED_NAMES, emptySet())
			module.setSlot(METHOD_DEFINITIONS_SET, emptySet())
			module.setSlot(GRAMMATICAL_RESTRICTIONS, emptySet())
			module.setSlot(VARIABLE_BINDINGS, emptyMap())
			module.setSlot(CONSTANT_BINDINGS, emptyMap())
			module.setSlot(VARIABLE_BINDINGS, emptyMap())
			module.setSlot(SEMANTIC_RESTRICTIONS, emptySet())
			module.setSlot(SEALS, emptyMap())
			module.setSlot(ENTRY_POINTS, emptyMap())
			module.setSlot(UNLOAD_FUNCTIONS, emptyTuple())
			module.setSlot(LEXERS, emptySet())
			// Adding the module to its ancestors set will cause recursive scanning
			// to mark everything as shared, so it's essential that all fields have
			// been initialized to *something* by now.
			module.makeShared()
			module.setSlot(ALL_ANCESTORS, set(module).makeShared())
			return module
		}

		/** The mutable [ModuleDescriptor].  */
		private val mutable = ModuleDescriptor(Mutability.MUTABLE)

		/** The shared [ModuleDescriptor].  */
		private val shared = ModuleDescriptor(Mutability.SHARED)

		/**
		 * Answer the `ModuleDescriptor module` currently undergoing
		 * [loading][AvailLoader] on the
		 * [current&#32;fiber][FiberDescriptor.currentFiber].
		 *
		 * @return
		 *   The module currently undergoing loading, or
		 *   [nil][NilDescriptor.nil] if the current fiber is not a loader
		 *   fiber.
		 */
		fun currentModule(): A_Module
		{
			val fiber = FiberDescriptor.currentFiber()
			val loader = fiber.availLoader() ?: return nil
			return loader.module()
		}
	}
}
