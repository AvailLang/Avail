/*
 * IndirectionDescriptor.kt
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
package com.avail.descriptor.representation

 import com.avail.annotations.HideFieldInDebugger
 import com.avail.compiler.AvailCodeGenerator
 import com.avail.compiler.scanning.LexingState
 import com.avail.compiler.splitter.MessageSplitter
 import com.avail.descriptor.atoms.A_Atom
 import com.avail.descriptor.atoms.A_Atom.Companion.atomName
 import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
 import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
 import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
 import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
 import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
 import com.avail.descriptor.atoms.A_Atom.Companion.issuingModule
 import com.avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
 import com.avail.descriptor.bundles.A_Bundle
 import com.avail.descriptor.bundles.A_Bundle.Companion.addDefinitionParsingPlan
 import com.avail.descriptor.bundles.A_Bundle.Companion.addGrammaticalRestriction
 import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
 import com.avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
 import com.avail.descriptor.bundles.A_Bundle.Companion.grammaticalRestrictions
 import com.avail.descriptor.bundles.A_Bundle.Companion.hasGrammaticalRestrictions
 import com.avail.descriptor.bundles.A_Bundle.Companion.message
 import com.avail.descriptor.bundles.A_Bundle.Companion.messageParts
 import com.avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
 import com.avail.descriptor.bundles.A_Bundle.Companion.removeGrammaticalRestriction
 import com.avail.descriptor.bundles.A_Bundle.Companion.removePlanForDefinition
 import com.avail.descriptor.bundles.A_BundleTree
 import com.avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
 import com.avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
 import com.avail.descriptor.bundles.A_BundleTree.Companion.expand
 import com.avail.descriptor.bundles.A_BundleTree.Companion.hasBackwardJump
 import com.avail.descriptor.bundles.A_BundleTree.Companion.isSourceOfCycle
 import com.avail.descriptor.bundles.A_BundleTree.Companion.latestBackwardJump
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyActions
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyComplete
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyIncomplete
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyIncompleteCaseInsensitive
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyPrefilterMap
 import com.avail.descriptor.bundles.A_BundleTree.Companion.lazyTypeFilterTreePojo
 import com.avail.descriptor.bundles.A_BundleTree.Companion.removePlanInProgress
 import com.avail.descriptor.bundles.A_BundleTree.Companion.updateForNewGrammaticalRestriction
 import com.avail.descriptor.character.A_Character.Companion.codePoint
 import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
 import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag
 import com.avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
 import com.avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
 import com.avail.descriptor.fiber.FiberDescriptor.TraceFlag
 import com.avail.descriptor.functions.A_Continuation
 import com.avail.descriptor.functions.A_Function
 import com.avail.descriptor.functions.A_RawFunction
 import com.avail.descriptor.maps.A_Map
 import com.avail.descriptor.maps.A_MapBin
 import com.avail.descriptor.maps.MapDescriptor.MapIterable
 import com.avail.descriptor.methods.A_Definition
 import com.avail.descriptor.methods.A_GrammaticalRestriction
 import com.avail.descriptor.methods.A_Method
 import com.avail.descriptor.methods.A_SemanticRestriction
 import com.avail.descriptor.module.A_Module
 import com.avail.descriptor.numbers.A_Number
 import com.avail.descriptor.numbers.AbstractNumberDescriptor
 import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
 import com.avail.descriptor.parsing.A_DefinitionParsingPlan
 import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
 import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
 import com.avail.descriptor.parsing.A_Lexer
 import com.avail.descriptor.parsing.A_Lexer.Companion.lexerBodyFunction
 import com.avail.descriptor.parsing.A_Lexer.Companion.lexerFilterFunction
 import com.avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
 import com.avail.descriptor.parsing.A_ParsingPlanInProgress
 import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.isBackwardJump
 import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.nameHighlightingPc
 import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPc
 import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPlan
 import com.avail.descriptor.phrases.A_Phrase
 import com.avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
 import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
 import com.avail.descriptor.phrases.A_Phrase.Companion.bundle
 import com.avail.descriptor.phrases.A_Phrase.Companion.childrenDo
 import com.avail.descriptor.phrases.A_Phrase.Companion.childrenMap
 import com.avail.descriptor.phrases.A_Phrase.Companion.copyConcatenating
 import com.avail.descriptor.phrases.A_Phrase.Companion.copyMutablePhrase
 import com.avail.descriptor.phrases.A_Phrase.Companion.copyWith
 import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
 import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
 import com.avail.descriptor.phrases.A_Phrase.Companion.expression
 import com.avail.descriptor.phrases.A_Phrase.Companion.expressionAt
 import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
 import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
 import com.avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
 import com.avail.descriptor.phrases.A_Phrase.Companion.generateInModule
 import com.avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
 import com.avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
 import com.avail.descriptor.phrases.A_Phrase.Companion.isLastUse
 import com.avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.lastExpression
 import com.avail.descriptor.phrases.A_Phrase.Companion.list
 import com.avail.descriptor.phrases.A_Phrase.Companion.literalObject
 import com.avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
 import com.avail.descriptor.phrases.A_Phrase.Companion.markerValue
 import com.avail.descriptor.phrases.A_Phrase.Companion.neededVariables
 import com.avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
 import com.avail.descriptor.phrases.A_Phrase.Companion.permutation
 import com.avail.descriptor.phrases.A_Phrase.Companion.statements
 import com.avail.descriptor.phrases.A_Phrase.Companion.statementsDo
 import com.avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
 import com.avail.descriptor.phrases.A_Phrase.Companion.stripMacro
 import com.avail.descriptor.phrases.A_Phrase.Companion.superUnionType
 import com.avail.descriptor.phrases.A_Phrase.Companion.token
 import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
 import com.avail.descriptor.phrases.A_Phrase.Companion.typeExpression
 import com.avail.descriptor.phrases.A_Phrase.Companion.validateLocally
 import com.avail.descriptor.phrases.A_Phrase.Companion.variable
 import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
 import com.avail.descriptor.representation.IndirectionDescriptor.ObjectSlots.INDIRECTION_TARGET
 import com.avail.descriptor.sets.A_Set
 import com.avail.descriptor.sets.SetDescriptor.SetIterator
 import com.avail.descriptor.tokens.A_Token
 import com.avail.descriptor.tokens.TokenDescriptor
 import com.avail.descriptor.tuples.A_String
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
 import com.avail.descriptor.types.TypeDescriptor
 import com.avail.descriptor.types.TypeTag
 import com.avail.descriptor.variables.A_Variable
 import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
 import com.avail.dispatch.LookupTree
 import com.avail.exceptions.AvailException
 import com.avail.exceptions.MalformedMessageException
 import com.avail.exceptions.MethodDefinitionException
 import com.avail.exceptions.SignatureException
 import com.avail.exceptions.VariableGetException
 import com.avail.exceptions.VariableSetException
 import com.avail.interpreter.Primitive
 import com.avail.interpreter.execution.AvailLoader
 import com.avail.interpreter.execution.AvailLoader.LexicalScanner
 import com.avail.interpreter.levelTwo.L2Chunk
 import com.avail.interpreter.levelTwo.operand.TypeRestriction
 import com.avail.io.TextInterface
 import com.avail.performance.Statistic
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.IteratorNotNull
 import com.avail.utility.Pair
 import com.avail.utility.json.JSONWriter
 import com.avail.utility.visitor.AvailSubobjectVisitor
 import java.math.BigInteger
 import java.nio.ByteBuffer
 import java.util.*
 import java.util.stream.Stream

/**
 * An [AvailObject] with an [IndirectionDescriptor] keeps track of its target,
 * that which it is pretending to be.  Almost all messages are routed to the
 * target, making it an ideal proxy.
 *
 * When some kinds of objects are compared to each other, say
 * [strings][A_String], a check is first made to see if the objects are at the
 * same location in memory -- the same AvailObject, in the current version that
 * uses [AvailObjectRepresentation].  If so, it immediately returns true.  If
 * not, a more detailed, potentially expensive comparison takes place.  If the
 * objects are found to be equal, one of them is mutated into an indirection (by
 * replacing its descriptor with an `IndirectionDescriptor`) to cause subsequent
 * comparisons to be faster.
 *
 * When Avail has had its own garbage collector over the years, it has been
 * possible to strip off indirections during a suitable level of garbage
 * collection.  When combined with the comparison optimization above, this has
 * the effect of collapsing together equal objects.  There was even once a
 * mechanism that collected objects at some garbage collection generation into a
 * set, causing *all* equal objects in that generation to be compared against
 * each other.  So not only does this mechanism save time, it also saves space.
 *
 * Of course, the cost of traversing indirections, and even just of descriptors
 * may be significant.  That's a complexity price that's paid once, with many
 * mechanisms depending on it to effect higher level optimizations.  Our bet is
 * that this will have a net payoff.  Especially since the low level
 * optimizations can be replaced with expression folding, dynamic inlining,
 * object escape analysis, instance-specific optimizations, and a plethora of
 * other just-in-time optimizations.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] that's in use at the target of this indirection.  Note that
 *   this will never change, even if the target is mutable.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class IndirectionDescriptor private constructor(
	mutability: Mutability,
	typeTag: TypeTag
) : AbstractDescriptor(
	mutability,
	typeTag,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The object slots of my [AvailObject] instances.  In particular, an
	 * [indirection][IndirectionDescriptor] has just a [INDIRECTION_TARGET],
	 * which is the object that the current object is equivalent to.  There may
	 * be other slots, depending on our mechanism for conversion to an
	 * indirection object, but they should be ignored.
	 */
	internal enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The target [object][AvailObject] to which my instance is delegating
		 * all behavior.
		 */
		INDIRECTION_TARGET,

		/**
		 * All other object slots should be ignored.
		 */
		@Suppress("unused")
		@HideFieldInDebugger
		IGNORED_OBJECT_SLOT_
	}

	/**
	 * The integer slots of my [AvailObject] instances.  Always ignored for an
	 * indirection object.
	 */
	internal enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * Ignore all integer slots.
		 */
		@Suppress("unused")
		@HideFieldInDebugger
		IGNORED_INTEGER_SLOT_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = e === INDIRECTION_TARGET

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = self.traversed().printOnAvoidingIndent(builder, recursionMap, indent)

	override fun o_ScanSubobjects(
		self: AvailObject,
		visitor: AvailSubobjectVisitor
	) {
		visitor.invoke(self.slot(INDIRECTION_TARGET))
	}

	override fun o_MakeImmutable(self: AvailObject): AvailObject {
		if (isMutable) {
			self.setDescriptor(immutable(typeTag))
			return self.slot(INDIRECTION_TARGET).makeImmutable()
		}
		return self.slot(INDIRECTION_TARGET)
	}

	override fun o_MakeShared(self: AvailObject): AvailObject {
		if (!isShared) {
			self.setDescriptor(shared(typeTag))
			return self.slot(INDIRECTION_TARGET).makeShared()
		}
		return self.slot(INDIRECTION_TARGET)
	}

	/**
	 * Answer the non-indirection pointed to (transitively) by object.  Also
	 * changes the object to point directly at the ultimate target to save hops
	 * next time if possible.
	 */
	override fun o_Traversed(self: AvailObject): AvailObject {
		val next = self.slot(INDIRECTION_TARGET)
		val finalObject = next.traversed()
		if (!finalObject.sameAddressAs(next)) {
			self.setSlot(INDIRECTION_TARGET, finalObject)
		}
		return finalObject
	}

	companion object {
		/** The mutable [IndirectionDescriptor].  */
		val mutables = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.MUTABLE, typeTag)
		}.toTypedArray()

		/** The immutable [IndirectionDescriptor].  */
		val immutables = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.IMMUTABLE, typeTag)
		}.toTypedArray()

		/** The shared [IndirectionDescriptor].  */
		val shareds = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.SHARED, typeTag)
		}.toTypedArray()

		/**
		 * Answer a [shared][Mutability.MUTABLE] `IndirectionDescriptor`
		 * suitable for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun mutable(typeTag: TypeTag): IndirectionDescriptor =
			mutables[typeTag.ordinal]

		/**
		 * Answer a [shared][Mutability.IMMUTABLE] `IndirectionDescriptor`
		 * suitable for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun immutable(typeTag: TypeTag): IndirectionDescriptor =
			immutables[typeTag.ordinal]

		/**
		 * Answer a [shared][Mutability.SHARED] `IndirectionDescriptor` suitable
		 * for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun shared(typeTag: TypeTag): IndirectionDescriptor =
			shareds[typeTag.ordinal]
	}

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun mutable(): AbstractDescriptor = mutables[typeTag.ordinal]

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun immutable(): AbstractDescriptor =
		immutables[typeTag.ordinal]

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun shared(): AbstractDescriptor = shareds[typeTag.ordinal]


	override fun o_ComputeTypeTag(self: AvailObject): TypeTag {
		val tag = self .. { typeTag() }
		// Now that we know it, switch to a descriptor that has it cached...
		self.setDescriptor(when {
			mutability === Mutability.MUTABLE -> mutable(tag)
			mutability === Mutability.IMMUTABLE -> immutable(tag)
			else -> shared(tag)
		})
		return tag
	}

	/**
	 * Define the infix ".." operator  to reducing redundancy in the many reflex
	 * methods below.
	 *
	 * @param body
	 *   How to redirect.
	 * @return
	 *   The result of the redirection.
	 */
	inline operator fun <R> AvailObject.rangeTo(
		body: AvailObject.() -> R
	): R = o_Traversed(this@rangeTo).body()


	/* ====================================================================
	 * The remainder of these methods are reflex methods that send the same
	 * message to the traversed target.
	 * ====================================================================
	 */

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type
	): Boolean = self .. { acceptsArgTypesFromFunctionType(functionType) }

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>
	): Boolean = self .. { acceptsListOfArgTypes(argTypes) }

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>
	): Boolean = self .. { acceptsListOfArgValues(argValues) }

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple
	): Boolean = self .. { acceptsTupleOfArgTypes(argTypes) }

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple
	): Boolean = self .. { acceptsTupleOfArguments(arguments) }

	override fun o_AddDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) = self .. { addDependentChunk(chunk) }

	@Throws(SignatureException::class)
	override fun o_MethodAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { methodAddDefinition(definition) }

	override fun o_AddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self .. { addGrammaticalRestriction(grammaticalRestriction) }

	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { addToInfinityCanDestroy(sign, canDestroy) }

	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. { addToIntegerCanDestroy(anInteger, canDestroy) }

	override fun o_ModuleAddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self .. { moduleAddGrammaticalRestriction(grammaticalRestriction) }

	override fun o_ModuleAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { moduleAddDefinition(definition) }

	override fun o_AddDefinitionParsingPlan(
		self: AvailObject,
		plan: A_DefinitionParsingPlan
	) = self .. { addDefinitionParsingPlan(plan) }

	override fun o_AddImportedName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { addImportedName(trueName) }

	override fun o_AddImportedNames(
		self: AvailObject,
		trueNames: A_Set
	) = self .. { addImportedNames(trueNames) }

	override fun o_IntroduceNewName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { introduceNewName(trueName) }

	override fun o_AddPrivateName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { addPrivateName(trueName) }

	override fun o_AddPrivateNames(
		self: AvailObject,
		trueNames: A_Set
	) = self .. { addPrivateNames(trueNames) }

	override fun o_SetBinAddingElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_BasicObject = self.. {
		setBinAddingElementHashLevelCanDestroy(
			elementObject, elementObjectHash, myLevel, canDestroy)
	}

	override fun o_BinElementAt(
		self: AvailObject,
		index: Int
	): AvailObject = self .. { binElementAt(index) }

	override fun o_BinHasElementWithHash(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean = self .. {
		binHasElementWithHash(elementObject, elementObjectHash)
	}

	override fun o_BinRemoveElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): AvailObject = self .. {
		binRemoveElementHashLevelCanDestroy(
			elementObject, elementObjectHash, myLevel, canDestroy)
	}

	override fun o_BreakpointBlock(
		self: AvailObject,
		value: AvailObject
	) = self .. { breakpointBlock(value) }

	override fun o_BuildFilteredBundleTree(
		self: AvailObject
	): A_BundleTree = self .. { buildFilteredBundleTree() }

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithStartingAt(
			startIndex1, endIndex1, anotherObject, startIndex2)
	}

	override fun o_CompareFromToWithAnyTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithAnyTupleStartingAt(
			startIndex1, endIndex1, aTuple, startIndex2)
	}

	override fun o_CompareFromToWithByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteStringStartingAt(
			startIndex1, endIndex1, aByteString, startIndex2)
	}

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteTupleStartingAt(
			startIndex1, endIndex1, aByteTuple, startIndex2)
	}

	override fun o_CompareFromToWithIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithIntegerIntervalTupleStartingAt(
			startIndex1, endIndex1, anIntegerIntervalTuple, startIndex2)
	}

	override fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex1, endIndex1, aSmallIntegerIntervalTuple, startIndex2)
	}

	override fun o_CompareFromToWithRepeatedElementTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithRepeatedElementTupleStartingAt(
			startIndex1, endIndex1, aRepeatedElementTuple, startIndex2)
	}

	override fun o_CompareFromToWithNybbleTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithNybbleTupleStartingAt(
			startIndex1, endIndex1, aNybbleTuple, startIndex2)
	}

	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithObjectTupleStartingAt(
			startIndex1, endIndex1, anObjectTuple, startIndex2)
	}

	override fun o_CompareFromToWithTwoByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithTwoByteStringStartingAt(
			startIndex1, endIndex1, aTwoByteString, startIndex2)
	}

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int
	): Int = self .. { computeHashFromTo(start, end) }

	override fun o_ConcatenateTuplesCanDestroy(
		self: AvailObject,
		canDestroy: Boolean
	): A_Tuple = self .. { concatenateTuplesCanDestroy(canDestroy) }

	override fun o_Continuation(
		self: AvailObject,
		value: A_Continuation
	) = self .. { continuation(value) }

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean
	): A_Tuple = self .. {
		copyTupleFromToCanDestroy(start, end, canDestroy)
	}

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>
	): Boolean = self .. { couldEverBeInvokedWith(argRestrictions) }

	override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { divideCanDestroy(aNumber, canDestroy) }

	override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { divideIntoInfinityCanDestroy(sign, canDestroy) }

	override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		divideIntoIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = self .. { equals(another) }

	override fun o_EqualsAnyTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsAnyTuple(aTuple) }

	override fun o_EqualsByteString(
		self: AvailObject,
		aByteString: A_String
	): Boolean = self .. { equalsByteString(aByteString) }

	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple
	): Boolean = self .. { equalsByteTuple(aByteTuple) }

	override fun o_EqualsCharacterWithCodePoint(
		self: AvailObject,
		aCodePoint: Int
	): Boolean = self .. { equalsCharacterWithCodePoint(aCodePoint) }

	override fun o_EqualsFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): Boolean = self .. { equalsFiberType(aFiberType) }

	override fun o_EqualsFunction(
		self: AvailObject,
		aFunction: A_Function
	): Boolean = self .. { equalsFunction(aFunction) }

	override fun o_EqualsFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): Boolean = self .. { equalsFunctionType(aFunctionType) }

	override fun o_EqualsIntegerIntervalTuple(
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple
	): Boolean = self .. { equalsIntegerIntervalTuple(
		anIntegerIntervalTuple) }

	override fun o_EqualsSmallIntegerIntervalTuple(
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple
	): Boolean = self .. {
		equalsSmallIntegerIntervalTuple(aSmallIntegerIntervalTuple)
	}

	override fun o_EqualsRepeatedElementTuple(
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple
	): Boolean = self .. {
		equalsRepeatedElementTuple(aRepeatedElementTuple)
	}

	override fun o_EqualsCompiledCode(
		self: AvailObject,
		aCompiledCode: A_RawFunction
	): Boolean = self .. { equalsCompiledCode(aCompiledCode) }

	override fun o_EqualsVariable(
		self: AvailObject,
		aVariable: A_Variable
	): Boolean = self .. { equalsVariable(aVariable) }

	override fun o_EqualsVariableType(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { equalsVariableType(aType) }

	override fun o_EqualsContinuation(
		self: AvailObject,
		aContinuation: A_Continuation
	): Boolean = self .. { equalsContinuation(aContinuation) }

	override fun o_EqualsContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): Boolean = self .. { equalsContinuationType(aContinuationType) }

	override fun o_EqualsCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): Boolean = self .. { equalsCompiledCodeType(aCompiledCodeType) }

	override fun o_EqualsDouble(
		self: AvailObject,
		aDouble: Double
	): Boolean = self .. { equalsDouble(aDouble) }

	override fun o_EqualsFloat(
		self: AvailObject,
		aFloat: Float
	): Boolean = self .. { equalsFloat(aFloat) }

	override fun o_EqualsInfinity(
		self: AvailObject,
		sign: Sign
	): Boolean = self .. { equalsInfinity(sign) }

	override fun o_EqualsInteger(
		self: AvailObject,
		anAvailInteger: AvailObject
	): Boolean = self .. { equalsInteger(anAvailInteger) }

	override fun o_EqualsIntegerRangeType(
		self: AvailObject,
		another: A_Type
	): Boolean = self .. { equalsIntegerRangeType(another) }

	override fun o_EqualsMap(
		self: AvailObject,
		aMap: A_Map
	): Boolean = self .. { equalsMap(aMap) }

	override fun o_EqualsMapType(
		self: AvailObject,
		aMapType: A_Type
	): Boolean = self .. { equalsMapType(aMapType) }

	override fun o_EqualsNybbleTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsNybbleTuple(aTuple) }

	override fun o_EqualsObject(
		self: AvailObject,
		anObject: AvailObject
	): Boolean = self .. { equalsObject(anObject) }

	override fun o_EqualsObjectTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsObjectTuple(aTuple) }

	override fun o_EqualsPojo(
		self: AvailObject,
		aPojo: AvailObject
	): Boolean = self .. { equalsPojo(aPojo) }

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject
	): Boolean = self .. { equalsPojoType(aPojoType) }

	override fun o_EqualsPrimitiveType(
		self: AvailObject,
		aPrimitiveType: A_Type
	): Boolean = self .. { equalsPrimitiveType(aPrimitiveType) }

	override fun o_EqualsRawPojoFor(
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any
	): Boolean = self .. { equalsRawPojoFor(otherRawPojo, otherJavaObject) }

	override fun o_EqualsReverseTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsReverseTuple(aTuple) }

	override fun o_EqualsSet(
		self: AvailObject,
		aSet: A_Set
	): Boolean = self .. { equalsSet(aSet) }

	override fun o_EqualsSetType(
		self: AvailObject,
		aSetType: A_Type
	): Boolean = self .. { equalsSetType(aSetType) }

	override fun o_EqualsTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): Boolean = self .. { equalsTupleType(aTupleType) }

	override fun o_EqualsTwoByteString(
		self: AvailObject,
		aString: A_String
	): Boolean = self .. { equalsTwoByteString(aString) }

	override fun o_ExecutionState(
		self: AvailObject,
		value: ExecutionState
	) = self .. { executionState(value) }

	override fun o_ExtractNybbleFromTupleAt(
		self: AvailObject,
		index: Int
	): Byte = self .. { extractNybbleFromTupleAt(index) }

	override fun o_FilterByTypes(
		self: AvailObject,
		argTypes: List<A_Type>
	): List<A_Definition> = self .. { filterByTypes(argTypes) }

	override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): AbstractNumberDescriptor.Order =
		self .. { numericCompareToInteger(anInteger) }

	override fun o_NumericCompareToInfinity(
		self: AvailObject,
		sign: Sign
	): AbstractNumberDescriptor.Order =
		self .. { numericCompareToInfinity(sign) }

	override fun o_HasElement(
		self: AvailObject,
		elementObject: A_BasicObject
	): Boolean = self .. { hasElement(elementObject) }

	override fun o_HashFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): Int = self .. { hashFromTo(startIndex, endIndex) }

	override fun o_HashOrZero(self: AvailObject, value: Int) =
		self .. { hashOrZero(value) }

	override fun o_HasKey(
		self: AvailObject,
		keyObject: A_BasicObject
	): Boolean = self .. { hasKey(keyObject) }

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean = self .. { hasObjectInstance(potentialInstance) }

	override fun o_DefinitionsAtOrBelow(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>
	): List<A_Definition> = self .. { definitionsAtOrBelow(argRestrictions) }

	override fun o_IncludesDefinition(
		self: AvailObject,
		definition: A_Definition
	): Boolean = self .. { includesDefinition(definition) }

	override fun o_SetInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	) = self .. { setInterruptRequestFlag(flag) }

	override fun o_CountdownToReoptimize(self: AvailObject, value: Int) =
		self .. { countdownToReoptimize(value) }

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject
	): Boolean = self .. { isBetterRepresentationThan(anotherObject) }

	override fun o_RepresentationCostOfTupleType(
		self: AvailObject
	): Int = self .. { representationCostOfTupleType() }

	override fun o_IsBinSubsetOf(
		self: AvailObject,
		potentialSuperset: A_Set
	): Boolean = self .. { isBinSubsetOf(potentialSuperset) }

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isInstanceOfKind(aType) }

	override fun o_IsSubsetOf(
		self: AvailObject,
		another: A_Set
	): Boolean = self .. { isSubsetOf(another) }

	override fun o_IsSubtypeOf(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isSubtypeOf(aType) }

	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): Boolean = self .. { isSupertypeOfVariableType(aVariableType) }

	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): Boolean = self .. {
		isSupertypeOfContinuationType(aContinuationType)
	}

	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): Boolean = self .. {
		isSupertypeOfCompiledCodeType(aCompiledCodeType)
	}

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isSupertypeOfFiberType(aType) }

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): Boolean = self .. { isSupertypeOfFunctionType(aFunctionType) }

	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): Boolean {
		return self .. { isSupertypeOfIntegerRangeType(anIntegerRangeType) }
	}

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): Boolean = self .. { isSupertypeOfListNodeType(aListNodeType) }

	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject
	): Boolean = self .. { isSupertypeOfMapType(aMapType) }

	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean = self .. { isSupertypeOfObjectType(anObjectType) }

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): Boolean = self .. { isSupertypeOfPhraseType(aPhraseType) }

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): Boolean = self .. { isSupertypeOfPojoType(aPojoType) }

	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types
	): Boolean = self .. {
		isSupertypeOfPrimitiveTypeEnum(primitiveTypeEnum)
	}

	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): Boolean = self .. { isSupertypeOfSetType(aSetType) }

	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): Boolean = self .. { isSupertypeOfTupleType(aTupleType) }

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_BasicObject
	): Boolean = self .. { isSupertypeOfEnumerationType(anEnumerationType) }

	override fun o_Iterator(self: AvailObject): IteratorNotNull<AvailObject> =
		self .. { iterator() }

	override fun o_Spliterator(self: AvailObject): Spliterator<AvailObject> =
		self .. { spliterator() }

	override fun o_Stream(self: AvailObject): Stream<AvailObject> =
		self .. { stream() }

	override fun o_ParallelStream(self: AvailObject): Stream<AvailObject> =
		self .. { parallelStream() }

	override fun o_LevelTwoChunkOffset(
		self: AvailObject,
		chunk: L2Chunk,
		offset: Int
	) = self .. { levelTwoChunkOffset(chunk, offset) }

	override fun o_LiteralAt(self: AvailObject, index: Int): AvailObject =
		self .. { literalAt(index) }

	override fun o_FrameAt(
		self: AvailObject,
		index: Int
	): AvailObject = self .. { frameAt(index) }

	override fun o_FrameAtPut(
		self: AvailObject,
		index: Int,
		value: AvailObject
	): AvailObject = self .. { frameAtPut(index, value) }

	override fun o_LocalTypeAt(
		self: AvailObject,
		index: Int
	): A_Type = self .. { localTypeAt(index) }

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByTypesFromTuple(
		self: AvailObject,
		argumentTypeTuple: A_Tuple
	): A_Definition = self .. { lookupByTypesFromTuple(argumentTypeTuple) }

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByValuesFromList(
		self: AvailObject,
		argumentList: List<A_BasicObject>
	): A_Definition = self .. { lookupByValuesFromList(argumentList) }

	override fun o_MapAt(
		self: AvailObject,
		keyObject: A_BasicObject
	): AvailObject = self .. { mapAt(keyObject) }

	override fun o_MapAtPuttingCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map = self .. {
		mapAtPuttingCanDestroy(keyObject, newValueObject, canDestroy)
	}

	override fun o_MapAtReplacingCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		canDestroy: Boolean
	): A_Map = self.. {
		mapAtReplacingCanDestroy(key, notFoundValue, transformer, canDestroy)
	}

	override fun o_MapWithoutKeyCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map = self .. { mapWithoutKeyCanDestroy(keyObject, canDestroy) }

	override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { minusCanDestroy(aNumber, canDestroy) }

	override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { multiplyByInfinityCanDestroy(sign, canDestroy) }

	override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		multiplyByIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_NameVisible(
		self: AvailObject,
		trueName: A_Atom
	): Boolean = self .. { nameVisible(trueName) }

	override fun o_OptionallyNilOuterVar(
		self: AvailObject,
		index: Int
	): Boolean = self .. { optionallyNilOuterVar(index) }

	override fun o_OuterTypeAt(self: AvailObject, index: Int): A_Type =
		self .. { outerTypeAt(index) }

	override fun o_OuterVarAt(self: AvailObject, index: Int): AvailObject =
		self .. { outerVarAt(index) }

	override fun o_OuterVarAtPut(
		self: AvailObject,
		index: Int,
		value: AvailObject
	) = self .. { outerVarAtPut(index, value) }

	override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { plusCanDestroy(aNumber, canDestroy) }

	override fun o_Priority(
		self: AvailObject,
		value: Int
	) = self .. { priority(value) }

	override fun o_FiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self .. { fiberGlobals(globals) }

	override fun o_RawByteForCharacterAt(
		self: AvailObject,
		index: Int
	): Short = self .. { rawByteForCharacterAt(index) }

	override fun o_RawShortForCharacterAt(
		self: AvailObject,
		index: Int
	): Int = self .. { rawShortForCharacterAt(index) }

	override fun o_RawShortForCharacterAtPut(
		self: AvailObject,
		index: Int,
		anInteger: Int
	) = self .. { rawShortForCharacterAtPut(index, anInteger) }

	override fun o_RawSignedIntegerAt(self: AvailObject, index: Int): Int =
		self .. { rawSignedIntegerAt(index) }

	override fun o_RawSignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self .. { rawSignedIntegerAtPut(index, value) }

	override fun o_RawUnsignedIntegerAt(
		self: AvailObject,
		index: Int
	): Long = self .. { rawUnsignedIntegerAt(index) }

	override fun o_RawUnsignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self .. { rawUnsignedIntegerAtPut(index, value) }

	override fun o_RemoveDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) = self .. { removeDependentChunk(chunk) }

	override fun o_RemoveFrom(
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: Function0<Unit>) =
			self .. { removeFrom(loader, afterRemoval) }

	override fun o_RemoveDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { removeDefinition(definition) }

	override fun o_RemoveGrammaticalRestriction(
		self: AvailObject,
		obsoleteRestriction: A_GrammaticalRestriction
	) = self .. { removeGrammaticalRestriction(obsoleteRestriction) }

	override fun o_ResolveForward(
		self: AvailObject,
		forwardDefinition: A_BasicObject
	) = self .. { resolveForward(forwardDefinition) }

	override fun o_SetIntersectionCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setIntersectionCanDestroy(otherSet, canDestroy) }

	override fun o_SetMinusCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setMinusCanDestroy(otherSet, canDestroy) }

	override fun o_SetUnionCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setUnionCanDestroy(otherSet, canDestroy) }

	@Throws(VariableSetException::class)
	override fun o_SetValue(
		self: AvailObject,
		newValue: A_BasicObject
	) = self .. { setValue(newValue) }

	override fun o_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject
	) = self .. { setValueNoCheck(newValue) }

	override fun o_SetWithElementCanDestroy(
		self: AvailObject,
		newElementObject: A_BasicObject,
		canDestroy: Boolean
	): A_Set = self .. {
		setWithElementCanDestroy(newElementObject, canDestroy)
	}

	override fun o_SetWithoutElementCanDestroy(
		self: AvailObject,
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean
	): A_Set = self .. {
		setWithoutElementCanDestroy(elementObjectToExclude, canDestroy)
	}

	override fun o_StackAt(self: AvailObject, slotIndex: Int): AvailObject =
		self .. { stackAt(slotIndex) }

	override fun o_SetStartingChunkAndReoptimizationCountdown(
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long
	) = self .. {
		setStartingChunkAndReoptimizationCountdown(chunk, countdown)
	}

	override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. {
		subtractFromInfinityCanDestroy(sign, canDestroy)
	}

	override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		subtractFromIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { timesCanDestroy(aNumber, canDestroy) }

	override fun o_TrueNamesForStringName(
		self: AvailObject,
		stringName: A_String
	): A_Set = self .. { trueNamesForStringName(stringName) }

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject =
		self .. { tupleAt(index) }

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Tuple = self .. {
		tupleAtPuttingCanDestroy(index, newValueObject, canDestroy)
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		self .. { tupleIntAt(index) }

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type =
		self .. { typeAtIndex(index) }

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = self .. { typeIntersection(another) }

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): A_Type = self .. {
		typeIntersectionOfCompiledCodeType(aCompiledCodeType)
	}

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): A_Type = self .. {
		typeIntersectionOfContinuationType(aContinuationType)
	}

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): A_Type = self .. { typeIntersectionOfFiberType(aFiberType) }

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): A_Type = self .. { typeIntersectionOfFunctionType(aFunctionType) }

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): A_Type = self .. {
		typeIntersectionOfIntegerRangeType(anIntegerRangeType)
	}

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): A_Type = self .. { typeIntersectionOfListNodeType(aListNodeType) }

	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type
	): A_Type = self .. { typeIntersectionOfMapType(aMapType) }

	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type = self .. { typeIntersectionOfObjectType(anObjectType) }

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): A_Type = self .. { typeIntersectionOfPhraseType(aPhraseType) }

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): A_Type = self .. { typeIntersectionOfPojoType(aPojoType) }

	override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): A_Type = self .. { typeIntersectionOfSetType(aSetType) }

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): A_Type = self .. { typeIntersectionOfTupleType(aTupleType) }

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): A_Type = self .. { typeIntersectionOfVariableType(aVariableType) }

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type
	): A_Type = self .. { typeUnion(another) }

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): A_Type = self .. { typeUnionOfFiberType(aFiberType) }

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): A_Type = self .. { typeUnionOfFunctionType(aFunctionType) }

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): A_Type = self .. { typeUnionOfVariableType(aVariableType) }

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): A_Type = self .. { typeUnionOfContinuationType(aContinuationType) }

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): A_Type = self .. { typeUnionOfCompiledCodeType(aCompiledCodeType) }

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): A_Type = self .. { typeUnionOfIntegerRangeType(anIntegerRangeType) }

	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type
	): A_Type = self .. { typeUnionOfMapType(aMapType) }

	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type = self .. { typeUnionOfObjectType(anObjectType) }

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): A_Type = self .. { typeUnionOfPhraseType(aPhraseType) }

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoType(aPojoType) }

	override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): A_Type = self .. { typeUnionOfSetType(aSetType) }

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): A_Type = self .. { typeUnionOfTupleType(aTupleType) }

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Type = self .. { unionOfTypesAtThrough(startIndex, endIndex) }

	override fun o_Value(
		self: AvailObject,
		value: A_BasicObject
	) = self .. { value(value) }

	override fun o_AsNativeString(self: AvailObject): String =
		self .. { asNativeString() }

	override fun o_AsSet(self: AvailObject): A_Set =
		self .. { asSet() }

	override fun o_AsTuple(self: AvailObject): A_Tuple =
		self .. { asTuple() }

	override fun o_BitsPerEntry(self: AvailObject): Int =
		self .. { bitsPerEntry() }

	override fun o_BodyBlock(self: AvailObject): A_Function =
		self .. { bodyBlock() }

	override fun o_BodySignature(self: AvailObject): A_Type =
		self .. { bodySignature() }

	override fun o_BreakpointBlock(self: AvailObject): A_BasicObject =
		self .. { breakpointBlock() }

	override fun o_Caller(self: AvailObject): A_Continuation =
		self .. { caller() }

	override fun o_ClearValue(self: AvailObject) =
		self .. { clearValue() }

	override fun o_Function(self: AvailObject): A_Function =
		self .. { function() }

	override fun o_FunctionType(self: AvailObject): A_Type =
		self .. { functionType() }

	override fun o_Code(self: AvailObject): A_RawFunction =
		self .. { code() }

	override fun o_CodePoint(self: AvailObject): Int =
		self .. { codePoint() }

	override fun o_LazyComplete(self: AvailObject): A_Set =
		self .. { lazyComplete() }

	override fun o_ConstantBindings(self: AvailObject): A_Map =
		self .. { constantBindings() }

	override fun o_ContentType(self: AvailObject): A_Type =
		self .. { contentType() }

	override fun o_Continuation(self: AvailObject): A_Continuation =
		self .. { continuation() }

	override fun o_CopyAsMutableIntTuple(self: AvailObject): A_Tuple =
		self .. { copyAsMutableIntTuple() }

	override fun o_CopyAsMutableObjectTuple(self: AvailObject): A_Tuple =
		self .. { copyAsMutableObjectTuple() }

	override fun o_DefaultType(self: AvailObject): A_Type =
		self .. { defaultType() }

	override fun o_EnsureMutable(self: AvailObject): A_Continuation =
		self .. { ensureMutable() }

	override fun o_ExecutionState(self: AvailObject): ExecutionState =
		self .. { executionState() }

	override fun o_Expand(self: AvailObject, module: A_Module) =
		self .. { expand(module) }

	override fun o_ExtractBoolean(self: AvailObject): Boolean =
		self .. { extractBoolean() }

	override fun o_ExtractUnsignedByte(self: AvailObject): Short =
		self .. { extractUnsignedByte() }

	override fun o_ExtractDouble(self: AvailObject): Double =
		self .. { extractDouble() }

	override fun o_ExtractFloat(self: AvailObject): Float =
		self .. { extractFloat() }

	override fun o_ExtractInt(self: AvailObject): Int =
		self .. { extractInt() }

	override fun o_ExtractLong(self: AvailObject): Long =
		self .. { extractLong() }

	override fun o_ExtractNybble(self: AvailObject): Byte =
		self .. { extractNybble() }

	override fun o_FieldMap(self: AvailObject): A_Map =
		self .. { fieldMap() }

	override fun o_FieldTypeMap(self: AvailObject): A_Map =
		self .. { fieldTypeMap() }

	@Throws(VariableGetException::class)
	override fun o_GetValue(self: AvailObject): AvailObject =
		self .. { getValue() }

	override fun o_Hash(self: AvailObject): Int =
		self .. { hash() }

	override fun o_HashOrZero(self: AvailObject): Int =
		self .. { hashOrZero() }

	override fun o_HasGrammaticalRestrictions(self: AvailObject): Boolean =
		self .. { hasGrammaticalRestrictions() }

	override fun o_DefinitionsTuple(self: AvailObject): A_Tuple =
		self .. { definitionsTuple() }

	override fun o_LazyIncomplete(self: AvailObject): A_Map =
		self .. { lazyIncomplete() }

	override fun o_DecrementCountdownToReoptimize(
		self: AvailObject,
		continuation: (Boolean)->Unit
	) = self .. { decrementCountdownToReoptimize(continuation) }

	override fun o_IsAbstractDefinition(self: AvailObject): Boolean =
		self .. { isAbstractDefinition() }

	override fun o_IsAbstract(self: AvailObject): Boolean =
		self .. { isAbstract }

	override fun o_IsBoolean(self: AvailObject): Boolean =
		self .. { isBoolean }

	override fun o_IsUnsignedByte(self: AvailObject): Boolean =
		self .. { isUnsignedByte }

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	override fun o_IsByteTuple(self: AvailObject): Boolean =
		self .. { isByteTuple }

	override fun o_IsCharacter(self: AvailObject): Boolean =
		self .. { isCharacter }

	override fun o_IsFunction(self: AvailObject): Boolean =
		self .. { isFunction }

	override fun o_IsAtom(self: AvailObject): Boolean =
		self .. { isAtom }

	override fun o_IsExtendedInteger(self: AvailObject): Boolean =
		self .. { isExtendedInteger }

	override fun o_IsFinite(self: AvailObject): Boolean =
		self .. { isFinite }

	override fun o_IsForwardDefinition(self: AvailObject): Boolean =
		self .. { isForwardDefinition() }

	override fun o_IsInstanceMeta(self: AvailObject): Boolean =
		self .. { isInstanceMeta }

	override fun o_IsMethodDefinition(self: AvailObject): Boolean =
		self .. { isMethodDefinition() }

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean =
		self .. { isIntegerRangeType }

	override fun o_IsMap(self: AvailObject): Boolean =
		self .. { isMap }

	override fun o_IsMapType(self: AvailObject): Boolean =
		self .. { isMapType }

	override fun o_IsNybble(self: AvailObject): Boolean =
		self .. { isNybble }

	override fun o_IsPositive(self: AvailObject): Boolean =
		self .. { isPositive() }

	override fun o_IsSet(self: AvailObject): Boolean =
		self .. { isSet }

	override fun o_IsSetType(self: AvailObject): Boolean =
		self .. { isSetType }

	override fun o_IsString(self: AvailObject): Boolean =
		self .. { isString }

	override fun o_IsSupertypeOfBottom(self: AvailObject): Boolean =
		self .. { isSupertypeOfBottom }

	override fun o_IsTuple(self: AvailObject): Boolean =
		self .. { isTuple }

	override fun o_IsTupleType(self: AvailObject): Boolean =
		self .. { isTupleType }

	override fun o_IsType(self: AvailObject): Boolean =
		self .. { isType }

	override fun o_KeysAsSet(self: AvailObject): A_Set =
		self .. { keysAsSet() }

	override fun o_KeyType(self: AvailObject): A_Type =
		self .. { keyType() }

	override fun o_LevelTwoChunk(self: AvailObject): L2Chunk =
		self .. { levelTwoChunk() }

	override fun o_LevelTwoOffset(self: AvailObject): Int =
		self .. { levelTwoOffset() }

	override fun o_Literal(self: AvailObject): AvailObject =
		self .. { literal() }

	override fun o_LowerBound(self: AvailObject): A_Number =
		self .. { lowerBound() }

	override fun o_LowerInclusive(self: AvailObject): Boolean =
		self .. { lowerInclusive() }

	override fun o_MakeSubobjectsImmutable(self: AvailObject): AvailObject =
		self .. { makeSubobjectsImmutable() }

	override fun o_MakeSubobjectsShared(self: AvailObject): AvailObject =
		self .. { makeSubobjectsShared() }

	override fun o_MapSize(self: AvailObject): Int =
		self .. { mapSize() }

	override fun o_MaxStackDepth(self: AvailObject): Int =
		self .. { maxStackDepth() }

	override fun o_Message(self: AvailObject): A_Atom =
		self .. { message() }

	override fun o_MessageParts(self: AvailObject): A_Tuple =
		self .. { messageParts() }

	override fun o_MethodDefinitions(self: AvailObject): A_Set =
		self .. { methodDefinitions() }

	override fun o_ImportedNames(self: AvailObject): A_Map =
		self .. { importedNames() }

	override fun o_NewNames(self: AvailObject): A_Map =
		self .. { newNames() }

	override fun o_NumArgs(self: AvailObject): Int =
		self .. { numArgs() }

	override fun o_NumSlots(self: AvailObject): Int =
		self .. { numSlots() }

	override fun o_NumLiterals(self: AvailObject): Int =
		self .. { numLiterals() }

	override fun o_NumLocals(self: AvailObject): Int =
		self .. { numLocals() }

	override fun o_NumOuters(self: AvailObject): Int =
		self .. { numOuters() }

	override fun o_NumOuterVars(self: AvailObject): Int =
		self .. { numOuterVars() }

	override fun o_Nybbles(self: AvailObject): A_Tuple =
		self .. { nybbles() }

	override fun o_Parent(self: AvailObject): A_BasicObject =
		self .. { parent() }

	override fun o_Pc(self: AvailObject): Int =
		self .. { pc() }

	override fun o_Priority(self: AvailObject): Int =
		self .. { priority() }

	override fun o_PrivateNames(self: AvailObject): A_Map =
		self .. { privateNames() }

	override fun o_FiberGlobals(self: AvailObject): A_Map =
		self .. { fiberGlobals() }

	override fun o_GrammaticalRestrictions(self: AvailObject): A_Set =
		self .. { grammaticalRestrictions() }

	override fun o_ReturnType(self: AvailObject): A_Type =
		self .. { returnType() }

	override fun o_SetBinHash(self: AvailObject): Int =
		self .. { setBinHash() }

	override fun o_SetBinSize(self: AvailObject): Int =
		self .. { setBinSize() }

	override fun o_SetSize(self: AvailObject): Int =
		self .. { setSize() }

	override fun o_SizeRange(self: AvailObject): A_Type =
		self .. { sizeRange() }

	override fun o_LazyActions(self: AvailObject): A_Map =
		self .. { lazyActions() }

	override fun o_Stackp(self: AvailObject): Int =
		self .. { stackp() }

	override fun o_Start(self: AvailObject): Int =
		self .. { start() }

	override fun o_StartingChunk(self: AvailObject): L2Chunk =
		self .. { startingChunk() }

	override fun o_String(self: AvailObject): A_String =
		self .. { string() }

	override fun o_TokenType(self: AvailObject): TokenDescriptor.TokenType =
		self .. { tokenType() }

	override fun o_TrimExcessInts(self: AvailObject) =
		self .. { trimExcessInts() }

	override fun o_TupleReverse(self: AvailObject): A_Tuple =
		self .. { tupleReverse() }

	override fun o_TupleSize(self: AvailObject): Int =
		self .. { tupleSize() }

	override fun o_Kind(self: AvailObject): A_Type =
		self .. { kind() }

	override fun o_TypeTuple(self: AvailObject): A_Tuple =
		self .. { typeTuple() }

	override fun o_UpperBound(self: AvailObject): A_Number =
		self .. { upperBound() }

	override fun o_UpperInclusive(self: AvailObject): Boolean =
		self .. { upperInclusive() }

	override fun o_Value(self: AvailObject): AvailObject =
		self .. { value() }

	override fun o_ValuesAsTuple(self: AvailObject): A_Tuple =
		self .. { valuesAsTuple() }

	override fun o_ValueType(self: AvailObject): A_Type =
		self .. { valueType() }

	override fun o_VariableBindings(self: AvailObject): A_Map =
		self .. { variableBindings() }

	override fun o_VisibleNames(self: AvailObject): A_Set =
		self .. { visibleNames() }

	override fun o_ParsingInstructions(self: AvailObject): A_Tuple =
		self .. { parsingInstructions() }

	override fun o_Expression(self: AvailObject): A_Phrase =
		self .. { expression() }

	override fun o_Variable(self: AvailObject): A_Phrase =
		self .. { variable() }

	override fun o_ArgumentsTuple(self: AvailObject): A_Tuple =
		self .. { argumentsTuple() }

	override fun o_StatementsTuple(self: AvailObject): A_Tuple =
		self .. { statementsTuple() }

	override fun o_ResultType(self: AvailObject): A_Type =
		self .. { resultType() }

	override fun o_NeededVariables(
		self: AvailObject,
		neededVariables: A_Tuple
	) = self .. { neededVariables(neededVariables) }

	override fun o_NeededVariables(self: AvailObject): A_Tuple =
		self .. { neededVariables() }

	override fun o_Primitive(self: AvailObject): Primitive? =
		self .. { primitive() }

	override fun o_PrimitiveNumber(self: AvailObject): Int =
		self .. { primitiveNumber() }

	override fun o_DeclaredType(self: AvailObject): A_Type =
		self .. { declaredType() }

	override fun o_DeclarationKind(self: AvailObject): DeclarationKind =
		self .. { declarationKind() }

	override fun o_TypeExpression(self: AvailObject): A_Phrase =
		self .. { typeExpression() }

	override fun o_InitializationExpression(self: AvailObject): AvailObject =
		self .. { initializationExpression() }

	override fun o_LiteralObject(self: AvailObject): A_BasicObject =
		self .. { literalObject() }

	override fun o_Token(self: AvailObject): A_Token =
		self .. { token() }

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self .. { markerValue() }

	override fun o_ArgumentsListNode(self: AvailObject): A_Phrase =
		self .. { argumentsListNode() }

	override fun o_Bundle(self: AvailObject): A_Bundle =
		self .. { bundle() }

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self .. { expressionsTuple() }

	override fun o_Declaration(self: AvailObject): A_Phrase =
		self .. { declaration() }

	override fun o_ExpressionType(self: AvailObject): A_Type =
		self .. { expressionType() }

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitEffectOn(codeGenerator) }

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitValueOn(codeGenerator) }

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self .. { childrenMap(transformer) }

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = self .. { childrenDo(action) }

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) = self .. { validateLocally(parent) }

	override fun o_GenerateInModule(
		self: AvailObject,
		module: A_Module
	): A_RawFunction = self .. { generateInModule(module) }

	override fun o_CopyWith(
		self: AvailObject,
		newPhrase: A_Phrase
	): A_Phrase = self .. { copyWith(newPhrase) }

	override fun o_CopyConcatenating(
		self: AvailObject,
		newListPhrase: A_Phrase
	): A_Phrase = self .. { copyConcatenating(newListPhrase) }

	override fun o_IsLastUse(
		self: AvailObject,
		isLastUse: Boolean
	) = self .. { isLastUse(isLastUse) }

	override fun o_IsLastUse(self: AvailObject): Boolean =
		self .. { isLastUse() }

	override fun o_IsMacroDefinition(self: AvailObject): Boolean =
		self .. { isMacroDefinition() }

	override fun o_CopyMutablePhrase(self: AvailObject): A_Phrase =
		self .. { copyMutablePhrase() }

	override fun o_BinUnionKind(self: AvailObject): A_Type =
		self .. { binUnionKind() }

	override fun o_OutputPhrase(self: AvailObject): A_Phrase =
		self .. { outputPhrase() }

	override fun o_ApparentSendName(self: AvailObject): A_Atom =
		self .. { apparentSendName() }

	override fun o_Statements(self: AvailObject): A_Tuple =
		self .. { statements() }

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self .. { flattenStatementsInto(accumulatedStatements) }

	override fun o_LineNumber(self: AvailObject): Int =
		self .. { lineNumber() }

	override fun o_AllParsingPlansInProgress(self: AvailObject): A_Map =
		self .. { allParsingPlansInProgress() }

	override fun o_IsSetBin(self: AvailObject): Boolean =
		self .. { isSetBin }

	override fun o_MapIterable(self: AvailObject): MapIterable =
		self .. { mapIterable() }

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self .. { declaredExceptions() }

	override fun o_IsInt(self: AvailObject): Boolean =
		self .. { isInt }

	override fun o_IsLong(self: AvailObject): Boolean =
		self .. { isLong }

	override fun o_ArgsTupleType(self: AvailObject): A_Type =
		self .. { argsTupleType() }

	override fun o_EqualsInstanceTypeFor(
		self: AvailObject,
		anObject: AvailObject
	): Boolean = self .. { equalsInstanceTypeFor(anObject) }

	override fun o_Instances(self: AvailObject): A_Set =
		self .. { instances() }

	override fun o_EqualsEnumerationWithSet(
		self: AvailObject,
		aSet: A_Set
	): Boolean = self .. { equalsEnumerationWithSet(aSet) }

	override fun o_IsEnumeration(self: AvailObject): Boolean =
		self .. { isEnumeration }

	override fun o_IsInstanceOf(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isInstanceOf(aType) }

	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean =
		self .. { enumerationIncludesInstance(potentialInstance) }

	override fun o_ComputeSuperkind(self: AvailObject): A_Type =
		self .. { computeSuperkind() }

	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject
	) = self .. { setAtomProperty(key, value) }

	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom
	): AvailObject = self .. { getAtomProperty(key) }

	override fun o_EqualsEnumerationType(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = self .. { equalsEnumerationType(another) }

	override fun o_ReadType(self: AvailObject): A_Type =
		self .. { readType() }

	override fun o_WriteType(self: AvailObject): A_Type =
		self .. { writeType() }

	override fun o_Versions(
		self: AvailObject,
		versionStrings: A_Set) {
		self .. { versions(versionStrings) }
	}

	override fun o_Versions(self: AvailObject): A_Set =
		self .. { versions() }

	override fun o_EqualsPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): Boolean = self .. { equalsPhraseType(aPhraseType) }

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		self .. { phraseKind() }

	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind
	): Boolean = self .. { phraseKindIsUnder(expectedPhraseKind) }

	override fun o_IsRawPojo(self: AvailObject): Boolean =
		self .. { isRawPojo }

	override fun o_AddSemanticRestriction(
		self: AvailObject,
		restrictionSignature: A_SemanticRestriction
	) = self .. { addSemanticRestriction(restrictionSignature) }

	override fun o_RemoveSemanticRestriction(
		self: AvailObject,
		restriction: A_SemanticRestriction
	) = self .. { removeSemanticRestriction(restriction) }

	override fun o_SemanticRestrictions(
		self: AvailObject
	): A_Set = self .. { semanticRestrictions() }

	override fun o_AddSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = self .. { addSealedArgumentsType(typeTuple) }

	override fun o_RemoveSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = self .. { removeSealedArgumentsType(typeTuple) }

	override fun o_SealedArgumentsTypesTuple(
		self: AvailObject
	): A_Tuple = self .. { sealedArgumentsTypesTuple() }

	override fun o_ModuleAddSemanticRestriction(
		self: AvailObject,
		semanticRestriction: A_SemanticRestriction
	) = self .. { moduleAddSemanticRestriction(semanticRestriction) }

	override fun o_AddConstantBinding(
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable
	) = self .. { addConstantBinding(name, constantBinding) }

	override fun o_AddVariableBinding(
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable
	) = self .. { addVariableBinding(name, variableBinding) }

	override fun o_IsMethodEmpty(
		self: AvailObject
	): Boolean = self .. { isMethodEmpty() }

	override fun o_IsPojoSelfType(self: AvailObject): Boolean =
		self .. { isPojoSelfType }

	override fun o_PojoSelfType(self: AvailObject): A_Type =
		self .. { pojoSelfType() }

	override fun o_JavaClass(self: AvailObject): AvailObject =
		self .. { javaClass() }

	override fun o_IsUnsignedShort(self: AvailObject): Boolean =
		self .. { isUnsignedShort }

	override fun o_ExtractUnsignedShort(self: AvailObject): Int =
		self .. { extractUnsignedShort() }

	override fun o_IsFloat(self: AvailObject): Boolean =
		self .. { isFloat }

	override fun o_IsDouble(self: AvailObject): Boolean =
		self .. { isDouble }

	override fun o_RawPojo(self: AvailObject): AvailObject =
		self .. { rawPojo() }

	override fun o_IsPojo(self: AvailObject): Boolean =
		self .. { isPojo }

	override fun o_IsPojoType(self: AvailObject): Boolean =
		self .. { isPojoType }

	override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number): AbstractNumberDescriptor.Order =
		self .. { numericCompare(another) }

	override fun o_NumericCompareToDouble(
		self: AvailObject,
		aDouble: Double): AbstractNumberDescriptor.Order =
		self .. { numericCompareToDouble(aDouble) }

	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = self .. {
		addToDoubleCanDestroy(doubleObject, canDestroy)
	}

	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { addToFloatCanDestroy(floatObject, canDestroy) }

	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { subtractFromDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { subtractFromFloatCanDestroy(floatObject, canDestroy) }

	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { multiplyByDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { multiplyByFloatCanDestroy(floatObject, canDestroy) }

	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { divideIntoDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { divideIntoFloatCanDestroy(floatObject, canDestroy) }

	override fun o_LazyPrefilterMap(self: AvailObject): A_Map =
		self .. { lazyPrefilterMap() }

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		self .. { serializerOperation() }

	override fun o_MapBinAtHashPutLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin = self.. {
		mapBinAtHashPutLevelCanDestroy(key, keyHash, value, myLevel, canDestroy)
	}

	override fun o_MapBinRemoveKeyHashCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	): A_MapBin =
		self .. { mapBinRemoveKeyHashCanDestroy(key, keyHash, canDestroy) }

	override fun o_MapBinAtHashReplacingLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin = self.. {
		mapBinAtHashReplacingLevelCanDestroy(
			key, keyHash, notFoundValue, transformer, myLevel, canDestroy)
	}

	override fun o_MapBinSize(self: AvailObject): Int =
		self .. { mapBinSize() }

	override fun o_MapBinKeyUnionKind(self: AvailObject): A_Type =
		self .. { mapBinKeyUnionKind() }

	override fun o_MapBinValueUnionKind(self: AvailObject): A_Type =
		self .. { mapBinValueUnionKind() }

	override fun o_IsHashedMapBin(self: AvailObject): Boolean =
		self .. { isHashedMapBin() }

	override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject? = self .. { mapBinAtHash(key, keyHash) }

	override fun o_MapBinKeysHash(
		self: AvailObject
	): Int = self .. { mapBinKeysHash() }

	override fun o_MapBinValuesHash(self: AvailObject): Int =
		self .. { mapBinValuesHash() }

	override fun o_IssuingModule(
		self: AvailObject
	): A_Module = self .. { issuingModule() }

	override fun o_IsPojoFusedType(self: AvailObject): Boolean =
		self .. { isPojoFusedType }

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type
	): Boolean = self .. { isSupertypeOfPojoBottomType(aPojoType) }

	override fun o_EqualsPojoBottomType(self: AvailObject): Boolean =
		self .. { equalsPojoBottomType() }

	override fun o_JavaAncestors(self: AvailObject): AvailObject =
		self .. { javaAncestors() }

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = self .. { typeIntersectionOfPojoFusedType(aFusedPojoType) }

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type =
		self .. { typeIntersectionOfPojoUnfusedType(anUnfusedPojoType) }

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoFusedType(aFusedPojoType) }

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoUnfusedType(anUnfusedPojoType) }

	override fun o_IsPojoArrayType(self: AvailObject): Boolean =
		self .. { isPojoArrayType }

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any = self .. { marshalToJava(classHint) }

	override fun o_TypeVariables(self: AvailObject): A_Map =
		self .. { typeVariables() }

	override fun o_EqualsPojoField(
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject
	): Boolean = self .. { equalsPojoField(field, receiver) }

	override fun o_IsSignedByte(self: AvailObject): Boolean =
		self .. { isSignedByte }

	override fun o_IsSignedShort(self: AvailObject): Boolean =
		self .. { isSignedShort }

	override fun o_ExtractSignedByte(self: AvailObject): Byte =
		self .. { extractSignedByte() }

	override fun o_ExtractSignedShort(self: AvailObject): Short =
		self .. { extractSignedShort() }

	override fun o_EqualsEqualityRawPojo(
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any
	): Boolean = self .. { equalsEqualityRawPojoFor(self, otherJavaObject) }

	override fun <T> o_JavaObject(self: AvailObject): T? =
		self .. { javaObject() }

	override fun o_AsBigInteger(
		self: AvailObject
	): BigInteger = self .. { asBigInteger() }

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean
	): A_Tuple = self .. { appendCanDestroy(newElement, canDestroy) }

	override fun o_LazyIncompleteCaseInsensitive(
		self: AvailObject
	): A_Map = self .. { lazyIncompleteCaseInsensitive() }

	override fun o_LowerCaseString(self: AvailObject): A_String =
		self .. { lowerCaseString() }

	override fun o_InstanceCount(self: AvailObject): A_Number =
		self .. { instanceCount() }

	override fun o_TotalInvocations(self: AvailObject): Long =
		self .. { totalInvocations() }

	override fun o_TallyInvocation(self: AvailObject) =
		self .. { tallyInvocation() }

	override fun o_FieldTypeTuple(self: AvailObject): A_Tuple =
		self .. { fieldTypeTuple() }

	override fun o_FieldTuple(self: AvailObject): A_Tuple =
		self .. { fieldTuple() }

	override fun o_LiteralType(self: AvailObject): A_Type =
		self .. { literalType() }

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): A_Type = self .. { typeIntersectionOfTokenType(aTokenType) }

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): A_Type =
		self .. { typeIntersectionOfLiteralTokenType(aLiteralTokenType) }

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): A_Type = self .. { typeUnionOfTokenType(aTokenType) }

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): A_Type = self .. { typeUnionOfLiteralTokenType(aLiteralTokenType) }

	override fun o_IsTokenType(self: AvailObject): Boolean =
		self .. { isTokenType }

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean =
		self .. { isLiteralTokenType }

	override fun o_IsLiteralToken(self: AvailObject): Boolean =
		self .. { isLiteralToken() }

	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): Boolean = self .. { isSupertypeOfTokenType(aTokenType) }

	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): Boolean =
		self .. { isSupertypeOfLiteralTokenType(aLiteralTokenType) }

	override fun o_EqualsTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): Boolean = self .. { equalsTokenType(aTokenType) }

	override fun o_EqualsLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): Boolean = self .. { equalsLiteralTokenType(aLiteralTokenType) }

	override fun o_EqualsObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean = self .. { equalsObjectType(anObjectType) }

	override fun o_EqualsToken(
		self: AvailObject,
		aToken: A_Token
	): Boolean = self .. { equalsToken(aToken) }

	override fun o_BitwiseAnd(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseAnd(anInteger, canDestroy) }

	override fun o_BitwiseOr(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseOr(anInteger, canDestroy) }

	override fun o_BitwiseXor(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseXor(anInteger, canDestroy) }

	override fun o_AddSeal(
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple
	) = self .. { addSeal(methodName, argumentTypes) }

	override fun o_Instance(
		self: AvailObject
	): AvailObject = self .. { instance() }

	override fun o_SetMethodName(
		self: AvailObject,
		methodName: A_String
	) = self .. { setMethodName(methodName) }

	override fun o_StartingLineNumber(
		self: AvailObject
	): Int = self .. { startingLineNumber() }

	override fun o_Module(self: AvailObject): A_Module =
		self .. { module() }

	override fun o_MethodName(self: AvailObject): A_String =
		self .. { methodName() }

	override fun o_NameForDebugger(self: AvailObject): String =
		"IND→" + (self .. { nameForDebugger() })

	override fun o_BinElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: A_Type
	): Boolean = self .. { binElementsAreAllInstancesOfKind(kind) }

	override fun o_SetElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: AvailObject
	): Boolean = self .. { setElementsAreAllInstancesOfKind(kind) }

	override fun o_MapBinIterable(
		self: AvailObject
	): MapIterable = self .. { mapBinIterable() }

	override fun o_RangeIncludesInt(
		self: AvailObject,
		anInt: Int
	): Boolean = self .. { rangeIncludesInt(anInt) }

	override fun o_BitShiftLeftTruncatingToBits(
		self: AvailObject,
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	): A_Number = self .. {
		bitShiftLeftTruncatingToBits(shiftFactor, truncationBits, canDestroy)
	}

	override fun o_SetBinIterator(
		self: AvailObject
	): SetIterator = self .. { setBinIterator() }

	override fun o_BitShift(
		self: AvailObject,
		shiftFactor: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitShift(shiftFactor, canDestroy) }

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = self .. { equalsPhrase(aPhrase) }

	override fun o_StripMacro(
		self: AvailObject
	): A_Phrase = self .. { stripMacro() }

	override fun o_DefinitionMethod(
		self: AvailObject
	): A_Method = self .. { definitionMethod() }

	override fun o_PrefixFunctions(
		self: AvailObject
	): A_Tuple = self .. { prefixFunctions() }

	override fun o_EqualsByteArrayTuple(
		self: AvailObject,
		aByteArrayTuple: A_Tuple
	): Boolean = self .. { equalsByteArrayTuple(aByteArrayTuple) }

	override fun o_CompareFromToWithByteArrayTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteArrayTupleStartingAt(
			startIndex1, endIndex1, aByteArrayTuple, startIndex2)
	}

	override fun o_ByteArray(self: AvailObject): ByteArray =
		self .. { byteArray() }

	override fun o_IsByteArrayTuple(self: AvailObject): Boolean =
		self .. { isByteArrayTuple }

	override fun o_UpdateForNewGrammaticalRestriction(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress,
		treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
	) = self .. {
		updateForNewGrammaticalRestriction(planInProgress, treesToVisit)
	}

	override fun <T> o_Lock(self: AvailObject, supplier: () -> T): T =
		self .. { lock(supplier) }

	override fun o_ModuleName(self: AvailObject): A_String =
		self .. { moduleName() }

	override fun o_BundleMethod(self: AvailObject): A_Method =
		self .. { bundleMethod() }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		self: AvailObject,
		newValue: A_BasicObject
	): AvailObject = self .. { getAndSetValue(newValue) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject
	): Boolean = self .. { compareAndSwapValues(reference, newValue) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		self: AvailObject,
		addend: A_Number
	): A_Number = self .. { fetchAndAddValue(addend) }

	override fun o_FailureContinuation(
		self: AvailObject
	): (Throwable)->Unit =
		self .. { failureContinuation() }

	override fun o_ResultContinuation(
		self: AvailObject
	): (AvailObject)->Unit =
		self .. { resultContinuation() }

	override fun o_AvailLoader(self: AvailObject): AvailLoader? =
		self .. { availLoader() }

	override fun o_AvailLoader(self: AvailObject, loader: AvailLoader?) =
		self .. { availLoader(loader) }

	override fun o_InterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = self .. { interruptRequestFlag(flag) }

	override fun o_GetAndClearInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = self .. { getAndClearInterruptRequestFlag(flag) }

	override fun o_GetAndSetSynchronizationFlag(
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean
	): Boolean = self.. { getAndSetSynchronizationFlag(flag, value) }

	override fun o_FiberResult(self: AvailObject): AvailObject =
		self .. { fiberResult() }

	override fun o_FiberResult(self: AvailObject, result: A_BasicObject) =
		self .. { fiberResult(result) }

	override fun o_JoiningFibers(self: AvailObject): A_Set =
		self .. { joiningFibers() }

	override fun o_WakeupTask(self: AvailObject): TimerTask? =
		self .. { wakeupTask() }

	override fun o_WakeupTask(self: AvailObject, task: TimerTask?) =
		self .. { wakeupTask(task) }

	override fun o_JoiningFibers(self: AvailObject, joiners: A_Set) =
		self .. { joiningFibers(joiners) }

	override fun o_HeritableFiberGlobals(self: AvailObject): A_Map =
		self .. { heritableFiberGlobals() }

	override fun o_HeritableFiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self .. { heritableFiberGlobals(globals) }

	override fun o_GeneralFlag(self: AvailObject, flag: GeneralFlag): Boolean =
		self .. { generalFlag(flag) }

	override fun o_SetGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		self .. { setGeneralFlag(flag) }

	override fun o_ClearGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		self .. { clearGeneralFlag(flag) }

	override fun o_ByteBuffer(self: AvailObject): ByteBuffer =
		self .. { byteBuffer() }

	override fun o_EqualsByteBufferTuple(
		self: AvailObject,
		aByteBufferTuple: A_Tuple
	): Boolean = self .. { equalsByteBufferTuple(aByteBufferTuple) }

	override fun o_CompareFromToWithByteBufferTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteBufferTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2)
	}

	override fun o_IsByteBufferTuple(self: AvailObject): Boolean =
		self .. { isByteBufferTuple }

	override fun o_FiberName(self: AvailObject): A_String =
		self .. { fiberName() }

	override fun o_FiberNameSupplier(
		self: AvailObject,
		supplier: () -> A_String
	) = self .. { fiberNameSupplier(supplier) }

	override fun o_Bundles(self: AvailObject): A_Set =
		self .. { bundles() }

	override fun o_MethodAddBundle(self: AvailObject, bundle: A_Bundle) =
		self .. { methodAddBundle(bundle) }

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self .. { definitionModule() }

	override fun o_DefinitionModuleName(self: AvailObject): A_String =
		self .. { definitionModuleName() }

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate(self: AvailObject): A_Bundle =
		self .. { bundleOrCreate() }

	override fun o_BundleOrNil(self: AvailObject): A_Bundle =
		self .. { bundleOrNil() }

	override fun o_EntryPoints(self: AvailObject): A_Map =
		self .. { entryPoints() }

	override fun o_AddEntryPoint(
		self: AvailObject,
		stringName: A_String,
		trueName: A_Atom
	) = self .. { addEntryPoint(stringName, trueName) }

	override fun o_AllAncestors(self: AvailObject): A_Set =
		self .. { allAncestors() }

	override fun o_AddAncestors(self: AvailObject, moreAncestors: A_Set) =
		self .. { addAncestors(moreAncestors) }

	override fun o_ArgumentRestrictionSets(self: AvailObject): A_Tuple =
		self .. { argumentRestrictionSets() }

	override fun o_RestrictedBundle(self: AvailObject): A_Bundle =
		self .. { restrictedBundle() }

	override fun o_AtomName(self: AvailObject): A_String =
		self .. { atomName() }

	override fun o_AdjustPcAndStackp(self: AvailObject, pc: Int, stackp: Int) =
		self .. { adjustPcAndStackp(pc, stackp) }

	override fun o_TreeTupleLevel(self: AvailObject): Int =
		self .. { treeTupleLevel() }

	override fun o_ChildCount(self: AvailObject): Int =
		self .. { childCount() }

	override fun o_ChildAt(self: AvailObject, childIndex: Int): A_Tuple =
		self .. { childAt(childIndex) }

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean
	): A_Tuple = self .. { concatenateWith(otherTuple, canDestroy) }

	override fun o_ReplaceFirstChild(
		self: AvailObject,
		newFirst: A_Tuple
	): A_Tuple = self .. { replaceFirstChild(newFirst) }

	override fun o_IsByteString(self: AvailObject): Boolean =
		self .. { isByteString }

	override fun o_IsTwoByteString(self: AvailObject): Boolean =
		self .. { isTwoByteString }

	override fun o_IsIntegerIntervalTuple(self: AvailObject): Boolean =
		self .. { isIntegerIntervalTuple }

	override fun o_IsSmallIntegerIntervalTuple(self: AvailObject): Boolean =
		self .. { isSmallIntegerIntervalTuple }

	override fun o_IsRepeatedElementTuple(self: AvailObject): Boolean =
		self .. { isRepeatedElementTuple }

	override fun o_AddWriteReactor(
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor
	) = self .. { addWriteReactor(key, reactor) }

	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor(self: AvailObject, key: A_Atom) =
		self .. { removeWriteReactor(key) }

	override fun o_TraceFlag(self: AvailObject, flag: TraceFlag): Boolean =
		self .. { traceFlag(flag) }

	override fun o_SetTraceFlag(self: AvailObject, flag: TraceFlag) =
		self .. { setTraceFlag(flag) }

	override fun o_ClearTraceFlag(self: AvailObject, flag: TraceFlag) =
		self .. { clearTraceFlag(flag) }

	override fun o_RecordVariableAccess(
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean
	) = self.. { recordVariableAccess(variable, wasRead) }

	override fun o_VariablesReadBeforeWritten(self: AvailObject): A_Set =
		self .. { variablesReadBeforeWritten() }

	override fun o_VariablesWritten(self: AvailObject): A_Set =
		self .. { variablesWritten() }

	override fun o_ValidWriteReactorFunctions(self: AvailObject): A_Set =
		self .. { validWriteReactorFunctions() }

	override fun o_ReplacingCaller(
		self: AvailObject,
		newCaller: A_Continuation
	): A_Continuation = self .. { replacingCaller(newCaller) }

	override fun o_WhenContinuationIsAvailableDo(
		self: AvailObject,
		whenReified: (A_Continuation) -> Unit
	) = self .. { whenContinuationIsAvailableDo(whenReified) }

	override fun o_GetAndClearReificationWaiters(self: AvailObject): A_Set =
		self .. { getAndClearReificationWaiters() }

	override fun o_IsBottom(self: AvailObject): Boolean =
		self .. { isBottom }

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		self .. { isVacuousType }

	override fun o_IsTop(self: AvailObject): Boolean =
		self .. { isTop }

	override fun o_IsAtomSpecial(self: AvailObject): Boolean =
		self .. { isAtomSpecial() }

	override fun o_HasValue(self: AvailObject): Boolean =
		self .. { hasValue() }

	override fun o_AddUnloadFunction(
		self: AvailObject,
		unloadFunction: A_Function
	) = self .. { addUnloadFunction(unloadFunction) }

	override fun o_ExportedNames(self: AvailObject): A_Set =
		self .. { exportedNames() }

	override fun o_IsInitializedWriteOnceVariable(self: AvailObject): Boolean =
		self .. { isInitializedWriteOnceVariable }

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer
	) = self .. {
		transferIntoByteBuffer(startIndex, endIndex, outputByteBuffer)
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type
	): Boolean = self .. {
		tupleElementsInRangeAreInstancesOf(startIndex, endIndex, type)
	}

	override fun o_IsNumericallyIntegral(self: AvailObject): Boolean =
		self .. { isNumericallyIntegral() }

	override fun o_TextInterface(self: AvailObject): TextInterface =
		self .. { textInterface() }

	override fun o_TextInterface(
		self: AvailObject,
		textInterface: TextInterface
	) = self .. { textInterface(textInterface) }

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		self .. { writeTo(writer) }

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		self .. { writeSummaryTo(writer) }

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types
	): A_Type =
		self .. { typeIntersectionOfPrimitiveTypeEnum(primitiveTypeEnum) }

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types
	): A_Type = self .. { typeUnionOfPrimitiveTypeEnum(primitiveTypeEnum) }

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Tuple = self .. { tupleOfTypesFromTo(startIndex, endIndex) }

	override fun o_ShowValueInNameForDebugger(
		self: AvailObject
	): Boolean = self .. { showValueInNameForDebugger() }

	override fun o_List(self: AvailObject): A_Phrase = self .. { list() }

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self .. { permutation() }

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitAllValuesOn(codeGenerator) }

	override fun o_SuperUnionType(self: AvailObject): A_Type =
		self .. { superUnionType() }

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self .. { hasSuperCast() }

	override fun o_MacroDefinitionsTuple(self: AvailObject): A_Tuple =
		self .. { macroDefinitionsTuple() }

	override fun o_LookupMacroByPhraseTuple(
		self: AvailObject,
		argumentPhraseTuple: A_Tuple
	): A_Tuple = self .. { lookupMacroByPhraseTuple(argumentPhraseTuple) }

	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self .. { expressionAt(index) }

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self .. { expressionsSize() }

	override fun o_ParsingPc(self: AvailObject): Int =
		self .. { parsingPc() }

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean =
		self .. { isMacroSubstitutionNode() }

	override fun o_MessageSplitter(self: AvailObject): MessageSplitter =
		self .. { messageSplitter() }

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = self .. { statementsDo(continuation) }

	override fun o_MacroOriginalSendNode(self: AvailObject): A_Phrase =
		self .. { macroOriginalSendNode() }

	override fun o_EqualsInt(
		self: AvailObject,
		theInt: Int
	): Boolean = self .. { equalsInt(theInt) }

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self .. { tokens() }

	override fun o_ChooseBundle(
		self: AvailObject,
		currentModule: A_Module
	): A_Bundle = self .. { chooseBundle(currentModule) }

	override fun o_ValueWasStablyComputed(self: AvailObject): Boolean =
		self .. { valueWasStablyComputed() }

	override fun o_ValueWasStablyComputed(
		self: AvailObject,
		wasStablyComputed: Boolean
	) = self .. { valueWasStablyComputed(wasStablyComputed) }

	override fun o_UniqueId(self: AvailObject): Long =
		self .. { uniqueId() }

	override fun o_Definition(self: AvailObject): A_Definition =
		self .. { definition() }

	override fun o_NameHighlightingPc(self: AvailObject): String =
		self .. { nameHighlightingPc() }

	override fun o_SetIntersects(self: AvailObject, otherSet: A_Set): Boolean =
		self .. { setIntersects(otherSet) }

	override fun o_RemovePlanForDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { removePlanForDefinition(definition) }

	override fun o_DefinitionParsingPlans(self: AvailObject): A_Map =
		self .. { definitionParsingPlans() }

	override fun o_EqualsListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): Boolean = self .. { equalsListNodeType(aListNodeType) }

	override fun o_SubexpressionsTupleType(self: AvailObject): A_Type =
		self .. { subexpressionsTupleType() }

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): A_Type = self .. { typeUnionOfListNodeType(aListNodeType) }

	override fun o_LazyTypeFilterTreePojo(self: AvailObject): A_BasicObject =
		self .. { lazyTypeFilterTreePojo() }

	override fun o_AddPlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = self .. { addPlanInProgress(planInProgress) }

	override fun o_ParsingSignature(self: AvailObject): A_Type =
		self .. { parsingSignature() }

	override fun o_RemovePlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = self .. { removePlanInProgress(planInProgress) }

	override fun o_ModuleSemanticRestrictions(self: AvailObject): A_Set =
		self .. { moduleSemanticRestrictions() }

	override fun o_ModuleGrammaticalRestrictions(self: AvailObject): A_Set =
		self .. { moduleGrammaticalRestrictions() }

	override fun o_FieldAt(
		self: AvailObject, field: A_Atom
	): AvailObject = self .. { fieldAt(field) }

	override fun o_FieldAtPuttingCanDestroy(
		self: AvailObject,
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	): A_BasicObject =
		self .. { fieldAtPuttingCanDestroy(field, value, canDestroy) }

	override fun o_FieldTypeAt(
		self: AvailObject, field: A_Atom
	): A_Type = self .. { fieldTypeAt(field) }

	override fun o_ParsingPlan(self: AvailObject): A_DefinitionParsingPlan =
		self .. { parsingPlan() }

	override fun o_CompareFromToWithIntTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithIntTupleStartingAt(
			startIndex1, endIndex1, anIntTuple, startIndex2)
	}

	override fun o_IsIntTuple(self: AvailObject): Boolean =
		self .. { isIntTuple }

	override fun o_EqualsIntTuple(
		self: AvailObject, anIntTuple: A_Tuple
	): Boolean = self .. { equalsIntTuple(anIntTuple) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject
	) = self .. { atomicAddToMap(key, value) }

	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey(
		self: AvailObject, key: A_BasicObject
	): Boolean = self .. { variableMapHasKey(key) }

	override fun o_LexerMethod(self: AvailObject): A_Method =
		self .. { lexerMethod() }

	override fun o_LexerFilterFunction(self: AvailObject): A_Function =
		self .. { lexerFilterFunction() }

	override fun o_LexerBodyFunction(self: AvailObject): A_Function =
		self .. { lexerBodyFunction() }

	override fun o_SetLexer(self: AvailObject, lexer: A_Lexer) =
		self .. { setLexer(lexer) }

	override fun o_AddLexer(self: AvailObject, lexer: A_Lexer) =
		self .. { addLexer(lexer) }

	override fun o_NextLexingState(self: AvailObject): LexingState =
		self .. { nextLexingState() }

	override fun o_NextLexingStatePojo(self: AvailObject): AvailObject =
		self .. { nextLexingStatePojo() }

	override fun o_SetNextLexingStateFromPrior(
		self: AvailObject,
		priorLexingState: LexingState
	) = self .. { setNextLexingStateFromPrior(priorLexingState) }

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int =
		self .. { tupleCodePointAt(index) }

	override fun o_OriginatingPhrase(self: AvailObject): A_Phrase =
		self .. { originatingPhrase() }

	override fun o_IsGlobal(self: AvailObject): Boolean =
		self .. { isGlobal() }

	override fun o_GlobalModule(self: AvailObject): A_Module =
		self .. { globalModule() }

	override fun o_GlobalName(self: AvailObject): A_String =
		self .. { globalName() }

	override fun o_CreateLexicalScanner(self: AvailObject): LexicalScanner =
		self .. { createLexicalScanner() }

	override fun o_Lexer(self: AvailObject): A_Lexer =
		self .. { lexer() }

	override fun o_SuspendingFunction(
		self: AvailObject,
		suspendingFunction: A_Function
	) = self .. { suspendingFunction(suspendingFunction) }

	override fun o_SuspendingFunction(self: AvailObject): A_Function =
		self .. { suspendingFunction() }

	override fun o_IsBackwardJump(self: AvailObject): Boolean =
		self .. { isBackwardJump() }

	override fun o_LatestBackwardJump(
		self: AvailObject
	): A_BundleTree = self .. { latestBackwardJump() }

	override fun o_HasBackwardJump(self: AvailObject): Boolean =
		self .. { hasBackwardJump() }

	override fun o_IsSourceOfCycle(self: AvailObject): Boolean =
		self .. { isSourceOfCycle() }

	override fun o_IsSourceOfCycle(
		self: AvailObject,
		isSourceOfCycle: Boolean
	) = self .. { isSourceOfCycle(isSourceOfCycle) }

	override fun o_DebugLog(self: AvailObject): StringBuilder =
		self .. { debugLog() }

	override fun o_NumConstants(self: AvailObject): Int =
		self .. { numConstants() }

	override fun o_ConstantTypeAt(self: AvailObject, index: Int): A_Type =
		self .. { constantTypeAt(index) }

	override fun o_ReturnerCheckStat(self: AvailObject): Statistic =
		self .. { returnerCheckStat() }

	override fun o_ReturneeCheckStat(self: AvailObject): Statistic =
		self .. { returneeCheckStat() }

	override fun o_NumNybbles(self: AvailObject): Int =
		self .. { numNybbles() }

	override fun o_LineNumberEncodedDeltas(self: AvailObject): A_Tuple =
		self .. { lineNumberEncodedDeltas() }

	override fun o_CurrentLineNumber(self: AvailObject): Int =
		self .. { currentLineNumber() }

	override fun o_FiberResultType(self: AvailObject): A_Type =
		self .. { fiberResultType() }

	override fun o_TestingTree(
		self: AvailObject): LookupTree<A_Definition, A_Tuple> =
		self .. { testingTree() }

	override fun o_ForEach(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) = self .. { forEach(action) }

	override fun o_ForEachInMapBin(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) = self .. { forEachInMapBin(action) }

	override fun o_SetSuccessAndFailureContinuations(
		self: AvailObject,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit
	) = self .. { setSuccessAndFailureContinuations(onSuccess, onFailure) }

	override fun o_ClearLexingState(self: AvailObject) =
		self .. { clearLexingState() }

	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self .. { lastExpression() }

	override fun o_RegisterDump(self: AvailObject): AvailObject =
		self .. { registerDump() }
}
