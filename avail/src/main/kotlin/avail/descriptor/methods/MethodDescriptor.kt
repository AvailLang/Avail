/*
 * MethodDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.methods

import avail.AvailRuntimeSupport
import avail.annotations.HideFieldInDebugger
import avail.annotations.HideFieldJustForPrinting
import avail.annotations.ThreadSafe
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.createSpecialAtom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.addDefinitionParsingPlan
import avail.descriptor.bundles.A_Bundle.Companion.bundleAddMacro
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.removePlanForSendable
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.chooseBundle
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.membershipChanged
import avail.descriptor.methods.A_Method.Companion.methodAddDefinition
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.definitionModule
import avail.descriptor.methods.MacroDescriptor.Companion.newMacroDefinition
import avail.descriptor.methods.MethodDefinitionDescriptor.Companion.newMethodDefinition
import avail.descriptor.methods.MethodDescriptor.Companion.initialMutableDescriptor
import avail.descriptor.methods.MethodDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.methods.MethodDescriptor.IntegerSlots.Companion.NUM_ARGS
import avail.descriptor.methods.MethodDescriptor.ObjectSlots.DEFINITIONS_TUPLE
import avail.descriptor.methods.MethodDescriptor.ObjectSlots.LEXER_OR_NIL
import avail.descriptor.methods.MethodDescriptor.ObjectSlots.SEALED_ARGUMENTS_TYPES_TUPLE
import avail.descriptor.methods.MethodDescriptor.ObjectSlots.SEMANTIC_RESTRICTIONS_SET
import avail.descriptor.methods.MethodDescriptor.ObjectSlots.STYLERS
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.hasAncestor
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.DefinitionParsingPlanDescriptor.Companion.newParsingPlan
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleWithout
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD
import avail.descriptor.types.TupleTypeDescriptor
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.TypeTag
import avail.descriptor.variables.A_Variable
import avail.dispatch.LeafLookupTree
import avail.dispatch.LookupStatistics
import avail.dispatch.LookupTree
import avail.dispatch.LookupTreeAdaptor
import avail.dispatch.TypeComparison.Companion.compareForDispatch
import avail.exceptions.AvailErrorCode.E_METHOD_IS_SEALED
import avail.exceptions.MalformedMessageException
import avail.exceptions.MethodDefinitionException
import avail.exceptions.MethodDefinitionException.Companion.extractUniqueMethod
import avail.exceptions.SignatureException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.PrimitiveHolder.Companion.holdersByClassName
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Chunk.InvalidationReason.DEPENDENCY_CHANGED
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.anyRestriction
import avail.interpreter.primitive.atoms.P_AtomRemoveProperty
import avail.interpreter.primitive.atoms.P_AtomSetProperty
import avail.interpreter.primitive.atoms.P_CreateAtom
import avail.interpreter.primitive.atoms.P_CreateExplicitSubclassAtom
import avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckImportVersion
import avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckModuleName
import avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPrefixCheckModuleVersion
import avail.interpreter.primitive.bootstrap.syntax.P_ModuleHeaderPseudoMacro
import avail.interpreter.primitive.continuations.P_ContinuationCaller
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.controlflow.P_ResumeContinuation
import avail.interpreter.primitive.fibers.P_CreateFiberHeritableAtom
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.hooks.P_DeclareStringificationAtom
import avail.interpreter.primitive.hooks.P_GetRaiseJavaExceptionInAvailFunction
import avail.interpreter.primitive.methods.P_AbstractMethodDeclarationForAtom
import avail.interpreter.primitive.methods.P_AddSemanticRestrictionForAtom
import avail.interpreter.primitive.methods.P_Alias
import avail.interpreter.primitive.methods.P_ForwardMethodDeclarationForAtom
import avail.interpreter.primitive.methods.P_GrammaticalRestrictionFromAtoms
import avail.interpreter.primitive.methods.P_MethodDeclarationFromAtom
import avail.interpreter.primitive.methods.P_SealMethodByAtom
import avail.interpreter.primitive.methods.P_SimpleLexerDefinitionForAtom
import avail.interpreter.primitive.methods.P_SimpleMacroDeclaration
import avail.interpreter.primitive.methods.P_SimpleMacroDefinitionForAtom
import avail.interpreter.primitive.methods.P_SimpleMethodDeclaration
import avail.interpreter.primitive.modules.P_AddUnloadFunction
import avail.interpreter.primitive.modules.P_DeclareAllAtomsExportedFromAnotherModule
import avail.interpreter.primitive.modules.P_DeclareAllExportedAtoms
import avail.interpreter.primitive.modules.P_PrivateCreateModuleVariable
import avail.interpreter.primitive.modules.P_PublishName
import avail.interpreter.primitive.objects.P_RecordNewTypeName
import avail.interpreter.primitive.phrases.P_CreateLiteralExpression
import avail.interpreter.primitive.phrases.P_CreateLiteralToken
import avail.interpreter.primitive.rawfunctions.P_SetCompiledCodeName
import avail.interpreter.primitive.style.P_SetStylerFunction
import avail.interpreter.primitive.variables.P_AtomicAddToMap
import avail.interpreter.primitive.variables.P_AtomicRemoveFromMap
import avail.interpreter.primitive.variables.P_GetValue
import avail.optimizer.L2Generator
import avail.performance.Statistic
import avail.performance.StatisticReport.DYNAMIC_LOOKUP
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.Collections.emptyList
import java.util.Collections.nCopies
import java.util.Collections.newSetFromMap
import java.util.Collections.synchronizedSet
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater
import kotlin.concurrent.withLock

/**
 * A method maintains all [A_Definition]s that have the same name.  At compile
 * time a name is looked up and the corresponding method is stored as a literal
 * in the object code for a call site.  At runtime, the actual function is
 * located within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership (e.g., adding a
 * method definition) causes an immediate invalidation of optimized level two
 * code that depends on the previous membership.
 *
 * Methods and macros are stored in separate tuples.  Note that macros may be
 * polymorphic (multiple [definitions][MacroDescriptor]), and a lookup
 * structure is used at compile time to decide which macro is most specific.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the resulting descriptor.  This should only be
 *   [Mutability.MUTABLE] for the [initialMutableDescriptor], and
 *   [Mutability.SHARED] for normal instances.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MethodDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.METHOD_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java)
{
	/**
	 * A [set][SetDescriptor] of [message&#32;bundles][MessageBundleDescriptor]
	 * that name this method. The method itself has no intrinsic name, as its
	 * bundles completely determine what it is called in various modules (based
	 * on the module scope of the bundles' [atomic&#32;names][AtomDescriptor]).
	 */
	@Volatile
	var owningBundles = AtomicReference(emptySet)

	/**
	 * A [LookupTree] used to determine the most specific method definition that
	 * satisfies the supplied argument types.  A `null` indicates the tree has
	 * not yet been constructed.
	 */
	@Volatile
	private var methodTestingTree: LookupTree<A_Definition, A_Tuple>? = null

	/**
	 * A [Pair] of [Statistic]s that track dynamic lookups, involving type
	 * testing within a [LookupTree].  By the time the first one occurs, a
	 * bundle will have been set.  The first tracks by time, and the second by
	 * depth.
	 */
	@Volatile
	private var dynamicLookupStats: LookupStatistics? = null

	/**
	 * Answer the [LookupStatistics] that tracks dynamic lookups, involving
	 * type testing within a [LookupTree].  By the time the first one occurs, a
	 * bundle will have been set.
	 */
	fun dynamicLookupStats(): LookupStatistics
	{
		dynamicLookupStats?.let { return it }
		return synchronized(this)
		{
			// Double-check the volatile field.
			dynamicLookupStats?.let { return it }
			val bundles = owningBundles.get()
			val name = when (bundles.setSize)
			{
				0 -> "(no name)"
				1 -> bundles.single().message.toString()
				else -> bundles.first().toString() + " & aliases"
			}
			val stat = LookupStatistics(name, DYNAMIC_LOOKUP)
			dynamicLookupStats = stat
			stat
		}
	}


	/**
	 * A weak set (implemented as the [key&#32;set][Map.keys] of a
	 * [WeakHashMap]) of [L2Chunk]s that depend on the membership of this
	 * method.  A change to the membership will invalidate all such chunks.
	 * This field is initially `null`.
	 */
	private var dependentChunksWeakSet: MutableSet<L2Chunk>? = null

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * [BitField]s for the hash and the argument count.  See below.
		 */
		@HideFieldInDebugger
		HASH_AND_NUM_ARGS;

		companion object {
			/**
			 * The hash of this method.  It's set to a random number during
			 * construction.
			 */
			val HASH = BitField(HASH_AND_NUM_ARGS, 0, 32) { null }

			/**
			 * The number of arguments expected by this method.  Set at
			 * construction time.
			 */
			val NUM_ARGS = BitField(HASH_AND_NUM_ARGS, 32, 32, Int::toString)
		}
	}

	/**
	 * The fields that are of type `AvailObject`.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [tuple][TupleDescriptor] of [definitions][DefinitionDescriptor]
		 * that constitute this multimethod.  This field should only be read and
		 * written with volatile slot semantics.
		 */
		DEFINITIONS_TUPLE,

		/**
		 * A [set][SetDescriptor] of
		 * [semantic&#32;restrictions][SemanticRestrictionDescriptor] which,
		 * when their functions are invoked with suitable
		 * [types][TypeDescriptor] as arguments, will determine whether the call
		 * arguments have mutually compatible types, and if so produce a type to
		 * which the call's return value is expected to conform.  This type
		 * strengthening is *assumed* to hold at compile time (of the call) and
		 * *checked* at runtime.
		 *
		 * When the [L2Generator] inlines a [Primitive] method definition, it
		 * asks the primitive what type it guarantees
		 * ([Primitive.returnTypeGuaranteedByVM]) to return for the specific
		 * provided argument types.  If that return type is sufficiently strong,
		 * the above runtime check may be waived.
		 */
		SEMANTIC_RESTRICTIONS_SET,

		/**
		 * A [tuple][TupleDescriptor] of [tuple&#32;types][TupleTypeDescriptor]
		 * below which new signatures may no longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * The method's [lexer][A_Lexer] or [nil].
		 */
		LEXER_OR_NIL,

		/**
		 * The [A_Set] of [A_Styler]s that have been added to this method. At
		 * most one may be added to each method per module.
		 *
		 * Styling only happens when a top-level statement of a module has been
		 * unambiguously compiled.  Child phrases are processed before their
		 * parent.
		 *
		 * The function accepts four arguments:
		 *   1. The send phrase, which is possibly the original phrase of a
		 *      macro substitution.
		 *   2. An [A_Variable] containing an [A_Map] from [A_Phrase] to a
		 *      style, and
		 *   3. An [A_Variable] containing a map from [A_Token] to style.
		 *   3. An [A_Variable] containing a map from [A_Token] to [A_Token],
		 *      where the key token is the use of some variable, and the value
		 *      token identifies its points of declaration.  While not strictly
		 *      a styling output, we take this opportunity nevertheless to
		 *      introduce this local navigation helper.
		 *
		 * The maps will already have been populated by running the styling
		 * function for each of the children (perhaps running all children in
		 * parallel).  These variables should be updated to reflect how the
		 * invocation of this bundle should be presented.
		 *
		 * These maps will later be used to produce a linear sequence of styled
		 * substrings, suitable for passing to an IDE or other technology
		 * capable of presenting styled text.
		 *
		 * The linearization of the fully populated style information proceeds
		 * by scanning the map from phrase to style, and for each phrase adding
		 * an entry to the map from token to style for each of the phrase's
		 * static tokens.  If there is already an entry for some static token,
		 * the map is not updated for that token.  Afterward, the token map is
		 * sorted, and used to partition the module text.
		 *
		 * The resulting tuple of ranges and styles (perhaps with line numbers)
		 * might even be accessed with a binary search, to deliver a windowed
		 * view into the styled text.  This allows enormous files to be
		 * presented in an IDE without having to transfer and decode all of the
		 * styling information for the entire file.
		 */
		@HideFieldJustForPrinting
		STYLERS
	}

	/**
	 * Extract the current [methodTestingTree], creating one atomically, if
	 * necessary.
	 *
	 * @param self
	 *   The [A_Method] for which to answer the [methodTestingTree].
	 * @return
	 *   The [LookupTree] for looking up method definitions.
	 */
	private fun methodTestingTree(
		self: AvailObject
	): LookupTree<A_Definition, A_Tuple> {
		var tree = methodTestingTree
		if (tree === null) {
			val numArgs = self.slot(NUM_ARGS)
			val newTree = runtimeDispatcher.createRoot(
				toList(self.volatileSlot(DEFINITIONS_TUPLE)),
				nCopiesOfAnyRestriction(numArgs),
				Unit)
			do
			{
				// Try to replace null with the new tree.  If the replacement
				// fails, it means someone else already succeeded, so use that
				// winner's tree.
				methodTestingTreeUpdater.compareAndSet(this, null, newTree)
				tree = methodTestingTree
			}
			while (tree === null)
		}
		return tree
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === DEFINITIONS_TUPLE
		|| e === SEMANTIC_RESTRICTIONS_SET
		|| e === SEALED_ARGUMENTS_TYPES_TUPLE
		|| e === LEXER_OR_NIL
		|| e === STYLERS

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.run {
			when (val size = self.definitionsTuple.tupleSize)
			{
				1 -> append("1 definition")
				else -> append("$size definitions")
			}
			append(" of ")
			self.bundles.joinTo(this, " a.k.a. ") { it.message.toString() }
		}
	}

	override fun o_AddDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) {
		// The set of dependents is only ever accessed within the monitor.
		synchronized(self) {
			var set = dependentChunksWeakSet
			if (set === null) {
				set = synchronizedSet(newSetFromMap(mutableMapOf()))
				dependentChunksWeakSet = set
			}
			set!!.add(chunk)
		}
	}

	override fun o_AddSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = synchronized(self) {
		assert(typeTuple.isTuple)
		val oldTuple: A_Tuple = self.slot(SEALED_ARGUMENTS_TYPES_TUPLE)
		val newTuple = oldTuple.appendCanDestroy(typeTuple, true)
		self.setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, newTuple.makeShared())
	}

	override fun o_AddSemanticRestriction(
		self: AvailObject,
		restriction: A_SemanticRestriction
	) = synchronized(self) {
		var set: A_Set = self.slot(SEMANTIC_RESTRICTIONS_SET)
		set = set.setWithElementCanDestroy(restriction, true)
		self.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared())
	}

	override fun o_Bundles(self: AvailObject): A_Set = owningBundles.get()

	override fun o_ChooseBundle(
		self: AvailObject,
		currentModule: A_Module
	): A_Bundle {
		val bundles: A_Set = owningBundles.get()
		return bundles.find {
			currentModule.hasAncestor(it.message.issuingModule)
		} ?: bundles.first() // Fall back to any bundle.
	}

	/**
	 * Look up all method definitions that could match arguments satisfying the
	 * given [TypeRestriction]s.  This should return the definitions that could
	 * be invoked at runtime at a call site with the given restrictions. This
	 * set is subject to change as new methods and types are created.  If a
	 * restriction and the corresponding argument type of a definition have no
	 * possible intersection except [bottom] (⊥), then disallow the definition
	 * (it could never actually be invoked because bottom is uninstantiable).
	 * Answer a [list][List] of
	 * [method&#32;definitions][MethodDefinitionDescriptor].
	 *
	 * Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.
	 */
	override fun o_DefinitionsAtOrBelow(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>
	): List<A_Definition> =
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		self.definitionsTuple.filter {
			it.bodySignature().couldEverBeInvokedWith(argRestrictions)
		}

	override fun o_MethodStylers(self: AvailObject): A_Set =
		self.volatileSlot(STYLERS)

	override fun o_DefinitionsTuple(self: AvailObject): A_Tuple =
		self.volatileSlot(DEFINITIONS_TUPLE)

	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		val fields = super.o_DescribeForDebugger(self).toMutableList()
		fields.add(
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				-1,
				owningBundles,
				slotName = "owningBundles"))
		fields.add(
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				-1,
				arrayOf(methodTestingTree),
				slotName = "methodTestingTree"))
		dependentChunksWeakSet?.let {
			fields.add(
				AvailObjectFieldHelper(
					self,
					DUMMY_DEBUGGER_SLOT,
					-1,
					it.toTypedArray(),
					slotName = "dependentChunks"))
		}
		return fields.toTypedArray()
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.traversed().sameAddressAs(self)

	/**
	 * Look up all method definitions that could match the given argument types.
	 * Answer a [list][List] of
	 * [method&#32;definitions][MethodDefinitionDescriptor].
	 *
	 * Uses the [A_Method.definitionsTuple] accessor instead of reading the slot
	 * directly, to acquire the monitor first.
	 */
	override fun o_FilterByTypes(
		self: AvailObject,
		argTypes: List<A_Type>
	): List<A_Definition> =
		self.definitionsTuple.filter {
			it.bodySignature().acceptsListOfArgTypes(argTypes)
		}

	override fun o_Hash(self: AvailObject) = self.slot(HASH)

	/**
	 * Test if the definition is present within this method.
	 *
	 * Uses the [A_Method.definitionsTuple] accessor instead of reading the slot
	 * directly, to acquire the monitor first.
	 */
	override fun o_IncludesDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self.definitionsTuple.contains(definition)

	override fun o_IsMethodEmpty(self: AvailObject) = synchronized(self) {
		self.volatileSlot(DEFINITIONS_TUPLE).tupleSize == 0
			&& self.slot(SEMANTIC_RESTRICTIONS_SET).setSize == 0
			&& self.slot(SEALED_ARGUMENTS_TYPES_TUPLE).tupleSize == 0
			&& owningBundles.get().all { it.macrosTuple.tupleSize == 0 }
	}

	override fun o_Kind(self: AvailObject): A_Type = METHOD.o

	override fun o_Lexer(self: AvailObject): A_Lexer =
		synchronized(self) { self.slot(LEXER_OR_NIL) }

	/**
	 * Look up the definition to invoke, given a tuple of argument types.
	 * Use the [methodTestingTree] to find the definition to invoke.
	 */
	@Throws(MethodDefinitionException::class)
	override fun o_LookupByTypesFromTuple(
		self: AvailObject,
		argumentTypeTuple: A_Tuple
	) = extractUniqueMethod(
		runtimeDispatcher.lookupByTypes(
			methodTestingTree(self),
			argumentTypeTuple,
			Unit,
			dynamicLookupStats()))

	/**
	 * Look up the definition to invoke, given a [List] of argument values. Use
	 * the [methodTestingTree] to find the definition to invoke.  Answer
	 * [nil] if a lookup error occurs.
	 */
	@Throws(MethodDefinitionException::class)
	override fun o_LookupByValuesFromList(
		self: AvailObject,
		argumentList: List<A_BasicObject>
	) = extractUniqueMethod(
		runtimeDispatcher.lookupByValues(
			methodTestingTree(self), argumentList, Unit, dynamicLookupStats()))

	override fun o_MethodAddBundle(
		self: AvailObject,
		bundle: A_Bundle)
	{
		owningBundles.updateAndGet {
			it.setWithElementCanDestroy(bundle, false).makeShared()
		}
	}

	override fun o_MethodRemoveBundle(
		self: AvailObject,
		bundle: A_Bundle)
	{
		owningBundles.updateAndGet {
			it.setWithoutElementCanDestroy(bundle, false).makeShared()
		}
	}

	/**
	 * Method manipulation takes place while all fibers are L1-precise and
	 * suspended.  Use a global lock at the outermost calls to side-step
	 * deadlocks.  Because no fiber is running, we don't have to protect
	 * subsystems like the L2Generator from these changes.
	 *
	 * Also create definition parsing plans for each bundle.  HOWEVER, note that
	 * we don't update the current module's message bundle tree here, and leave
	 * that to the caller to deal with.  Other modules' parsing should be
	 * unaffected, although runtime execution may change.
	 */
	@Throws(SignatureException::class)
	override fun o_MethodAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = L2Chunk.invalidationLock.withLock {
		val paramTypes = definition.bodySignature().argsTupleType
		val seals: A_Tuple = self.slot(SEALED_ARGUMENTS_TYPES_TUPLE)
		seals.forEach { seal: A_Tuple ->
			val sealType = tupleTypeForSizesTypesDefaultType(
				singleInt(seal.tupleSize), seal, bottom)
			if (paramTypes.isSubtypeOf(sealType)) {
				throw SignatureException(E_METHOD_IS_SEALED)
			}
		}
		self.atomicUpdateSlot(DEFINITIONS_TUPLE) {
			appendCanDestroy(definition, true)
		}
		// TODO MvG 2021-06-19:  This might be a race.  I *think* the worst that
		//  can happen is that dependency-incomparable modules won't have
		//  parsing plans for definitions that they can't parse invocations of
		//  anyhow, because they're not dependently related.  However, internal
		//  accounting might end up not quite right when the definitions or
		//  bundles need to be removed during unloading.
		owningBundles.get().forEach {
			it.addDefinitionParsingPlan(newParsingPlan(it, definition))
		}
		self.membershipChanged()
	}

	override fun o_MethodName(self: AvailObject): A_String =
		self.chooseBundle(self.module).message.atomName

	override fun o_NumArgs(self: AvailObject) = self.slot(NUM_ARGS)

	/**
	 * Remove the definition from me. Causes dependent chunks to be invalidated.
	 *
	 * Method manipulation takes place while all fibers are L1-precise and
	 * suspended.  Use a global lock at the outermost calls to side-step
	 * deadlocks.  Because no fiber is running, we don't have to protect
	 * subsystems like the L2Generator from these changes.
	 */
	override fun o_RemoveDefinition(
		self: AvailObject,
		definition: A_Definition
	) = L2Chunk.invalidationLock.withLock {
		assert(definition.definitionModule().notNil)
		self.atomicUpdateSlot(DEFINITIONS_TUPLE) {
			tupleWithout(this, definition)
		}
		owningBundles.get().forEach { bundle ->
			bundle.removePlanForSendable(definition)
		}
		self.membershipChanged()
	}

	/**
	 * Remove the chunk from my set of dependent chunks because it has been
	 * invalidated by a new definition in either me or another method on which
	 * the chunk is contingent.
	 */
	override fun o_RemoveDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) = synchronized(self) {
		assert(L2Chunk.invalidationLock.isHeldByCurrentThread)
		val set = dependentChunksWeakSet
		if (set !== null) {
			set.remove(chunk)
			if (set.isEmpty()) {
				dependentChunksWeakSet = null
			}
		}
	}

	override fun o_RemoveSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = synchronized(self) {
		val oldTuple: A_Tuple = self.slot(SEALED_ARGUMENTS_TYPES_TUPLE)
		val newTuple = tupleWithout(oldTuple, typeTuple)
		assert(newTuple.tupleSize == oldTuple.tupleSize - 1)
		self.setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, newTuple.makeShared())
	}

	override fun o_RemoveSemanticRestriction(
		self: AvailObject,
		restriction: A_SemanticRestriction
	) = synchronized(self) {
		var set: A_Set = self.slot(SEMANTIC_RESTRICTIONS_SET)
		set = set.setWithoutElementCanDestroy(restriction, true)
		self.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared())
	}

	override fun o_SealedArgumentsTypesTuple(self: AvailObject): A_Tuple =
		self.slot(SEALED_ARGUMENTS_TYPES_TUPLE)

	override fun o_SemanticRestrictions(self: AvailObject): A_Set =
		synchronized(self) { self.slot(SEMANTIC_RESTRICTIONS_SET) }

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.METHOD

	override fun o_SetLexer(self: AvailObject, lexer: A_Lexer) =
		synchronized(self) { self.setSlot(LEXER_OR_NIL, lexer) }

	override fun o_TestingTree(
		self: AvailObject
	): LookupTree<A_Definition, A_Tuple> = methodTestingTree(self)

	override fun o_UpdateStylers (
		self: AvailObject,
		updater: A_Set.() -> A_Set)
	{
		self.atomicUpdateSlot(STYLERS, 1, updater)
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("method") }
			at("aliases") { owningBundles.get().writeTo(writer) }
			at("definitions") { self.volatileSlot(DEFINITIONS_TUPLE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("method") }
			at("aliases") { owningBundles.get().writeSummaryTo(writer) }
			at("definitions") {
				self.volatileSlot(DEFINITIONS_TUPLE).writeSummaryTo(writer)
			}
		}

	/**
	 * The membership of this [method][MethodDescriptor] has changed. Invalidate
	 * anything that depended on the previous membership, including the
	 * [methodTestingTree] and any [L2Chunk]s in the [dependentChunksWeakSet].
	 *
	 * @param self
	 *   The method that changed.
	 */
	override fun o_MembershipChanged(self: AvailObject) {
		assert(L2Chunk.invalidationLock.isHeldByCurrentThread)
		// Invalidate any affected level two chunks.
		// Copy the set of chunks to avoid modification during iteration.
		val dependentsCopy: List<L2Chunk>
		synchronized(self) {
			val set: Set<L2Chunk?>? = dependentChunksWeakSet
			dependentsCopy =
				if (set === null) emptyList()
				else dependentChunksWeakSet!!.toList()
		}
		dependentsCopy.forEach { it.invalidate(DEPENDENCY_CHANGED) }
		synchronized(self) {
			assert(dependentChunksWeakSet === null
				|| dependentChunksWeakSet!!.isEmpty())

			// Invalidate the roots of the lookup trees.
			methodTestingTree = null
		}
	}

	/**
	 * `SpecialMethodAtom` enumerates [atoms][A_Atom] that are known to the
	 * virtual machine and that correspond to specific primitive invocations.
	 * Multiple [primitives][Primitive] may be provided to make the associated
	 * [method][A_Method] polymorphic.
	 *
	 * @constructor
	 *   Create an [A_Atom], an [A_Bundle], and either synthesized
	 *   [method&#32;definitions][MethodDefinitionDescriptor] or synthesized
	 *   [macro&#32;definitions][MacroDescriptor] wrapping the given
	 *   vararg array of [Primitive]s.  If the `prefixFunctions` list is
	 *   provided and non-null, produce macros, otherwise (`prefixFunctions` is
	 *   elided or null), produce methods.
	 * @param name
	 *   The name of the method or macro being defined.
	 * @param prefixFunctions
	 *   A [List] of prefix functions to provide to the macro definition if this
	 *   is a macro being defined, or null to indicate this is a non-macro. Note
	 *   that if there are multiple primitives provided in the variadic argument
	 *   below, each will use the same list of prefix functions.
	 * @param primitiveNames
	 *   The primitive to wrap into a method or macro definition.  Note that
	 *   multiple overrides may be provided in this variadic argument.
	 */
	enum class SpecialMethodAtom constructor(
		name: String,
		prefixFunctions: List<String>?,
		vararg primitiveNames: String)
	{
		/** The special atom for failing during bootstrap.  Must be first. */
		CRASH(
			"vm crash:_",
			P_EmergencyExit::class.java.name),

		/** The special atom for defining abstract methods. */
		ABSTRACT_DEFINER(
			"vm abstract_for_",
			P_AbstractMethodDeclarationForAtom::class.java.name),

		/** The special atom for adding to a map inside a variable. */
		ADD_TO_MAP_VARIABLE(
			"vm_↑[_]:=_",
			P_AtomicAddToMap::class.java.name),

		/** The special atom for removing from a map inside a variable. */
		REMOVE_FROM_MAP_VARIABLE(
			"vm_↑-=_",
			P_AtomicRemoveFromMap::class.java.name),

		/** The special atom for adding a module unload function. */
		ADD_UNLOADER(
			"vm on unload_",
			P_AddUnloadFunction::class.java.name),

		/** The special atom for creating aliases of atoms. */
		ALIAS(
			"vm alias new name_to_",
			P_Alias::class.java.name),

		/** The special atom for function application. */
		APPLY(
			"vm function apply_with tuple_",
			P_InvokeWithTuple::class.java.name),

		/** The special atom for adding properties to atoms. */
		ATOM_PROPERTY(
			"vm atom_at property_put_",
			P_AtomSetProperty::class.java.name),

		/** The special atom for removing properties from atoms. */
		ATOM_REMOVE_PROPERTY(
			"vm atom_remove property_",
			P_AtomRemoveProperty::class.java.name),

		/** The special atom for extracting the caller of a continuation. */
		CONTINUATION_CALLER(
			"vm_'s caller",
			P_ContinuationCaller::class.java.name),

		/** The special atom for creating a literal phrase. */
		CREATE_LITERAL_PHRASE(
			"vm create literal phrase_",
			P_CreateLiteralExpression::class.java.name),

		/** The special atom for creating a literal token. */
		CREATE_LITERAL_TOKEN(
			"vm create literal token_,_,_,_",
			P_CreateLiteralToken::class.java.name),

		/** The special atom for declaring the stringifier atom. */
		DECLARE_STRINGIFIER(
			"vm stringifier:=_",
			P_DeclareStringificationAtom::class.java.name),

		/** The special atom for forward-defining methods. */
		FORWARD_DEFINER(
			"vm forward_for_",
			P_ForwardMethodDeclarationForAtom::class.java.name),

		/** The special atom for getting a variable's value. */
		GET_VARIABLE(
			"vm↓_",
			P_GetValue::class.java.name),

		/** The special atom for adding grammatical restrictions. */
		GRAMMATICAL_RESTRICTION(
			"vm grammatical restriction_is_",
			P_GrammaticalRestrictionFromAtoms::class.java.name),

		/** The special atom for defining lexers. */
		LEXER_DEFINER(
			"vm lexer_filter is_body is_",
			P_SimpleLexerDefinitionForAtom::class.java.name),

		/** The special atom for defining macros. */
		MACRO_DEFINER(
			"vm macro_is«_,»_«styled by_»?",
			P_SimpleMacroDeclaration::class.java.name,
			P_SimpleMacroDefinitionForAtom::class.java.name),

		/** The special atom for defining methods. */
		METHOD_DEFINER(
			"vm method_is_«styled by_»?",
			P_SimpleMethodDeclaration::class.java.name,
			P_MethodDeclarationFromAtom::class.java.name),

		/**
		 * The special atom for explicitly attaching a name to compiled code.
		 * Note that some defining methods also have this effect implicitly.
		 */
		SET_COMPILED_CODE_NAME(
			"vm set name of raw function_to_",
			P_SetCompiledCodeName::class.java.name),

		/** The special atom for creating ordinary atoms. */
		CREATE_ATOM(
			"vm create atom_",
			P_CreateAtom::class.java.name),

		/** The special atom for creating fiber-heritable atoms. */
		CREATE_HERITABLE_ATOM(
			"vm create heritable atom_",
			P_CreateFiberHeritableAtom::class.java.name),

		/** The special atom for creating explicit-subclass atoms. */
		CREATE_EXPLICIT_SUBCLASS_ATOM(
			"vm create explicit subclass atom_",
			P_CreateExplicitSubclassAtom::class.java.name),

		/** The special atom for publishing atoms. */
		PUBLISH_ATOMS(
			"vm publish atom set_(public=_)",
			P_DeclareAllExportedAtoms::class.java.name),

		/**
		 * The special atom for publishing an atom created in the module body.
		 */
		PUBLISH_NEW_NAME(
			"vm publish new atom_",
			P_PublishName::class.java.name),

		/** The special atom for publishing all atoms imported from a module. */
		PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE(
			"vm publish all atoms from modules named_(public=_)",
			P_DeclareAllAtomsExportedFromAnotherModule::class.java.name),

		/** The special atom for recording a type's name. */
		RECORD_TYPE_NAME(
			"vm record type_name_",
			P_RecordNewTypeName::class.java.name),

		/** The special atom for creating a module variable/constant. */
		CREATE_MODULE_VARIABLE(
			"vm in_create_with variable type_«constant»?«stably computed»?",
			P_PrivateCreateModuleVariable::class.java.name),

		/** The special atom for sealing methods. */
		SEAL(
			"vm seal_at_",
			P_SealMethodByAtom::class.java.name),

		/** The special atom for adding semantic restrictions. */
		SEMANTIC_RESTRICTION(
			"vm semantic restriction_is_",
			P_AddSemanticRestrictionForAtom::class.java.name),

		/** The special atom for resuming a continuation. */
		RESUME_CONTINUATION(
			"vm resume_",
			P_ResumeContinuation::class.java.name),

		/** The special atom for rethrowing a Java exception in Avail. */
		GET_RETHROW_JAVA_EXCEPTION(
			"vm get rethrow in Avail hook",
			P_GetRaiseJavaExceptionInAvailFunction::class.java.name),

		SET_STYLER(
			"vm add styler of bundle_is_",
			P_SetStylerFunction::class.java.name),

		/** The special atom for parsing module headers. */
		MODULE_HEADER(
			"Module…#§"
				+ "«Versions«…#§‡,»»"
				+ ('«'
					+ ("«Extends|Uses»!"
						+ '«'
						+ "…#"
						+ "«(«…#§‡,»)»"
						+ "«=(««-»?…#«→…#»?‡,»,⁇«`…»?)»"
						+ "‡,"
					+ '»')
				+ '»')
				+ "«Names«…#‡,»»"
				+ "«Entries«…#‡,»»"
				+ "«Pragma«…#‡,»»"
				+ "Body",
			listOf(
				P_ModuleHeaderPrefixCheckModuleName::class.java.name,
				P_ModuleHeaderPrefixCheckModuleVersion::class.java.name,
				P_ModuleHeaderPrefixCheckImportVersion::class.java.name),
			P_ModuleHeaderPseudoMacro::class.java.name);

		/**
		 * Define a method.  Note that another variant of this constructor
		 * includes a list of prefix functions, indicating a macro should be
		 * constructed.
		 *
		 * @param name
		 * The name of the method or macro being defined.
		 * @param primitiveNames
		 * The primitive to wrap into a method or macro definition.  Note
		 * that multiple overrides may be provided in this variadic
		 * argument.
		 */
		constructor(
			name: String,
			vararg primitiveNames: String
		) : this(name, null, *primitiveNames)

		/** The special atom. */
		val atom: A_Atom = createSpecialAtom(name)

		/** The special atom's message bundle. */
		val bundle: A_Bundle =
			try
			{
				atom.bundleOrCreate()
			}
			catch (e: MalformedMessageException)
			{
				throw RuntimeException("VM method name is invalid: $name", e)
			}

		init
		{
			primitiveNames.forEach { primitiveName ->
				val function = createFunction(
					newPrimitiveRawFunction(
						holdersByClassName[primitiveName]!!.primitive, nil, 0),
					emptyTuple())
				try
				{
					when (prefixFunctions)
					{
						null -> bundle.bundleMethod.also { method ->
							method.methodAddDefinition(
								newMethodDefinition(method, nil, function))
						}
						else -> bundle.bundleAddMacro(
							newMacroDefinition(
								bundle,
								nil,
								function,
								tupleFromList(
									prefixFunctions.map { prefixName ->
										val holder =
											holdersByClassName[prefixName]!!
										createFunction(
											newPrimitiveRawFunction(
												holder.primitive, nil, 0),
											emptyTuple())
									})),
							true)
					}
				}
				catch (e: SignatureException)
				{
					assert(false) { "This should not happen!" }
					throw RuntimeException(
						"VM method name is invalid: $name", e)
				}
			}
			assert(atom.descriptor().isShared)
			assert(atom.isAtomSpecial)
		}
	}

	@Deprecated("Not supported", ReplaceWith("newMethod()"))
	override fun mutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newMethod()"))
	override fun immutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newMethod()"))
	override fun shared() = unsupported

	companion object {
		/** Atomic access to [methodTestingTree]. */
		private val methodTestingTreeUpdater = newUpdater(
			MethodDescriptor::class.java,
			LookupTree::class.java,
			"methodTestingTree")

		/**
		 * A [LookupTreeAdaptor] used for building and navigating the
		 * [LookupTree]s that implement runtime dispatching.  Also used for
		 * looking up [A_Macro]s in an [A_Bundle].
		 *
		 * @see methodTestingTree
		 */
		val runtimeDispatcher =
			object : LookupTreeAdaptor<A_Definition, A_Tuple, Unit>()
			{
				override val emptyLeaf =
					LeafLookupTree<A_Definition, A_Tuple>(emptyTuple)

				override fun extractSignature(element: A_Definition) =
					element.bodySignature().argsTupleType

				override fun constructResult(
					elements: List<A_Definition>,
					memento: Unit
				) = tupleFromList(elements)

				override fun compareTypes(
					argumentRestrictions: List<TypeRestriction>,
					signatureType: A_Type
				) = compareForDispatch(argumentRestrictions, signatureType)

				override fun testsArgumentPositions() = true

				override fun subtypesHideSupertypes() = true
			}

		/**
		 * Answer a new [method][MethodDescriptor]. It has no name yet, but will
		 * before it gets used in a send phrase.  It gets named by virtue of it
		 * being referenced by one or more
		 * [message&#32;bundles][MessageBundleDescriptor]s, each of which keeps
		 * track of how to parse it using that bundle's name.  The bundles will
		 * be grouped into a bundle tree to allow parsing of many possible
		 * message sends in aggregate.
		 *
		 * A method is always [shared][Mutability.SHARED], but its set of owning
		 * bundles, its tuple of definitions, its cached privateTestingTree, its
		 * macro testing tree, and its set of dependents chunk indices can all
		 * be updated (while holding a lock).
		 *
		 * @param numArgs
		 *   The number of arguments that this method expects.
		 * @return
		 *   A new method with no name and no definitions.
		 */
		fun newMethod(numArgs: Int): AvailObject =
			initialMutableDescriptor.create {
				setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())
				setSlot(NUM_ARGS, numArgs)
				setVolatileSlot(DEFINITIONS_TUPLE, emptyTuple)
				setSlot(SEMANTIC_RESTRICTIONS_SET, emptySet)
				setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, emptyTuple)
				setSlot(LEXER_OR_NIL, nil)
				setSlot(STYLERS, emptySet)
				// Create and plug in a new shared descriptor.
				setDescriptor(MethodDescriptor(Mutability.SHARED))
			}

		/**
		 * The number of lists to cache of N occurrences of the
		 * [TypeRestriction] that restricts an element to the type
		 * [any][ANY].
		 */
		private const val sizeOfListsOfAny = 10

		/**
		 * An array of lists of increasing size consisting only of
		 * [TypeRestriction]s to the type [any][ANY].
		 */
		private val listsOfAny = Array(sizeOfListsOfAny) {
			nCopies(it, anyRestriction)
		}

		/**
		 * Return a [List] of n copies of the [TypeRestriction] for
		 * [any][ANY].  N is required to be ≥ 0.
		 *
		 * @param n
		 *   The number of elements in the desired list, all the type any.
		 * @return
		 *   The list. Do not modify it, as it may be cached and reused.
		 */
		private fun nCopiesOfAnyRestriction(n: Int): List<TypeRestriction> =
			if (n < sizeOfListsOfAny) listsOfAny[n]
			else nCopies(n, anyRestriction)

		/**
		 * The sole [mutable][Mutability.MUTABLE] descriptor, used only while
		 * initializing a new [A_Method].
		 */
		private val initialMutableDescriptor =
			MethodDescriptor(Mutability.MUTABLE)
	}
}
