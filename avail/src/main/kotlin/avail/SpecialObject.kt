/*
 * SpecialObject.kt
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
package avail

import avail.AvailRuntime.HookType
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerFilterFunctionType
import avail.descriptor.pojos.PojoDescriptor.Companion.nullPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationMeta
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FiberTypeDescriptor.Companion.fiberMeta
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.characterCodePoints
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u16
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u4
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import avail.descriptor.types.MapTypeDescriptor.Companion.mapMeta
import avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoArrayType
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfType
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfTypeAtom
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setMeta
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableMeta
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.exceptions.AvailErrorCode.Companion.allNumericCodes

/**
 * The [special objects][AvailObject] of the [runtime][AvailRuntime].
 *
 * **DO NOT** alter existing entries, as they are used to generate the
 * `SpecialObjectNames_en.properties` file.  If a new entry is appended, you
 * should run the `generateSpecialObjectNames` Gradle task, update the entries
 * in that file, then run the `generateBootstrap` task.
 */
enum class SpecialObject(weakValue: A_BasicObject)
{
	// Special entry, not used.
	Nil(nil),
	Any(Types.ANY()),
	Boolean(booleanType),
	Character(Types.CHARACTER()),
	Function(mostGeneralFunctionType),
	FunctionMeta(functionMeta()),
	RawFunction(mostGeneralCompiledCodeType()),
	Variable(mostGeneralVariableType),
	VariableMeta(mostGeneralVariableMeta),
	Continuation(mostGeneralContinuationType),
	ContinuationMeta(continuationMeta),
	Atom(Types.ATOM()),
	Double(Types.DOUBLE()),
	ExtendedInteger(extendedIntegers),
	AnyTypeTupleType(instanceMeta(zeroOrMoreOf(anyMeta))),
	Float(Types.FLOAT()),
	Number(Types.NUMBER()),
	Integer(integers),
	ExtendedIntegerMeta(extendedIntegersMeta),
	MapMeta(mapMeta()),
	Module(Types.MODULE()),
	ErrorCodesTuple(tupleFromIntegerList(allNumericCodes())),
	ObjectType(mostGeneralObjectType),
	ObjectMeta(mostGeneralObjectMeta),
	Exception(Exceptions.exceptionType),
	Fiber(mostGeneralFiberType()),
	Set(mostGeneralSetType()),
	Setmeta(setMeta()),
	String(stringType),
	Bottom(bottom),
	BottomMeta(bottomMeta),
	NonType(Types.NONTYPE()),
	Tuple(mostGeneralTupleType),
	TupleMeta(tupleMeta),
	TopMeta(topMeta),
	Top(TOP()),
	WholeNumber(wholeNumbers),
	NaturalNumber(naturalNumbers),
	CodePoint(characterCodePoints),
	Map(mostGeneralMapType()),
	MessageBundle(Types.MESSAGE_BUNDLE()),
	Method(Types.METHOD()),
	Definition(Types.DEFINITION()),
	AbstractDefinition(Types.ABSTRACT_DEFINITION()),
	ForwardDefinition(Types.FORWARD_DEFINITION()),
	MethodDefinition(Types.METHOD_DEFINITION()),
	MacroDefinition(Types.MACRO_DEFINITION()),
	FunctionTuple(zeroOrMoreOf(mostGeneralFunctionType)),
	StackDumpAtom(Exceptions.stackDumpAtom),
	Phrase(PhraseKind.PARSE_PHRASE.mostGeneralType),
	SequencePhrase(PhraseKind.SEQUENCE_PHRASE.mostGeneralType),
	ExpressionPhrase(PhraseKind.EXPRESSION_PHRASE.mostGeneralType),
	AssignmentPhrase(PhraseKind.ASSIGNMENT_PHRASE.mostGeneralType),
	BlockPhrase(PhraseKind.BLOCK_PHRASE.mostGeneralType),
	LiteralPhrase(PhraseKind.LITERAL_PHRASE.mostGeneralType),
	ReferencePhrase(PhraseKind.REFERENCE_PHRASE.mostGeneralType),
	SendPhrase(PhraseKind.SEND_PHRASE.mostGeneralType),
	SequenceAsExpressionPhrase(
		PhraseKind.SEQUENCE_AS_EXPRESSION_PHRASE.mostGeneralType),
	LiteralTokenMeta(instanceMeta(mostGeneralLiteralTokenType())),
	ListPhrase(PhraseKind.LIST_PHRASE.mostGeneralType),
	VariableUsePhrase(PhraseKind.VARIABLE_USE_PHRASE.mostGeneralType),
	DeclarationPhrase(PhraseKind.DECLARATION_PHRASE.mostGeneralType),
	ArgumentPhrase(PhraseKind.ARGUMENT_PHRASE.mostGeneralType),
	LabelPhrase(PhraseKind.LABEL_PHRASE.mostGeneralType),
	LocalVariablePhrase(PhraseKind.LOCAL_VARIABLE_PHRASE.mostGeneralType),
	LocalConstantPhrase(PhraseKind.LOCAL_CONSTANT_PHRASE.mostGeneralType),
	ModuleVariablePhrase(PhraseKind.MODULE_VARIABLE_PHRASE.mostGeneralType),
	ModuleConstantPhrase(PhraseKind.MODULE_CONSTANT_PHRASE.mostGeneralType),
	PrimitiveFailureReasonPhrase(
		PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType),
	AnyMeta(anyMeta),
	True(trueObject),
	False(falseObject),
	StringTuple(zeroOrMoreOf(stringType)),
	TopMetaTuple(zeroOrMoreOf(topMeta)),
	StringSetTuple(
		zeroOrMoreOf(setTypeForSizesContentType(wholeNumbers, stringType))),
	StringSet(setTypeForSizesContentType(wholeNumbers, stringType)),
	PrimitiveFailureFunction(functionType(tuple(naturalNumbers), bottom)),
	EmptySet(emptySet),
	NegativeInfinity(negativeInfinity),
	PositiveInfinity(positiveInfinity),
	PojoType(mostGeneralPojoType()),
	PojoBottom(pojoBottom()),
	NullPojo(nullPojo()),
	PojoSelf(pojoSelfType()),
	PojoMeta(instanceMeta(mostGeneralPojoType())),
	PojoArrayMeta(instanceMeta(mostGeneralPojoArrayType())),
	FunctionReturningAny(functionTypeReturning(Types.ANY())),
	PojoArray(mostGeneralPojoArrayType()),
	PojoSelfTypeAtom(pojoSelfTypeAtom()),
	PojoThrowable(pojoTypeForClass(Throwable::class.java)),
	NullaryFunction(functionType(emptyTuple(), TOP())),
	NullaryFunctionreturningBoolean(functionType(emptyTuple(), booleanType)),
	ContinuationVariable(variableTypeFor(mostGeneralContinuationType)),
	MapFromAtomToAny(
		mapTypeForSizesKeyTypeValueType(
			wholeNumbers, Types.ATOM(), Types.ANY())),
	MapFromAtomToAnyMeta(
		mapTypeForSizesKeyTypeValueType(wholeNumbers, Types.ATOM(), anyMeta)),
	KeyValueTuple(
		tupleTypeForSizesTypesDefaultType(
			wholeNumbers,
			emptyTuple(),
			tupleTypeForSizesTypesDefaultType(
				singleInt(2),
				emptyTuple(),
				Types.ANY()))),
	EmptyMap(emptyMap),
	NonemptyMap(
		mapTypeForSizesKeyTypeValueType(
			naturalNumbers, Types.ANY(), Types.ANY())),
	WholeNumberMeta(instanceMeta(wholeNumbers)),
	NonemptySet(setTypeForSizesContentType(naturalNumbers, Types.ANY())),
	TupleOfTuple(
		tupleTypeForSizesTypesDefaultType(
			wholeNumbers, emptyTuple, mostGeneralTupleType)),
	U4(u4),
	U4Tuple(zeroOrMoreOf(u4)),
	U16(u16),
	EmptyTuple(emptyTuple),
	UnaryFunction(functionType(tuple(bottom), TOP())),
	ZeroType(instanceType(zero)),
	FunctionReturningType(functionTypeReturning(topMeta)),
	TupleOfFunctionReturningType(
		tupleTypeForSizesTypesDefaultType(
			wholeNumbers, emptyTuple(), functionTypeReturning(topMeta))),
	FunctionReturningPhrase(
		functionTypeReturning(PhraseKind.PARSE_PHRASE.mostGeneralType)),
	TwoType(instanceType(two)),
	EulersConstant(fromDouble(Math.E)),
	EulersConstantType(instanceType(fromDouble(Math.E))),
	PhraseMeta(instanceMeta(PhraseKind.PARSE_PHRASE.mostGeneralType)),
	AtomSet(setTypeForSizesContentType(wholeNumbers, Types.ATOM())),
	Token(Types.TOKEN()),
	LiteralToken(mostGeneralLiteralTokenType()),
	AnyMetaTuple(zeroOrMoreOf(anyMeta)),
	ExtendedWholeNumber(inclusive(zero, positiveInfinity)),
	FieldTypeTuple(
		zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2),
				tuple(Types.ATOM()),
				anyMeta))),
	FieldTuple(
		zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2),
				tuple(Types.ATOM()),
				Types.ANY()))),
	PhraseTuple(zeroOrMoreOf(PhraseKind.PARSE_PHRASE.mostGeneralType)),
	ArgumentPhraseTuple(
		zeroOrMoreOf(PhraseKind.ARGUMENT_PHRASE.mostGeneralType)),
	DeclarationPhraseTuple(
		zeroOrMoreOf(PhraseKind.DECLARATION_PHRASE.mostGeneralType)),
	WriteOnlyVariable(variableReadWriteType(TOP(), bottom)),
	ExpressionPhraseTuple(
		zeroOrMoreOf(PhraseKind.EXPRESSION_PHRASE.create(Types.ANY()))),
	AnyExpressionPhrase(PhraseKind.EXPRESSION_PHRASE.create(Types.ANY())),
	PojoFailureFunction(
		functionType(tuple(pojoTypeForClass(Throwable::class.java)), bottom)),
	AtomSetTuple(
		zeroOrMoreOf(setTypeForSizesContentType(wholeNumbers, Types.ATOM()))),
	U8(u8),
	AnyMetaTupleTuple(zeroOrMoreOf(zeroOrMoreOf(anyMeta))),
	ExtendedIntegerReadOnlyVariable(
		variableReadWriteType(extendedIntegers, bottom)),
	FiberMeta(fiberMeta()),
	NonemptyString(nonemptyStringType),
	SetOfException(
		setTypeForSizesContentType(wholeNumbers, Exceptions.exceptionType)),
	StringNonemptySet(setTypeForSizesContentType(naturalNumbers, stringType)),
	AtomNonemptySet(setTypeForSizesContentType(naturalNumbers, Types.ATOM())),
	NonemptyTuple(oneOrMoreOf(Types.ANY())),
	IntegerTuple(zeroOrMoreOf(integers)),
	TwoOrMoreTuple(
		tupleTypeForSizesTypesDefaultType(
			integerRangeType(fromInt(2), true, positiveInfinity, false),
			emptyTuple(),
			Types.ANY())),

	// Some of these entries may need to be shuffled into earlier
	// slots to maintain reasonable topical consistency.)
	FirstOfSequencePhrase(PhraseKind.FIRST_OF_SEQUENCE_PHRASE.mostGeneralType),
	PermutedListPhrase(PhraseKind.PERMUTED_LIST_PHRASE.mostGeneralType),
	SuperCastPhrase(PhraseKind.SUPER_CAST_PHRASE.mostGeneralType),
	ParseMapKey(SpecialAtom.CLIENT_DATA_GLOBAL_KEY),
	ScopeMapKey(SpecialAtom.COMPILER_SCOPE_MAP_KEY),
	AllTokensKey(SpecialAtom.ALL_TOKENS_KEY),
	I32(i32),
	I64(i64),
	StatementPhrase(PhraseKind.STATEMENT_PHRASE.mostGeneralType),
	CompilerScopeStackKey(SpecialAtom.COMPILER_SCOPE_STACK_KEY),
	ExpressionAsStatementPhrase(
		PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType),
	PermutationTuple(oneOrMoreOf(naturalNumbers)),
	DefinitionTuple(zeroOrMoreOf(Types.DEFINITION())),
	StringToAtomMap(
		mapTypeForSizesKeyTypeValueType(
			wholeNumbers, stringType, Types.ATOM())),
	MacroBundleKey(SpecialAtom.MACRO_BUNDLE_KEY),
	ExplicitSubclassKey(SpecialAtom.EXPLICIT_SUBCLASSING_KEY),
	MapVariable(variableReadWriteType(mostGeneralMapType(), bottom)),
	LexerFilterFunction(lexerFilterFunctionType()),
	LexerBodyFunction(lexerBodyFunctionType()),
	StaticTokensKey(SpecialAtom.STATIC_TOKENS_KEY),
	StaticTokenIndicesKey(SpecialAtom.STATIC_TOKEN_INDICES_KEY),
	EndOfFileTokenClassifier(TokenType.END_OF_FILE.atom),
	KeywordTokenClassifier(TokenType.KEYWORD.atom),
	LiteralTokenClassifier(TokenType.LITERAL.atom),
	OperatorTokenClassifier(TokenType.OPERATOR.atom),
	CommentTokenClassifier(TokenType.COMMENT.atom),
	WhitespaceTokenClassifier(TokenType.WHITESPACE.atom),
	ParseRejectionLevel(inclusive(1, 4)),
	CharacterTypeNumber(inclusive(0, 31)),
	ContinuationReturningTop(
		continuationTypeForFunctionType(functionTypeReturning(TOP()))),
	DigitNonemptyString(CharacterDescriptor.nonemptyStringOfDigitsType),
	OptionalSendPhraseAndReportString(
		tupleTypeForTypes(
			zeroOrOneOf(PhraseKind.SEND_PHRASE.mostGeneralType), stringType)),
	StylerFunction(stylerFunctionType),
	TokenClassifiers(
		enumerationWith(
			set(
				TokenType.WHITESPACE.atom,
				TokenType.COMMENT.atom,
				TokenType.OPERATOR.atom,
				TokenType.KEYWORD.atom,
				TokenType.END_OF_FILE.atom))),
	TokenTuple(zeroOrMoreOf(Types.TOKEN())),
	MarkerPhrase(PhraseKind.MARKER_PHRASE.mostGeneralType),
	ModuleImportsTuple(
		oneOrMoreOf(
			tupleTypeForTypes(
				// Imported module name (Uses).
				stringType,
				// Optional import names list.
				zeroOrOneOf(
					// Import names list.
					tupleTypeForTypes(
						zeroOrMoreOf(
							tupleTypeForTypes(
								// Negated import.
								booleanType,
								// Imported name.
								nonemptyStringType,
								// Optional rename.
								zeroOrOneOf(nonemptyStringType))),
						// Wildcard.
						booleanType))))),
	OptionalStylerFunction(zeroOrOneOf(stylerFunctionType)),
	OptionalPhrase(zeroOrOneOf(PhraseKind.PARSE_PHRASE.mostGeneralType)),
	StringLiteralPhrase(
		PhraseKind.LITERAL_PHRASE.create(literalTokenType(stringType))),
	NonemptyStringNonemetyTuple(oneOrMoreOf(nonemptyStringType)),
	NonemptyStringSet(
		setTypeForSizesContentType(wholeNumbers, nonemptyStringType)),
	// The hooks' default values.
	HookPrimitiveFailure(HookType.PRIMITIVE_FAILURE_HANDLER),
	HookStringification(HookType.STRINGIFICATION),
	HookReadUnassignedVariable(HookType.READ_UNASSIGNED_VARIABLE),
	HookResultDisagreedWithExpectedType(
		HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE),
	HookInvalidMessageSend(HookType.INVALID_MESSAGE_SEND),
	HookImplicitObserve(HookType.IMPLICIT_OBSERVE),
	HookRaiseJavaExceptionInAvail(HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL),
	HookBaseFrame(HookType.BASE_FRAME),
	HookDebuggableBaseFrame(HookType.DEBUGGABLE_BASE_FRAME),
	HookDefaultStyler(HookType.DEFAULT_STYLER)

	;

	constructor(hookType: HookType) : this(hookType.functionSupplier())

	constructor(specialAtom: SpecialAtom) : this(specialAtom.atom)

	val value = weakValue as AvailObject

	init
	{
		assert(!value.isAtom || value.isAtomSpecial)
	}

	companion object
	{
		/**
		 * Answer the special object ([AvailObject]) with the specified ordinal.
		 *
		 * @param ordinal
		 *   The [special object][AvailObject] with the specified ordinal.
		 * @return
		 *   An [AvailObject].
		 */
		fun specialObject(ordinal: Int): AvailObject = entries[ordinal].value

		/**
		 * A map from special objects to their index.
		 */
		val specialObjectsToIndex = entries.associate { it.value to it.ordinal }

		/**
		 * Look up the [AvailObject] and answer its ordinal if it's a special
		 * object, or -1 if not found.
		 */
		fun specialObjectIndex(value: A_BasicObject): Int =
			specialObjectsToIndex[value] ?: -1
	}
}
