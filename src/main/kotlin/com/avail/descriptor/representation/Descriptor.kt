/*
 * Descriptor.kt
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
package com.avail.descriptor.representation

import com.avail.compiler.AvailCodeGenerator
import com.avail.compiler.ModuleHeader
import com.avail.compiler.scanning.LexingState
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
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
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.methods.A_Sendable
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.hasElement
import com.avail.descriptor.sets.A_SetBin
import com.avail.descriptor.sets.LinearSetBinDescriptor.Companion.createLinearSetBinPair
import com.avail.descriptor.sets.LinearSetBinDescriptor.Companion.emptyLinearSetBin
import com.avail.descriptor.sets.SetDescriptor.SetIterator
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
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
import com.avail.utility.json.JSONWriter
import com.avail.utility.visitor.AvailSubobjectVisitor
import com.avail.utility.visitor.BeImmutableSubobjectVisitor
import com.avail.utility.visitor.BeSharedSubobjectVisitor
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Deque
import java.util.NoSuchElementException
import java.util.Spliterator
import java.util.TimerTask
import java.util.stream.Stream

/**
 * This is the primary subclass of [AbstractDescriptor]. It has the sibling
 * IndirectionDescriptor.
 *
 * When a new method is added in a subclass, it should be added with the
 * [@Override][Override] annotation. That way the project will indicate errors
 * until an abstract declaration is added to [AbstractDescriptor], a default
 * implementation is added to `Descriptor`, and a redirecting implementation is
 * added to [IndirectionDescriptor]. Any code attempting to send the
 * corresponding message to an [AvailObject] will also indicate a problem until
 * a suitable implementation is added to AvailObject.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Descriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
abstract class Descriptor protected constructor (
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?)
: AbstractDescriptor(
	mutability,
	typeTag,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{
	/**
	 * A special enumeration used to visit all object slots within an instance
	 * of the receiver.
	 */
	internal enum class FakeObjectSlotsForScanning : ObjectSlotsEnum
	{
		/**
		 * An indexed object slot that makes it easy to visit all object slots.
		 */
		ALL_OBJECT_SLOTS_
	}

	override fun o_AcceptsArgTypesFromFunctionType (
		self: AvailObject,
		functionType: A_Type): Boolean = unsupported

	override fun o_AcceptsListOfArgTypes (
		self: AvailObject,
		argTypes: List<A_Type>): Boolean = unsupported

	override fun o_AcceptsListOfArgValues (
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean = unsupported

	override fun o_AcceptsTupleOfArgTypes (
		self: AvailObject,
		argTypes: A_Tuple): Boolean = unsupported

	override fun o_AcceptsTupleOfArguments (
		self: AvailObject,
		arguments: A_Tuple): Boolean = unsupported

	override fun o_AddDependentChunk (
		self: AvailObject,
		chunk: L2Chunk): Unit = unsupported

	override fun o_AddUnloadFunction (
		self: AvailObject,
		unloadFunction: A_Function): Unit = unsupported

	override fun o_AdjustPcAndStackp (
		self: AvailObject,
		pc: Int,
		stackp: Int): Unit = unsupported

	override fun o_AllAncestors (self: AvailObject): A_Set = unsupported

	override fun o_ArgumentRestrictionSets (self: AvailObject): A_Tuple =
		unsupported

	override fun o_AtomName (self: AvailObject): A_String = unsupported

	override fun o_AddDefinitionParsingPlan (
		self: AvailObject,
		plan: A_DefinitionParsingPlan): Unit = unsupported

	override fun o_AddImportedName (
		self: AvailObject,
		trueName: A_Atom): Unit = unsupported

	override fun o_AddImportedNames (
		self: AvailObject,
		trueNames: A_Set): Unit = unsupported

	override fun o_AddPrivateName (
		self: AvailObject,
		trueName: A_Atom): Unit = unsupported

	override fun o_FrameAt (
		self: AvailObject,
		index: Int): AvailObject = unsupported

	override fun o_FrameAtPut (
		self: AvailObject,
		index: Int,
		value: AvailObject): AvailObject = unsupported

	override fun o_AsNativeString (self: AvailObject): String = unsupported

	override fun o_AsSet (self: AvailObject): A_Set = unsupported

	override fun o_AsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_ArgumentsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_ApparentSendName (self: AvailObject): A_Atom = unsupported

	override fun o_AllParsingPlansInProgress (self: AvailObject): A_Map =
		unsupported

	override fun o_ArgsTupleType (self: AvailObject): A_Type =
		unsupported

	override fun o_AddSemanticRestriction (
			self: AvailObject,
			restriction: A_SemanticRestriction): Unit =
		unsupported

	override fun o_AddSealedArgumentsType (
		self: AvailObject,
		typeTuple: A_Tuple): Unit = unsupported

	override fun o_AddConstantBinding (
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable): Unit = unsupported

	override fun o_AddVariableBinding (
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable): Unit = unsupported

	override fun o_AddToDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_AddToFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_AsBigInteger (self: AvailObject): BigInteger = unsupported

	override fun o_AppendCanDestroy (
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple = unsupported

	override fun o_ArgumentsListNode (self: AvailObject): A_Phrase =
		unsupported

	override fun o_AddSeal (
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple): Unit = unsupported

	override fun o_AvailLoader (self: AvailObject): AvailLoader? = unsupported

	override fun o_SetAvailLoader (
		self: AvailObject,
		loader: AvailLoader?): Unit = unsupported

	override fun o_AddWriteReactor (
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor): Unit = unsupported

	override fun o_AddPrivateNames (
		self: AvailObject,
		trueNames: A_Set): Unit = unsupported

	@Throws(SignatureException::class)
	override fun o_MethodAddDefinition (
		self: AvailObject,
		definition: A_Definition): Unit = unsupported

	override fun o_AddGrammaticalRestriction (
			self: AvailObject,
			grammaticalRestriction: A_GrammaticalRestriction): Unit =
		unsupported

	override fun o_AddToInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_AddToIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_ModuleAddGrammaticalRestriction (
			self: AvailObject,
			grammaticalRestriction: A_GrammaticalRestriction): Unit =
		unsupported

	override fun o_ModuleAddDefinition (
		self: AvailObject,
		definition: A_Definition): Unit = unsupported

	override fun o_IntroduceNewName (
		self: AvailObject,
		trueName: A_Atom): Unit = unsupported

	override fun o_BinElementAt (self: AvailObject, index: Int): AvailObject =
		unsupported

	override fun o_SetBreakpointBlock (
		self: AvailObject,
		value: AvailObject): Unit = unsupported

	override fun o_BuildFilteredBundleTree (self: AvailObject): A_BundleTree =
		unsupported

	override fun o_CompareFromToWithStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithAnyTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithByteStringStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithByteTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithIntegerIntervalTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithRepeatedElementTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithNybbleTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithObjectTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_CompareFromToWithTwoByteStringStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int): Boolean = unsupported

	override fun o_ComputeHashFromTo (
		self: AvailObject,
		start: Int,
		end: Int): Int = unsupported

	override fun o_ConcatenateTuplesCanDestroy (
		self: AvailObject,
		canDestroy: Boolean): A_Tuple = unsupported

	override fun o_ConstantTypeAt (
		self: AvailObject,
		index: Int): A_Type = unsupported

	override fun o_SetContinuation (
		self: AvailObject,
		value: A_Continuation): Unit = unsupported

	override fun o_CopyTupleFromToCanDestroy (
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple = unsupported

	override fun o_CouldEverBeInvokedWith (
			self: AvailObject,
			argRestrictions: List<TypeRestriction>): Boolean =
		unsupported

	override fun o_DebugLog (self: AvailObject): StringBuilder = unsupported

	override fun o_DivideCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_DivideIntoInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_DivideIntoIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_SetExecutionState (
		self: AvailObject, value: ExecutionState): Unit = unsupported

	override fun o_ExtractNybbleFromTupleAt (
		self: AvailObject, index: Int): Byte = unsupported

	override fun o_FilterByTypes (
		self: AvailObject,
		argTypes: List<A_Type>): List<A_Definition> = unsupported

	override fun o_HasElement (
		self: AvailObject,
		elementObject: A_BasicObject): Boolean = unsupported

	override fun o_HashFromTo (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): Int = unsupported

	override fun o_SetHashOrZero (self: AvailObject, value: Int): Unit =
		unsupported

	override fun o_HasKey (
		self: AvailObject,
		keyObject: A_BasicObject): Boolean = unsupported

	override fun o_DefinitionsAtOrBelow (
			self: AvailObject,
			argRestrictions: List<TypeRestriction>): List<A_Definition> =
		unsupported

	override fun o_IncludesDefinition (
		self: AvailObject,
		definition: A_Definition): Boolean = unsupported

	override fun o_SetInterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag): Unit = unsupported

	override fun o_CountdownToReoptimize (
		self: AvailObject,
		value: Long
	): Unit = unsupported

	override fun o_IsSubsetOf (
		self: AvailObject,
		another: A_Set): Boolean = unsupported

	override fun o_IsSubtypeOf (
		self: AvailObject,
		aType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfFiberType (
		self: AvailObject,
		aType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfMapType (
		self: AvailObject,
		aMapType: AvailObject): Boolean = unsupported

	override fun o_IsSupertypeOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): Boolean = unsupported

	override fun o_IsSupertypeOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfPrimitiveTypeEnum (
			self: AvailObject,
			primitiveTypeEnum: TypeDescriptor.Types): Boolean =
		unsupported

	override fun o_IsSupertypeOfSetType (
		self: AvailObject,
		aSetType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfEnumerationType (
		self: AvailObject,
		anEnumerationType: A_Type): Boolean = unsupported

	override fun o_LevelTwoChunkOffset (
		self: AvailObject,
		chunk: L2Chunk,
		offset: Int): Unit = unsupported

	override fun o_LiteralAt (self: AvailObject, index: Int): AvailObject =
		unsupported

	override fun o_LocalTypeAt (self: AvailObject, index: Int): A_Type =
		unsupported

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByTypesFromTuple (
		self: AvailObject,
		argumentTypeTuple: A_Tuple): A_Definition = unsupported

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByValuesFromList (
			self: AvailObject,
			argumentList: List<A_BasicObject>): A_Definition =
		unsupported

	override fun o_MapAt (
		self: AvailObject,
		keyObject: A_BasicObject): AvailObject = unsupported

	override fun o_MapAtPuttingCanDestroy (
		self: AvailObject,
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Map = unsupported

	override fun o_MapAtReplacingCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		canDestroy: Boolean): A_Map = unsupported

	override fun o_MapWithoutKeyCanDestroy (
		self: AvailObject,
		keyObject: A_BasicObject,
		canDestroy: Boolean): A_Map = unsupported

	override fun o_MinusCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_MultiplyByInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_MultiplyByIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_OptionallyNilOuterVar (
		self: AvailObject,
		index: Int): Boolean = unsupported

	override fun o_OuterTypeAt (
		self: AvailObject,
		index: Int): A_Type = unsupported

	override fun o_OuterVarAt (self: AvailObject, index: Int): AvailObject =
		unsupported

	override fun o_OuterVarAtPut (
		self: AvailObject,
		index: Int,
		value: AvailObject): Unit = unsupported

	override fun o_PlusCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_SetPriority (self: AvailObject, value: Int): Unit =
		unsupported

	override fun o_SetFiberGlobals (self: AvailObject, globals: A_Map): Unit =
		unsupported

	override fun o_RawByteForCharacterAt (
		self: AvailObject,
		index: Int): Short = unsupported

	override fun o_RawSignedIntegerAt (self: AvailObject, index: Int): Int =
		unsupported

	override fun o_RawSignedIntegerAtPut (
		self: AvailObject,
		index: Int,
		value: Int): Unit = unsupported

	override fun o_RawUnsignedIntegerAt (self: AvailObject, index: Int): Long =
		unsupported

	override fun o_RawUnsignedIntegerAtPut (
		self: AvailObject,
		index: Int,
		value: Int): Unit = unsupported

	override fun o_RemoveDependentChunk (
		self: AvailObject,
		chunk: L2Chunk): Unit = unsupported

	override fun o_RemoveFrom (
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit): Unit = unsupported

	override fun o_RemoveDefinition (
		self: AvailObject,
		definition: A_Definition): Unit = unsupported

	override fun o_RemoveGrammaticalRestriction (
			self: AvailObject,
			obsoleteRestriction: A_GrammaticalRestriction): Unit =
		unsupported

	override fun o_ResolveForward (
		self: AvailObject,
		forwardDefinition: A_BasicObject): Unit = unsupported

	/**
	 * Visit all of the object's object slots, passing the parent and child
	 * objects to the provided visitor.
	 *
	 * @param self
	 *   The object to scan.
	 * @param visitor
	 *   The visitor to invoke.
	 */
	override fun o_ScanSubobjects (
		self: AvailObject,
		visitor: AvailSubobjectVisitor)
	{
		val limit = self.objectSlotsCount()
		for (i in 1 .. limit)
		{
			val child = self.slot(
				FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
				i)
			val replacementChild = visitor.invoke(child)
			if (replacementChild !== child)
			{
				self.writeBackSlot(
					FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
					i,
					replacementChild)
			}
		}
	}

	override fun o_SetIntersectionCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set = unsupported

	override fun o_SetMinusCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set = unsupported

	override fun o_SetUnionCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set = unsupported

	@Throws(VariableSetException::class)
	override fun o_SetValue (self: AvailObject, newValue: A_BasicObject): Unit =
		unsupported

	override fun o_SetValueNoCheck (
		self: AvailObject,
		newValue: A_BasicObject): Unit = unsupported

	override fun o_SetWithElementCanDestroy (
		self: AvailObject,
		newElementObject: A_BasicObject,
		canDestroy: Boolean): A_Set = unsupported

	override fun o_SetWithoutElementCanDestroy (
		self: AvailObject,
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean): A_Set = unsupported

	override fun o_StackAt (self: AvailObject, slotIndex: Int): AvailObject =
		unsupported

	override fun o_SetStartingChunkAndReoptimizationCountdown (
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long): Unit = unsupported

	override fun o_SubtractFromInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_SubtractFromIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_TimesCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_TrueNamesForStringName (
		self: AvailObject,
		stringName: A_String): A_Set = unsupported

	override fun o_TupleReverse (self: AvailObject): A_Tuple = unsupported

	override fun o_TupleAt (self: AvailObject, index: Int): AvailObject =
		unsupported

	override fun o_TupleAtPuttingCanDestroy (
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple = unsupported

	override fun o_TupleIntAt (self: AvailObject, index: Int): Int =
		unsupported

	override fun o_TupleLongAt (self: AvailObject, index: Int): Long =
		unsupported

	override fun o_TypeAtIndex (self: AvailObject, index: Int): A_Type =
		unsupported

	override fun o_TypeIntersection (
			self: AvailObject,
			another: A_Type): A_Type =
		unsupported

	override fun o_TypeIntersectionOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfFiberType (
		self: AvailObject,
		aFiberType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfMapType (
		self: AvailObject,
		aMapType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): A_Type = unsupported

	override fun o_TypeIntersectionOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfSetType (
		self: AvailObject,
		aSetType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): A_Type = unsupported

	override fun o_TypeUnion (self: AvailObject, another: A_Type): A_Type =
		unsupported

	override fun o_TypeUnionOfFiberType (
		self: AvailObject,
		aFiberType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfMapType (
		self: AvailObject,
		aMapType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): A_Type = unsupported

	override fun o_TypeUnionOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfSetType (
		self: AvailObject,
		aSetType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): A_Type = unsupported

	override fun o_UnionOfTypesAtThrough (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type = unsupported

	override fun o_BitsPerEntry (self: AvailObject): Int = unsupported

	override fun o_BodyBlock (self: AvailObject): A_Function = unsupported

	override fun o_BodySignature (self: AvailObject): A_Type = unsupported

	override fun o_BreakpointBlock (self: AvailObject): A_BasicObject =
		unsupported

	override fun o_Caller (self: AvailObject): A_Continuation = unsupported

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap (
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject): Unit = unsupported

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicRemoveFromMap (
		self: AvailObject,
		key: A_BasicObject): Unit = unsupported

	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey (
		self: AvailObject,
		key: A_BasicObject): Boolean = unsupported

	override fun o_ClearValue (self: AvailObject): Unit = unsupported

	override fun o_Function (self: AvailObject): A_Function = unsupported

	override fun o_FunctionType (self: AvailObject): A_Type = unsupported

	override fun o_Code (self: AvailObject): A_RawFunction = unsupported

	override fun o_CodePoint (self: AvailObject): Int = unsupported

	override fun o_LazyComplete (self: AvailObject): A_Set = unsupported

	override fun o_ConstantBindings (self: AvailObject): A_Map = unsupported

	override fun o_ContentType (self: AvailObject): A_Type = unsupported

	override fun o_Continuation (self: AvailObject): A_Continuation =
		unsupported

	override fun o_CopyAsMutableIntTuple (self: AvailObject): A_Tuple =
		unsupported

	override fun o_CopyAsMutableLongTuple (self: AvailObject): A_Tuple =
		unsupported

	override fun o_CopyAsMutableObjectTuple (self: AvailObject): A_Tuple =
		unsupported

	override fun o_DefaultType (self: AvailObject): A_Type = unsupported

	override fun o_EnsureMutable (self: AvailObject): A_Continuation =
		unsupported

	override fun o_ExecutionState (self: AvailObject): ExecutionState =
		unsupported

	override fun o_Expand (
		self: AvailObject,
		module: A_Module): Unit = unsupported

	override fun o_ExtractBoolean (self: AvailObject): Boolean = unsupported

	override fun o_ExtractUnsignedByte (self: AvailObject): Short = unsupported

	override fun o_ExtractDouble (self: AvailObject): Double = unsupported

	override fun o_ExtractFloat (self: AvailObject): Float = unsupported

	override fun o_ExtractInt (self: AvailObject): Int = unsupported

	/**
	 * Extract a 64-bit signed Java `long` from the specified Avail
	 * [integer][IntegerDescriptor].
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   A 64-bit signed Java `long`
	 */
	override fun o_ExtractLong (self: AvailObject): Long = unsupported

	override fun o_ExtractNybble (self: AvailObject): Byte = unsupported

	override fun o_FieldMap (self: AvailObject): A_Map = unsupported

	override fun o_FieldTypeMap (self: AvailObject): A_Map = unsupported

	@Throws(VariableGetException::class)
	override fun o_GetValue (self: AvailObject): AvailObject = unsupported

	override fun o_HashOrZero (self: AvailObject): Int = unsupported

	override fun o_HasGrammaticalRestrictions (self: AvailObject): Boolean =
		unsupported

	override fun o_DefinitionsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_LazyIncomplete (self: AvailObject): A_Map = unsupported

	override fun o_DecrementCountdownToReoptimize (
		self: AvailObject,
		continuation: (Boolean)->Unit): Unit =
		unsupported

	override fun o_IsAbstract (self: AvailObject): Boolean = unsupported

	override fun o_IsAbstractDefinition (self: AvailObject): Boolean =
		unsupported

	override fun o_IsFinite (self: AvailObject): Boolean = unsupported

	override fun o_IsForwardDefinition (self: AvailObject): Boolean =
		unsupported

	override fun o_IsInstanceMeta (self: AvailObject): Boolean = false

	override fun o_IsMethodDefinition (self: AvailObject): Boolean = unsupported

	override fun o_IsPositive (self: AvailObject): Boolean = unsupported

	override fun o_KeysAsSet (self: AvailObject): A_Set = unsupported

	override fun o_KeyType (self: AvailObject): A_Type = unsupported

	override fun o_LevelTwoChunk (self: AvailObject): L2Chunk = unsupported

	override fun o_LevelTwoOffset (self: AvailObject): Int = unsupported

	override fun o_Literal (self: AvailObject): AvailObject = unsupported

	override fun o_LowerBound (self: AvailObject): A_Number = unsupported

	override fun o_LowerInclusive (self: AvailObject): Boolean = unsupported

	override fun o_MapSize (self: AvailObject): Int = unsupported

	override fun o_MaxStackDepth (self: AvailObject): Int = unsupported

	override fun o_Message (self: AvailObject): A_Atom = unsupported

	override fun o_MessagePart (self: AvailObject, index: Int): A_String =
		unsupported

	override fun o_MessageParts (self: AvailObject): A_Tuple = unsupported

	override fun o_MethodDefinitions (self: AvailObject): A_Set = unsupported

	override fun o_ImportedNames (self: AvailObject): A_Map = unsupported

	override fun o_NewNames (self: AvailObject): A_Map = unsupported

	override fun o_NumArgs (self: AvailObject): Int = unsupported

	override fun o_NumConstants (self: AvailObject): Int = unsupported

	override fun o_NumSlots (self: AvailObject): Int = unsupported

	override fun o_NumLiterals (self: AvailObject): Int = unsupported

	override fun o_NumLocals (self: AvailObject): Int = unsupported

	override fun o_NumOuters (self: AvailObject): Int = unsupported

	override fun o_NumOuterVars (self: AvailObject): Int = unsupported

	override fun o_Nybbles (self: AvailObject): A_Tuple = unsupported

	override fun o_Parent (self: AvailObject): A_BasicObject = unsupported

	override fun o_Pc (self: AvailObject): Int = unsupported

	override fun o_Priority (self: AvailObject): Int = unsupported

	override fun o_PrivateNames (self: AvailObject): A_Map = unsupported

	override fun o_FiberGlobals (self: AvailObject): A_Map = unsupported

	override fun o_GrammaticalRestrictions (self: AvailObject): A_Set =
		unsupported

	override fun o_ReturnType (self: AvailObject): A_Type = unsupported

	override fun o_SetSize (self: AvailObject): Int = unsupported

	override fun o_SizeRange (self: AvailObject): A_Type = unsupported

	override fun o_LazyActions (self: AvailObject): A_Map = unsupported

	override fun o_Stackp (self: AvailObject): Int = unsupported

	override fun o_Start (self: AvailObject): Int = unsupported

	override fun o_StartingChunk (self: AvailObject): L2Chunk = unsupported

	override fun o_String (self: AvailObject): A_String = unsupported

	override fun o_TokenType (self: AvailObject): TokenDescriptor.TokenType =
		unsupported

	override fun o_TrimExcessInts (self: AvailObject): Unit = unsupported

	override fun o_TupleSize (self: AvailObject): Int = unsupported

	override fun o_TypeTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_UpperBound (self: AvailObject): A_Number = unsupported

	override fun o_UpperInclusive (self: AvailObject): Boolean = unsupported

	override fun o_Value (self: AvailObject): AvailObject = unsupported

	override fun o_ValuesAsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_ValueType (self: AvailObject): A_Type = unsupported

	override fun o_VariableBindings (self: AvailObject): A_Map = unsupported

	override fun o_VisibleNames (self: AvailObject): A_Set = unsupported

	override fun o_Equals (
		self: AvailObject, another: A_BasicObject): Boolean =
		unsupported

	override fun o_EqualsAnyTuple (
		self: AvailObject,
		aTuple: A_Tuple) = false

	override fun o_EqualsByteString (
		self: AvailObject,
		aByteString: A_String) = false

	override fun o_EqualsByteTuple (
		self: AvailObject,
		aByteTuple: A_Tuple) = false

	override fun o_EqualsCharacterWithCodePoint (
		self: AvailObject,
		aCodePoint: Int) = false

	override fun o_EqualsFunction (
		self: AvailObject,
		aFunction: A_Function) = false

	override fun o_EqualsFiberType (
		self: AvailObject,
		aFiberType: A_Type) = false

	override fun o_EqualsFunctionType (
		self: AvailObject,
		aFunctionType: A_Type) = false

	override fun o_EqualsIntegerIntervalTuple (
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple) = false

	override fun o_EqualsIntTuple (
		self: AvailObject,
		anIntTuple: A_Tuple) = false

	override fun o_EqualsLongTuple (
		self: AvailObject,
		aLongTuple: A_Tuple) = false

	override fun o_EqualsSmallIntegerIntervalTuple (
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple) = false

	override fun o_EqualsRepeatedElementTuple (
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple) = false

	override fun o_EqualsCompiledCode (
		self: AvailObject,
		aCompiledCode: A_RawFunction) = false

	override fun o_EqualsVariable (
		self: AvailObject,
		aVariable: A_Variable) = false

	override fun o_EqualsVariableType (
		self: AvailObject,
		aType: A_Type) = false

	override fun o_EqualsContinuation (
		self: AvailObject,
		aContinuation: A_Continuation) = false

	override fun o_EqualsContinuationType (
		self: AvailObject,
		aContinuationType: A_Type) = false

	override fun o_EqualsCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type) = false

	override fun o_EqualsDouble (self: AvailObject, aDouble: Double) = false

	override fun o_EqualsFloat (self: AvailObject, aFloat: Float) = false

	override fun o_EqualsInfinity (self: AvailObject, sign: Sign) = false

	override fun o_EqualsInteger (
		self: AvailObject,
		anAvailInteger: AvailObject) = false

	override fun o_EqualsIntegerRangeType (
		self: AvailObject,
		another: A_Type) = false

	override fun o_EqualsMap (self: AvailObject, aMap: A_Map) = false

	override fun o_EqualsMapType (self: AvailObject, aMapType: A_Type) = false

	override fun o_EqualsNybbleTuple (
		self: AvailObject,
		aTuple: A_Tuple) = false

	override fun o_EqualsObject (
		self: AvailObject,
		anObject: AvailObject) = false

	override fun o_EqualsObjectTuple (
		self: AvailObject,
		aTuple: A_Tuple) = false

	override fun o_EqualsPhraseType (
		self: AvailObject,
		aPhraseType: A_Type) = false

	override fun o_EqualsPojo (self: AvailObject, aPojo: AvailObject) = false

	override fun o_EqualsPojoType (
		self: AvailObject,
		aPojoType: AvailObject) = false

	override fun o_EqualsPrimitiveType (
		self: AvailObject,
		aPrimitiveType: A_Type) = false

	override fun o_EqualsRawPojoFor (
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any?) = false

	override fun o_EqualsReverseTuple (
		self: AvailObject,
		aTuple: A_Tuple) = false

	override fun o_EqualsSet (self: AvailObject, aSet: A_Set) = false

	override fun o_EqualsSetType (self: AvailObject, aSetType: A_Type) = false

	override fun o_EqualsTupleType (
		self: AvailObject,
		aTupleType: A_Type) = false

	override fun o_EqualsTwoByteString (
		self: AvailObject,
		aString: A_String) = false

	override fun o_HasObjectInstance (
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = unsupported

	override fun o_IsBetterRepresentationThan (
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than the
		// second one
		val objectCost = self.objectSlotsCount() + self.integerSlotsCount()
		val anotherCost =
			anotherObject.objectSlotsCount() + anotherObject.integerSlotsCount()
		return objectCost < anotherCost
	}

	/**
	 * Given two objects that are known to be equal, the second of which is in
	 * the form of a tuple type, is the first one in a better form than the
	 * second one?
	 *
	 * Explanation: This must be called with a tuple type as the second
	 * argument, but the two arguments must also be equal. All alternative
	 * implementations of tuple types should re-implement this method.
	 */
	override fun o_RepresentationCostOfTupleType (self: AvailObject): Int =
		unsupported

	override fun o_IsInstanceOfKind (self: AvailObject, aType: A_Type) =
		self.kind().isSubtypeOf(aType)

	override fun o_Hash (self: AvailObject): Int =
		// Answer a 32-bit long that is always the same for equal objects, but
		// statistically different for different objects.
		unsupported

	override fun o_IsFunction (self: AvailObject) = false

	override fun o_MakeImmutable (self: AvailObject): AvailObject
	{
		// Make the object immutable. If I was mutable I have to scan my
		// children and make them immutable as well (recursively down to
		// immutable descendants).
		if (isMutable)
		{
			self.setDescriptor(self.descriptor().immutable())
			self.makeSubobjectsImmutable()
		}
		return self
	}

	override fun o_MakeShared (self: AvailObject): AvailObject
	{
		// Make the object shared. If I wasn't shared I have to scan my
		// children and make them shared as well (recursively down to
		// shared descendants).
		if (!isShared)
		{
			self.setDescriptor(self.descriptor().shared())
			self.makeSubobjectsShared()
		}
		return self
	}

	/**
	 * {@inheritDoc}
	 *
	 * Make my subobjects be immutable. Don't change my own mutability state.
	 * Also, ignore my mutability state, as it should be tested (and sometimes
	 * set preemptively to immutable) prior to invoking this method.
	 *
	 * @return
	 *   The receiving [AvailObject].
	 */
	override fun o_MakeSubobjectsImmutable (self: AvailObject): AvailObject
	{
		self.scanSubobjects(BeImmutableSubobjectVisitor)
		return self
	}

	/**
	 * {@inheritDoc}
	 *
	 * Make my subobjects be shared. Don't change my own mutability state. Also,
	 * ignore my mutability state, as it should be tested (and sometimes set
	 * preemptively to shared) prior to invoking this method.
	 *
	 * @return
	 *   The receiving [AvailObject].
	 */
	override fun o_MakeSubobjectsShared (self: AvailObject): AvailObject
	{
		self.scanSubobjects(BeSharedSubobjectVisitor)
		return self
	}

	override fun o_Kind (self: AvailObject): A_Type = unsupported

	override fun o_IsBoolean (self: AvailObject) = false

	override fun o_IsByteTuple (self: AvailObject) = false

	override fun o_IsCharacter (self: AvailObject) = false

	override fun o_IsIntTuple (self: AvailObject) = false

	override fun o_IsLongTuple (self: AvailObject) = false

	/**
	 * Is the specified [AvailObject] an Avail string?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is an Avail string, `false` otherwise.
	 */
	override fun o_IsString (self: AvailObject) = false

	// Overridden in IndirectionDescriptor to skip over indirections.
	override fun o_Traversed (self: AvailObject): AvailObject = self

	override fun o_IsMap (self: AvailObject) = false

	override fun o_IsUnsignedByte (self: AvailObject) = false

	override fun o_IsNybble (self: AvailObject) = false

	override fun o_IsSet (self: AvailObject) = false

	override fun o_SetBinAddingElementHashLevelCanDestroy (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean): A_SetBin
	{
		// Add the given element to this bin, potentially modifying it if
		// canDestroy and it's mutable. Answer the new bin. Note that the client
		// is responsible for marking elementObject as immutable if another
		// reference exists. In particular, the object is masquerading as a bin
		// of size one.
		if (self.equals(elementObject))
		{
			return self
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			elementObject.makeImmutable()
		}
		// Create a linear bin with two slots.
		return createLinearSetBinPair(myLevel, self, elementObject)
	}

	// Elements are treated as bins to save space, since bins are not
	// entirely first-class objects (i.e., they can't be added to sets.
	override fun o_BinHasElementWithHash (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int) = self.equals(elementObject)

	/**
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and `canDestroy`.
	 * In particular, an element is masquerading as a bin of size one, so the
	 * answer must be either the object or nil (to indicate a size zero bin).
	 *
	 * @param self
	 *   The set bin from which to remove the element.
	 * @param elementObject
	 *   The element to remove.
	 * @param elementObjectHash
	 *   The already-computed hash of the element to remove
	 * @param canDestroy
	 *   Whether this set bin can be destroyed or reused by this operation if
	 *   it's also mutable.
	 * @return
	 *   A set bin like the given object, but without the given elementObject,
	 *   if it was present.
	 */
	override fun o_BinRemoveElementHashLevelCanDestroy (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean): A_SetBin
	{
		if (self.equals(elementObject))
		{
			return emptyLinearSetBin(myLevel)
		}
		if (!canDestroy)
		{
			self.makeImmutable()
		}
		return self
	}

	/**
	 * Sets only use explicit bins for collisions, otherwise they store the
	 * element itself. This works because a bin can't be an element of a set.
	 *
	 * @param self
	 *   The set bin, or single value in this case, to test for being within the
	 *   given set.
	 * @param potentialSuperset
	 *   The set inside which to look for the given object.
	 * @return
	 *   Whether the object (acting as a singleton bin) was in the set.
	 */
	override fun o_IsBinSubsetOf (
		self: AvailObject,
		potentialSuperset: A_Set): Boolean
	{
		return potentialSuperset.hasElement(self)
	}

	// An object masquerading as a size one bin has a setBinHash which is the
	// sum of the elements' hashes, which in this case is just the object's
	// hash.
	override fun o_SetBinHash (self: AvailObject): Int = self.hash()

	// Answer how many elements this bin contains. By default, the object
	// acts as a bin of size one.
	override fun o_SetBinSize (self: AvailObject): Int = 1

	override fun o_IsTuple (self: AvailObject) = false

	override fun o_IsAtom (self: AvailObject) = false

	override fun o_IsExtendedInteger (self: AvailObject) = false

	override fun o_IsIntegerRangeType (self: AvailObject) = false

	override fun o_IsMapType (self: AvailObject) = false

	override fun o_IsSetType (self: AvailObject) = false

	override fun o_IsTupleType (self: AvailObject) = false

	override fun o_IsType (self: AvailObject) = false

	/**
	 * Answer an [iterator][Iterator] suitable for traversing the elements of
	 * the [object][AvailObject] with a Java *foreach* construct.
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   An [iterator][Iterator].
	 */
	override fun o_Iterator (self: AvailObject): Iterator<AvailObject> =
		unsupported

	override fun o_Spliterator (self: AvailObject): Spliterator<AvailObject> =
		unsupported

	override fun o_Stream (self: AvailObject): Stream<AvailObject> =
		unsupported

	override fun o_ParallelStream (self: AvailObject): Stream<AvailObject> =
		unsupported

	override fun o_ParsingInstructions (self: AvailObject): A_Tuple =
		unsupported

	override fun o_Expression (self: AvailObject): A_Phrase = unsupported

	override fun o_Variable (self: AvailObject): A_Phrase = unsupported

	override fun o_StatementsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_ResultType (self: AvailObject): A_Type = unsupported

	override fun o_NeededVariables (
		self: AvailObject,
		neededVariables: A_Tuple): Unit = unsupported

	override fun o_NeededVariables (self: AvailObject): A_Tuple = unsupported

	override fun o_Primitive (self: AvailObject): Primitive? = unsupported

	override fun o_DeclaredType (self: AvailObject): A_Type = unsupported

	override fun o_DeclarationKind (self: AvailObject): DeclarationKind =
		unsupported

	override fun o_TypeExpression (self: AvailObject): A_Phrase = unsupported

	override fun o_InitializationExpression (self: AvailObject): AvailObject =
		unsupported

	override fun o_LiteralObject (self: AvailObject): A_BasicObject =
		unsupported

	override fun o_Token (self: AvailObject): A_Token = unsupported

	override fun o_MarkerValue (self: AvailObject): A_BasicObject = unsupported

	override fun o_Bundle (self: AvailObject): A_Bundle = unsupported

	override fun o_ExpressionsTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_Declaration (self: AvailObject): A_Phrase = unsupported

	override fun o_EmitEffectOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator): Unit = unsupported

	override fun o_EmitValueOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator): Unit = unsupported

	override fun o_ChildrenMap (
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase): Unit = unsupported

	/**
	 * Visit my child phrases with the action.
	 */
	override fun o_ChildrenDo (
		self: AvailObject,
		action: (A_Phrase) -> Unit): Unit = unsupported

	override fun o_ValidateLocally (
		self: AvailObject,
		parent: A_Phrase?): Unit = unsupported

	override fun o_GenerateInModule (
		self: AvailObject,
		module: A_Module): A_RawFunction = unsupported

	override fun o_CopyWith (self: AvailObject, newPhrase: A_Phrase): A_Phrase =
		unsupported

	override fun o_CopyConcatenating (
		self: AvailObject,
		newListPhrase: A_Phrase): A_Phrase = unsupported

	override fun o_IsLastUse (self: AvailObject, isLastUse: Boolean): Unit =
		unsupported

	override fun o_IsLastUse (self: AvailObject): Boolean = unsupported

	override fun o_CopyMutablePhrase (self: AvailObject): A_Phrase = unsupported

	// Ordinary (non-bin, non-nil) objects act as set bins of size one.
	override fun o_BinUnionKind (self: AvailObject): A_Type = self.kind()

	override fun o_OutputPhrase (self: AvailObject): A_Phrase = unsupported

	override fun o_Statements (self: AvailObject): A_Tuple = unsupported

	override fun o_FlattenStatementsInto (
			self: AvailObject,
			accumulatedStatements: MutableList<A_Phrase>): Unit =
		unsupported

	override fun o_LineNumber (self: AvailObject): Int = unsupported

	override fun o_IsSetBin (self: AvailObject) = false

	override fun o_MapIterable (self: AvailObject): MapIterable = unsupported

	override fun o_DeclaredExceptions (self: AvailObject): A_Set = unsupported

	override fun o_IsInt (self: AvailObject) = false

	override fun o_IsLong (self: AvailObject) = false

	override fun o_EqualsInstanceTypeFor (
		self: AvailObject,
		anObject: AvailObject) = false

	override fun o_Instances (self: AvailObject): A_Set = unsupported

	override fun o_EqualsEnumerationWithSet (self: AvailObject, aSet: A_Set) =
		false

	override fun o_IsEnumeration (self: AvailObject) = false

	override fun o_IsInstanceOf (self: AvailObject, aType: A_Type): Boolean
	{
		return (
			if (aType.isEnumeration) aType.enumerationIncludesInstance(self)
			else self.isInstanceOfKind(aType))
	}

	override fun o_EnumerationIncludesInstance (
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = unsupported

	override fun o_ComputeSuperkind (self: AvailObject): A_Type = unsupported

	override fun o_SetAtomProperty (
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject): Unit = unsupported

	override fun o_GetAtomProperty (
		self: AvailObject,
		key: A_Atom): AvailObject = unsupported

	override fun o_EqualsEnumerationType (
		self: AvailObject,
		another: A_BasicObject) = false

	override fun o_ReadType (self: AvailObject): A_Type = unsupported

	override fun o_WriteType (self: AvailObject): A_Type = unsupported

	override fun o_Versions (self: AvailObject): A_Set = unsupported

	override fun o_PhraseExpressionType (self: AvailObject): A_Type =
		unsupported

	override fun o_PhraseTypeExpressionType (self: AvailObject): A_Type =
		unsupported

	override fun o_PhraseKind (self: AvailObject): PhraseKind = unsupported

	override fun o_PhraseKindIsUnder (
		self: AvailObject,
		expectedPhraseKind: PhraseKind): Boolean = unsupported

	override fun o_IsRawPojo (self: AvailObject) = false

	override fun o_RemoveSemanticRestriction (
		self: AvailObject,
		restriction: A_SemanticRestriction): Unit = unsupported

	override fun o_SemanticRestrictions (self: AvailObject): A_Set = unsupported

	override fun o_RemoveSealedArgumentsType (
		self: AvailObject,
		typeTuple: A_Tuple): Unit = unsupported

	override fun o_SealedArgumentsTypesTuple (self: AvailObject): A_Tuple =
		unsupported

	override fun o_ModuleAddSemanticRestriction (
			self: AvailObject,
			semanticRestriction: A_SemanticRestriction): Unit =
		unsupported

	override fun o_IsMethodEmpty (self: AvailObject): Boolean = unsupported

	override fun o_IsPojoSelfType (self: AvailObject) = false

	override fun o_PojoSelfType (self: AvailObject): A_Type = unsupported

	override fun o_JavaClass (self: AvailObject): AvailObject = unsupported

	override fun o_IsUnsignedShort (self: AvailObject) = false

	override fun o_ExtractUnsignedShort (self: AvailObject): Int = unsupported

	override fun o_IsFloat (self: AvailObject) = false

	override fun o_IsDouble (self: AvailObject) = false

	override fun o_RawPojo (self: AvailObject): AvailObject = unsupported

	override fun o_IsPojo (self: AvailObject) = false

	override fun o_IsPojoType (self: AvailObject) = false

	override fun o_NumericCompare (
		self: AvailObject,
		another: A_Number): Order = unsupported

	override fun o_NumericCompareToInfinity (
		self: AvailObject,
		sign: Sign): Order = unsupported

	override fun o_NumericCompareToDouble (
		self: AvailObject,
		aDouble: Double): Order = unsupported

	override fun o_NumericCompareToInteger (
		self: AvailObject,
		anInteger: AvailObject): Order = unsupported

	override fun o_SubtractFromDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_SubtractFromFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_MultiplyByDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_MultiplyByFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_DivideIntoDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_DivideIntoFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_LazyPrefilterMap (self: AvailObject): A_Map = unsupported

	override fun o_SerializerOperation (
		self: AvailObject): SerializerOperation = unsupported

	override fun o_MapBinAtHashPutLevelCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean): A_MapBin = unsupported

	override fun o_MapBinRemoveKeyHashCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean): A_MapBin = unsupported

	override fun o_MapBinAtHashReplacingLevelCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean): A_MapBin = unsupported

	override fun o_MapBinKeyUnionKind (self: AvailObject): A_Type = unsupported

	override fun o_MapBinValueUnionKind (self: AvailObject): A_Type =
		unsupported

	override fun o_IsHashedMapBin (self: AvailObject): Boolean = unsupported

	override fun o_MapBinAtHash (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int): AvailObject? = unsupported

	override fun o_MapBinKeysHash (self: AvailObject): Int = unsupported

	override fun o_MapBinSize (self: AvailObject): Int = unsupported

	override fun o_MapBinValuesHash (self: AvailObject): Int = unsupported

	override fun o_IssuingModule (self: AvailObject): A_Module = unsupported

	override fun o_IsPojoFusedType (self: AvailObject): Boolean = unsupported

	override fun o_IsSupertypeOfPojoBottomType (
		self: AvailObject,
		aPojoType: A_Type): Boolean = unsupported

	override fun o_EqualsPojoBottomType (self: AvailObject) = false

	override fun o_JavaAncestors (self: AvailObject): AvailObject = unsupported

	override fun o_TypeIntersectionOfPojoFusedType (
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfPojoUnfusedType (
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfPojoFusedType (
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfPojoUnfusedType (
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type = unsupported

	override fun o_IsPojoArrayType (self: AvailObject): Boolean = unsupported

	// Treat AvailObjects as opaque for most purposes. Pass them to Java
	// unmarshaled, but made shared.
	override fun o_MarshalToJava (
		self: AvailObject,
		classHint: Class<*>?): Any? = self.makeShared()

	override fun o_TypeVariables (self: AvailObject): A_Map = unsupported

	override fun o_EqualsPojoField (
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject) = false

	override fun o_IsSignedByte (self: AvailObject): Boolean = unsupported

	override fun o_IsSignedShort (self: AvailObject): Boolean = unsupported

	override fun o_ExtractSignedByte (self: AvailObject): Byte = unsupported

	override fun o_ExtractSignedShort (self: AvailObject): Short = unsupported

	override fun o_EqualsEqualityRawPojo (
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?) = false

	override fun <T : Any> o_JavaObject(self: AvailObject): T? =
		unsupported

	override fun o_LazyIncompleteCaseInsensitive (self: AvailObject): A_Map =
		unsupported

	override fun o_LowerCaseString (self: AvailObject): A_String = unsupported

	override fun o_InstanceCount (self: AvailObject): A_Number = unsupported

	override fun o_TotalInvocations (self: AvailObject): Long = unsupported

	override fun o_TallyInvocation (self: AvailObject): Unit = unsupported

	override fun o_FieldTypeTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_FieldTuple (self: AvailObject): A_Tuple = unsupported

	override fun o_LiteralType (self: AvailObject): A_Type = unsupported

	override fun o_TypeIntersectionOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): A_Type = unsupported

	override fun o_TypeIntersectionOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): A_Type = unsupported

	override fun o_TypeUnionOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = unsupported

	override fun o_IsTokenType (self: AvailObject) = false

	override fun o_IsLiteralTokenType (self: AvailObject) = false

	override fun o_IsLiteralToken (self: AvailObject) = false

	override fun o_IsSupertypeOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): Boolean = unsupported

	override fun o_IsSupertypeOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean = unsupported

	override fun o_EqualsTokenType (
		self: AvailObject,
		aTokenType: A_Type) = false

	override fun o_EqualsLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type) = false

	override fun o_EqualsObjectType (
		self: AvailObject,
		anObjectType: AvailObject) = false

	override fun o_EqualsToken (self: AvailObject, aToken: A_Token) = false

	override fun o_BitwiseAnd (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_BitwiseOr (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_BitwiseXor (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_Instance (self: AvailObject): AvailObject =
		unsupported

	override fun o_SetMethodName (
		self: AvailObject,
		methodName: A_String): Unit = unsupported

	override fun o_StartingLineNumber (self: AvailObject): Int = unsupported

	override fun o_OriginatingPhrase (self: AvailObject): A_Phrase = unsupported

	override fun o_Module (self: AvailObject): A_Module = unsupported

	override fun o_MethodName (self: AvailObject): A_String = unsupported

	override fun o_NameForDebugger (self: AvailObject): String
	{
		var typeName = this@Descriptor.javaClass.simpleName
		if (typeName.endsWith("Descriptor"))
		{
			typeName = typeName.substring(0, typeName.length - 10)
		}
		typeName += mutability.suffix
		return (
			if (self.showValueInNameForDebugger())
				"($typeName) = $self"
			else
				"($typeName)")
	}

	// Actual bins (instances of SetBinDescriptor's subclasses) and nil will
	// override this, but single non-null values act as a singleton bin.
	override fun o_BinElementsAreAllInstancesOfKind (
		self: AvailObject,
		kind: A_Type): Boolean = self.isInstanceOfKind(kind)

	override fun o_SetElementsAreAllInstancesOfKind (
		self: AvailObject,
		kind: AvailObject): Boolean = unsupported

	override fun o_MapBinIterable (self: AvailObject): MapIterable = unsupported

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		unsupported

	override fun o_BitShiftLeftTruncatingToBits (
		self: AvailObject,
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_SetBinIterator (self: AvailObject): SetIterator =
		// By default an object acts like a bin of size one.
		object : SetIterator()
		{
			/** Whether there are more elements.  */
			private var hasNext = true

			override fun next(): AvailObject
			{
				if (!hasNext)
				{
					throw NoSuchElementException()
				}
				hasNext = false
				return self
			}

			override fun hasNext(): Boolean
			{
				return hasNext
			}
		}

	override fun o_BitShift (
		self: AvailObject,
		shiftFactor: A_Number,
		canDestroy: Boolean): A_Number = unsupported

	override fun o_EqualsPhrase (
		self: AvailObject,
		aPhrase: A_Phrase) = false

	override fun o_StripMacro (self: AvailObject): A_Phrase = unsupported

	override fun o_DefinitionMethod (self: AvailObject): A_Method = unsupported

	override fun o_PrefixFunctions (self: AvailObject): A_Tuple = unsupported

	override fun o_EqualsByteArrayTuple (
		self: AvailObject,
		aByteArrayTuple: A_Tuple) = false

	override fun o_CompareFromToWithByteArrayTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_ByteArray (self: AvailObject): ByteArray =
		unsupported

	override fun o_IsByteArrayTuple (self: AvailObject) = false

	override fun o_UpdateForNewGrammaticalRestriction (
			self: AvailObject,
			planInProgress: A_ParsingPlanInProgress,
			treesToVisit: Deque<Pair<
				A_BundleTree,
				A_ParsingPlanInProgress>>): Unit =
		unsupported

	// Only bother to acquire the monitor if it's shared.
	override fun <T> o_Lock (self: AvailObject, body: () -> T): T =
		if (isShared) synchronized(self) { body() }
		else body()

	override fun o_ModuleName (self: AvailObject): A_String = unsupported

	override fun o_BundleMethod (self: AvailObject): A_Method = unsupported

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue (
		self: AvailObject,
		newValue: A_BasicObject): AvailObject = unsupported

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues (
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean = unsupported

	@Throws(VariableSetException::class)
	override fun o_CompareAndSwapValuesNoCheck (
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean = unsupported

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue (
		self: AvailObject,
		addend: A_Number): A_Number = unsupported

	override fun o_FailureContinuation (
		self: AvailObject): (Throwable) -> Unit = unsupported

	override fun o_ResultContinuation (
		self: AvailObject): (AvailObject) -> Unit = unsupported

	override fun o_InterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag): Boolean = unsupported

	override fun o_GetAndClearInterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag): Boolean = unsupported

	override fun o_GetAndSetSynchronizationFlag (
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean): Boolean = unsupported

	override fun o_FiberResult (self: AvailObject): AvailObject =
		unsupported

	override fun o_SetFiberResult (
		self: AvailObject,
		result: A_BasicObject): Unit = unsupported

	override fun o_JoiningFibers (self: AvailObject): A_Set = unsupported

	override fun o_WakeupTask (self: AvailObject): TimerTask? = unsupported

	override fun o_SetWakeupTask (self: AvailObject, task: TimerTask?): Unit =
		unsupported

	override fun o_SetJoiningFibers (self: AvailObject, joiners: A_Set): Unit =
		unsupported

	override fun o_HeritableFiberGlobals (self: AvailObject): A_Map =
		unsupported

	override fun o_SetHeritableFiberGlobals (
		self: AvailObject,
		globals: A_Map): Unit = unsupported

	override fun o_GeneralFlag (self: AvailObject, flag: GeneralFlag): Boolean =
		unsupported

	override fun o_SetGeneralFlag (self: AvailObject, flag: GeneralFlag): Unit =
		unsupported

	override fun o_ClearGeneralFlag (
		self: AvailObject,
		flag: GeneralFlag): Unit = unsupported

	override fun o_ByteBuffer (self: AvailObject): ByteBuffer =
		unsupported

	override fun o_EqualsByteBufferTuple (
		self: AvailObject,
		aByteBufferTuple: A_Tuple) = false

	override fun o_CompareFromToWithByteBufferTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_IsByteBufferTuple (self: AvailObject) = false

	override fun o_FiberName (self: AvailObject): A_String =
		unsupported

	override fun o_FiberNameSupplier (
		self: AvailObject,
		supplier: () -> A_String): Unit = unsupported

	override fun o_Bundles (self: AvailObject): A_Set = unsupported

	override fun o_MethodAddBundle (self: AvailObject, bundle: A_Bundle): Unit =
		unsupported

	override fun o_MethodRemoveBundle (
		self: AvailObject,
		bundle: A_Bundle
	): Unit = unsupported

	override fun o_DefinitionModule (self: AvailObject): A_Module = unsupported

	override fun o_DefinitionModuleName (self: AvailObject): A_String =
		unsupported

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate (self: AvailObject): A_Bundle = unsupported

	override fun o_BundleOrNil (self: AvailObject): A_Bundle = unsupported

	override fun o_EntryPoints (self: AvailObject): A_Map = unsupported

	override fun o_RestrictedBundle (self: AvailObject): A_Bundle = unsupported

	override fun o_TreeTupleLevel (self: AvailObject): Int = unsupported

	override fun o_ChildCount (self: AvailObject): Int = unsupported

	override fun o_ChildAt (self: AvailObject, childIndex: Int): A_Tuple =
		unsupported

	override fun o_ConcatenateWith (
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple = unsupported

	override fun o_ReplaceFirstChild (
		self: AvailObject,
		newFirst: A_Tuple): A_Tuple = unsupported

	override fun o_IsByteString (self: AvailObject) = false

	override fun o_IsTwoByteString (self: AvailObject) = false

	override fun o_IsIntegerIntervalTuple (self: AvailObject) = false

	override fun o_IsSmallIntegerIntervalTuple (self: AvailObject) = false

	override fun o_IsRepeatedElementTuple (self: AvailObject) = false

	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor (self: AvailObject, key: A_Atom): Unit =
		unsupported

	override fun o_TraceFlag (self: AvailObject, flag: TraceFlag): Boolean =
		unsupported

	override fun o_SetTraceFlag (self: AvailObject, flag: TraceFlag): Unit =
		unsupported

	override fun o_ClearTraceFlag (self: AvailObject, flag: TraceFlag): Unit =
		unsupported

	override fun o_RecordVariableAccess (
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean): Unit = unsupported

	override fun o_VariablesReadBeforeWritten (self: AvailObject): A_Set =
		unsupported

	override fun o_VariablesWritten (self: AvailObject): A_Set =
		unsupported

	override fun o_ValidWriteReactorFunctions (self: AvailObject): A_Set =
		unsupported

	override fun o_ReplacingCaller (
		self: AvailObject,
		newCaller: A_Continuation): A_Continuation = unsupported

	override fun o_WhenContinuationIsAvailableDo (
		self: AvailObject,
		whenReified: (A_Continuation) -> Unit): Unit = unsupported

	override fun o_GetAndClearReificationWaiters (
		self: AvailObject): A_Set = unsupported

	// Only types should be tested for being bottom.
	override fun o_IsBottom (self: AvailObject): Boolean = unsupported

	// Only types should be tested for being vacuous.
	override fun o_IsVacuousType (self: AvailObject): Boolean = unsupported

	// Only types should be tested for being top.
	override fun o_IsTop (self: AvailObject): Boolean = unsupported

	// Only atoms should be tested for being special.
	override fun o_IsAtomSpecial (self: AvailObject): Boolean = unsupported

	override fun o_HasValue (self: AvailObject): Boolean = unsupported

	override fun o_ExportedNames (self: AvailObject): A_Set = unsupported

	override fun o_IsInitializedWriteOnceVariable (self: AvailObject): Boolean =
		unsupported

	override fun o_TransferIntoByteBuffer (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer): Unit = unsupported

	override fun o_TupleElementsInRangeAreInstancesOf (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean = unsupported

	override fun o_IsNumericallyIntegral (self: AvailObject): Boolean =
		unsupported

	override fun o_TextInterface (self: AvailObject): TextInterface =
		unsupported

	override fun o_SetTextInterface (
		self: AvailObject,
		textInterface: TextInterface): Unit = unsupported

	override fun o_WriteTo (
		self: AvailObject,
		writer: JSONWriter): Unit = unsupported

	override fun o_WriteSummaryTo (self: AvailObject, writer: JSONWriter) =
		self.writeTo(writer)

	override fun o_TypeIntersectionOfPrimitiveTypeEnum (
			self: AvailObject,
			primitiveTypeEnum: TypeDescriptor.Types): A_Type =
		unsupported

	override fun o_TypeUnionOfPrimitiveTypeEnum (
			self: AvailObject,
			primitiveTypeEnum: TypeDescriptor.Types): A_Type =
		unsupported

	override fun o_TupleOfTypesFromTo (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple = unsupported

	override fun o_List (self: AvailObject): A_Phrase = unsupported

	override fun o_Permutation (self: AvailObject): A_Tuple =
		unsupported

	override fun o_EmitAllValuesOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator): Unit = unsupported

	override fun o_SuperUnionType (self: AvailObject): A_Type = unsupported

	override fun o_HasSuperCast (self: AvailObject): Boolean = unsupported

	override fun o_MacrosTuple (self: AvailObject): A_Tuple =
		unsupported

	override fun o_LookupMacroByPhraseTuple (
		self: AvailObject,
		argumentPhraseTuple: A_Tuple): A_Tuple = unsupported

	override fun o_ExpressionAt (self: AvailObject, index: Int): A_Phrase =
		unsupported

	override fun o_ExpressionsSize (self: AvailObject): Int = unsupported

	override fun o_ParsingPc (self: AvailObject): Int = unsupported

	override fun o_IsMacroSubstitutionNode (self: AvailObject): Boolean =
		unsupported

	override fun o_LastExpression (self: AvailObject): A_Phrase = unsupported

	override fun o_MessageSplitter (self: AvailObject): MessageSplitter =
		unsupported

	override fun o_StatementsDo (
			self: AvailObject,
			continuation: (A_Phrase) -> Unit): Unit =
		unsupported

	override fun o_MacroOriginalSendNode (self: AvailObject): A_Phrase =
		unsupported

	override fun o_EqualsInt (self: AvailObject, theInt: Int) = false

	override fun o_Tokens (self: AvailObject): A_Tuple = unsupported

	override fun o_ChooseBundle (
		self: AvailObject,
		currentModule: A_Module): A_Bundle = unsupported

	override fun o_SetValueWasStablyComputed (
		self: AvailObject,
		wasStablyComputed: Boolean): Unit = unsupported

	override fun o_ValueWasStablyComputed (self: AvailObject): Boolean =
		unsupported

	override fun o_UniqueId (self: AvailObject): Long = unsupported

	override fun o_Definition (self: AvailObject): A_Definition =
		unsupported

	override fun o_NameHighlightingPc (self: AvailObject): String =
		unsupported

	override fun o_SetIntersects (self: AvailObject, otherSet: A_Set): Boolean =
		unsupported

	override fun o_RemovePlanForSendable (
		self: AvailObject,
		sendable: A_Sendable
	): Unit = unsupported

	override fun o_DefinitionParsingPlans (self: AvailObject): A_Map =
		unsupported

	override fun o_EqualsListNodeType (
		self: AvailObject,
		aListNodeType: A_Type) = false

	override fun o_SubexpressionsTupleType (self: AvailObject): A_Type =
		unsupported

	override fun o_IsSupertypeOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): Boolean = unsupported

	override fun o_TypeUnionOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): A_Type = unsupported

	override fun o_LazyTypeFilterTreePojo (self: AvailObject): A_BasicObject =
		unsupported

	override fun o_AddPlanInProgress (
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress): Unit = unsupported

	override fun o_ParsingSignature (self: AvailObject): A_Type =
		unsupported

	override fun o_RemovePlanInProgress (
		self: AvailObject, planInProgress: A_ParsingPlanInProgress): Unit =
		unsupported

	override fun o_ComputeTypeTag (self: AvailObject): TypeTag = unsupported

	override fun o_FieldAt (
		self: AvailObject,
		field: A_Atom): AvailObject = unsupported

	override fun o_FieldAtOrNull (
		self: AvailObject,
		field: A_Atom): AvailObject? = unsupported

	override fun o_FieldAtPuttingCanDestroy (
		self: AvailObject,
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean): A_BasicObject = unsupported

	override fun o_FieldTypeAt (
		self: AvailObject,
		field: A_Atom): A_Type = unsupported

	override fun o_FieldTypeAtOrNull (
		self: AvailObject,
		field: A_Atom): A_Type? = unsupported

	override fun o_ParsingPlan (self: AvailObject): A_DefinitionParsingPlan =
		unsupported

	override fun o_CompareFromToWithIntTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int): Boolean = unsupported

	override fun o_LexerMethod (self: AvailObject): A_Method = unsupported

	override fun o_LexerFilterFunction (self: AvailObject): A_Function =
		unsupported

	override fun o_LexerBodyFunction (self: AvailObject): A_Function =
		unsupported

	override fun o_SetLexer (self: AvailObject, lexer: A_Lexer): Unit =
		unsupported

	override fun o_AddLexer (self: AvailObject, lexer: A_Lexer): Unit =
		unsupported

	override fun o_NextLexingState (self: AvailObject): LexingState =
		unsupported

	override fun o_NextLexingStatePojo (self: AvailObject): AvailObject =
		unsupported

	override fun o_SetNextLexingStateFromPrior (
		self: AvailObject,
		priorLexingState: LexingState): Unit = unsupported

	override fun o_TupleCodePointAt (self: AvailObject, index: Int): Int =
		unsupported

	override fun o_IsGlobal (self: AvailObject): Boolean = unsupported

	override fun o_GlobalModule (self: AvailObject): A_Module = unsupported

	override fun o_GlobalName (self: AvailObject): A_String = unsupported

	override fun o_CreateLexicalScanner (self: AvailObject): LexicalScanner =
		unsupported

	override fun o_Lexer (self: AvailObject): A_Lexer = unsupported

	override fun o_SetSuspendingFunction (
		self: AvailObject,
		suspendingFunction: A_Function): Unit = unsupported

	override fun o_SuspendingFunction (self: AvailObject): A_Function =
		unsupported

	override fun o_IsBackwardJump (self: AvailObject): Boolean =
		unsupported

	override fun o_LatestBackwardJump (self: AvailObject): A_BundleTree =
		unsupported

	override fun o_HasBackwardJump (self: AvailObject): Boolean = unsupported

	override fun o_IsSourceOfCycle (self: AvailObject): Boolean = unsupported

	override fun o_IsSourceOfCycle (
		self: AvailObject,
		isSourceOfCycle: Boolean): Unit = unsupported

	override fun o_ReturnerCheckStat (self: AvailObject): Statistic =
		unsupported

	override fun o_ReturneeCheckStat (self: AvailObject): Statistic =
		unsupported

	override fun o_NumNybbles (self: AvailObject): Int = unsupported

	override fun o_LineNumberEncodedDeltas (self: AvailObject): A_Tuple =
		unsupported

	override fun o_CurrentLineNumber (self: AvailObject): Int = unsupported

	override fun o_FiberResultType (self: AvailObject): A_Type = unsupported

	override fun o_TestingTree (
			self: AvailObject): LookupTree<A_Definition, A_Tuple> =
		unsupported

	override fun o_ForEach (
			self: AvailObject,
			action: (AvailObject, AvailObject) -> Unit): Unit =
		unsupported

	override fun o_ForEachInMapBin (
			self: AvailObject,
			action: (AvailObject, AvailObject) -> Unit): Unit =
		unsupported

	override fun o_SetSuccessAndFailureContinuations (
		self: AvailObject,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit): Unit = unsupported

	override fun o_ClearLexingState (self: AvailObject): Unit = unsupported

	override fun o_RegisterDump (self: AvailObject): AvailObject = unsupported

	override fun o_MembershipChanged(self: AvailObject): Unit = unsupported

	override fun o_DefinitionBundle(self: AvailObject): A_Bundle = unsupported

	override fun o_BundleAddMacro(
		self: AvailObject,
		macro: A_Macro,
		ignoreSeals: Boolean
	): Unit = unsupported

	override fun o_ModuleAddMacro(self: AvailObject, macro: A_Macro): Unit =
		unsupported

	override fun o_RemoveMacro(self: AvailObject, macro: A_Macro): Unit =
		unsupported

	override fun o_AddBundle(self: AvailObject, bundle: A_Bundle): Unit =
		unsupported

	override fun o_ReturnTypeIfPrimitiveFails(self: AvailObject): A_Type =
		unsupported

	override fun o_ExtractDumpedObjectAt(
		self: AvailObject,
		index: Int
	): AvailObject = unsupported

	override fun o_ExtractDumpedLongAt(self: AvailObject, index: Int): Long =
		unsupported

	override fun o_IsOpen(self: AvailObject): Boolean = unsupported

	override fun o_CloseModule (self: AvailObject): Unit = unsupported

	override fun o_SetAtomBundle(self: AvailObject, bundle: A_Bundle): Unit =
		unsupported

	override fun o_OriginatingPhraseAtIndex(
		self: AvailObject,
		index: Int
	): A_Phrase = unsupported

	override fun o_RecordBlockPhrase(
		self: AvailObject,
		blockPhrase: A_Phrase
	): A_Number = unsupported

	override fun o_GetAndSetTupleOfBlockPhrases(
		self: AvailObject,
		newValue: AvailObject
	): AvailObject = unsupported

	override fun o_OriginatingPhraseOrIndex(self: AvailObject): AvailObject =
		unsupported

	override fun o_DeclarationNames(self: AvailObject): A_Tuple = unsupported

	override fun o_PackedDeclarationNames(self: AvailObject): A_String =
		unsupported

	override fun o_SetOriginatingPhraseOrIndex(
		self: AvailObject,
		phraseOrIndex: AvailObject
	): Unit = unsupported

	override fun o_LexerApplicability(
		self: AvailObject,
		codePoint: Int
	): Boolean? = unsupported

	override fun o_SetLexerApplicability(
		self: AvailObject,
		codePoint: Int,
		applicability: Boolean
	): Unit = unsupported

	override fun o_SerializedObjects(
		self: AvailObject,
		serializedObjects: A_Tuple
	): Unit = unsupported

	override fun o_SerializedObjectsMap(
		self: AvailObject,
		serializedObjectsMap: A_Map
	): Unit = unsupported

	override fun o_ApplyModuleHeader(
		self: AvailObject,
		loader: AvailLoader,
		moduleHeader: ModuleHeader
	): String? = unsupported

	override fun o_HasAncestor(
		self: AvailObject,
		potentialAncestor: A_Module
	): Boolean = unsupported
}
