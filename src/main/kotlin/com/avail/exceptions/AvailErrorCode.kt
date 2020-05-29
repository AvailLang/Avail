/*
 * AvailErrorCode.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.exceptions

import com.avail.AvailRuntime
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.methods.AbstractDefinitionDescriptor
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.ForwardDefinitionDescriptor
import com.avail.descriptor.methods.MacroDefinitionDescriptor
import com.avail.descriptor.methods.MethodDefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.InfinityDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.phrases.ReferencePhraseDescriptor
import com.avail.descriptor.phrases.SequencePhraseDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.FiberTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PojoTypeDescriptor
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.primitive.phrases.P_AcceptParsing
import com.avail.interpreter.primitive.phrases.P_CurrentMacroName
import com.avail.interpreter.primitive.phrases.P_RejectParsing
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.Deserializer
import com.avail.serialization.Serializer
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.*

/**
 * `AvailErrorCode` is an enumeration of all possible failures of operations on
 * [Avail&#32;objects][AvailObject].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property code
 *   The numeric error code.
 *
 * @constructor
 * Construct a new `AvailErrorCode` with the specified numeric error code.
 *
 * @param code
 *   The numeric error code.
 */
enum class AvailErrorCode constructor(val code: Int)
{
	/** Operation is required to fail. */
	E_REQUIRED_FAILURE(0),

	/**
	 * Cannot [A_Number.plusCanDestroy] add} [infinities][InfinityDescriptor] of
	 * unlike sign.
	 */
	E_CANNOT_ADD_UNLIKE_INFINITIES(1),

	/**
	 * Cannot [subtract][A_Number.minusCanDestroy]
	 * [infinities][InfinityDescriptor] of unlike sign.
	 */
	E_CANNOT_SUBTRACT_LIKE_INFINITIES(2),

	/**
	 * Cannot [multiply][A_Number.timesCanDestroy]
	 * [zero][IntegerDescriptor.zero] and [infinity][InfinityDescriptor].
	 */
	E_CANNOT_MULTIPLY_ZERO_AND_INFINITY(3),

	/**
	 * Cannot [divide][A_Number.divideCanDestroy] by
	 * [zero][IntegerDescriptor.zero].
	 */
	E_CANNOT_DIVIDE_BY_ZERO(4),

	/**
	 * Cannot [divide][A_Number.divideCanDestroy] two
	 * [infinities][InfinityDescriptor].
	 */
	E_CANNOT_DIVIDE_INFINITIES(5),

	/** Cannot read from an unassigned [variable][VariableDescriptor]. */
	E_CANNOT_READ_UNASSIGNED_VARIABLE(6),

	/**
	 * Cannot write an incorrectly typed value into a
	 * [variable][VariableDescriptor] or [pojo][PojoTypeDescriptor].
	 */
	E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE(7),

	/**
	 * Cannot swap the contents of two differently typed
	 * [variables][VariableDescriptor].
	 */
	E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES(8),

	/** No such [fiber][FiberDescriptor] variable. */
	E_NO_SUCH_FIBER_VARIABLE(9),

	/** Subscript out of bounds. */
	E_SUBSCRIPT_OUT_OF_BOUNDS(10),

	/** Incorrect number of arguments. */
	E_INCORRECT_NUMBER_OF_ARGUMENTS(11),

	/** Incorrect argument [type][TypeDescriptor]. */
	E_INCORRECT_ARGUMENT_TYPE(12),

	/**
	 * A [method&#32;definition][MethodDescriptor] did not declare the same
	 * return type as its [forward declaration][ForwardDefinitionDescriptor].
	 */
	E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED(13),

	/**
	 * [Continuation][ContinuationDescriptor] expected a stronger
	 * [type][TypeDescriptor].
	 */
	E_CONTINUATION_EXPECTED_STRONGER_TYPE(14),

	/** The requested operation is not currently supported on this platform. */
	E_OPERATION_NOT_SUPPORTED(15),

	//	E_??? (16),

	/**
	 * The specified type is not a finite
	 * [enumeration][EnumerationTypeDescriptor] of values.
	 */
	E_NOT_AN_ENUMERATION(17),

	/** The shift and truncate operation operates on non-negative integers. */
	E_SHIFT_AND_TRUNCATE_REQUIRES_NON_NEGATIVE(18),

	/**
	 * No [method][MethodDescriptor] exists for the specified
	 * [name][AtomDescriptor].
	 */
	E_NO_METHOD(19),

	/**
	 * The wrong number or [types][TypeDescriptor] of outers were specified for
	 * creation of a [function][FunctionDescriptor] from a
	 * [raw&#32;function][CompiledCodeDescriptor].
	 */
	E_WRONG_OUTERS(20),

	/** A key was not present in a [map][MapDescriptor]. */
	E_KEY_NOT_FOUND(21),

	/**
	 * A size [range][IntegerRangeTypeDescriptor]'s lower bound must be
	 * non-negative (>=0).
	 */
	E_NEGATIVE_SIZE(22),

	/** An I/O error has occurred. */
	E_IO_ERROR(23),

	/**
	 * The operation was forbidden by the platform or the Java
	 * [security&#32;manager][SecurityManager] because of insufficient user
	 * privilege.
	 */
	E_PERMISSION_DENIED(24),

	/** A resource handle was invalid for some particular use. */
	E_INVALID_HANDLE(25),

	/** A primitive number is invalid. */
	E_INVALID_PRIMITIVE_NUMBER(26),

	// E_?? (27)
	// E_?? (28)
	// E_?? (29)

	/** A computation would produce a value too large to represent. */
	E_TOO_LARGE_TO_REPRESENT(30),

	/**
	 * The specified type restriction function should expect types as arguments
	 * in order to check the validity (and specialize the result) of a call
	 * site.
	 */
	E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES(31),

	/**
	 * A method's argument type was inconsistent with a
	 * [special&#32;object][AvailRuntime.specialObject] specific requirements.
	 */
	E_INCORRECT_TYPE_FOR_GROUP(32),

	/** A [special object][AvailRuntime.specialObject] number is invalid. */
	E_NO_SPECIAL_OBJECT(33),

	/**
	 * A [macro][MacroDefinitionDescriptor] [body][FunctionDescriptor] must
	 * restrict each parameter to be at least as specific as a
	 * [phrase][PhraseDescriptor].
	 */
	E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE(34),

	/**
	 * There are multiple [true names][AtomDescriptor] associated with the
	 * string.
	 */
	E_AMBIGUOUS_NAME(35),

	/** Cannot assign to this [kind of declaration][DeclarationKind]. */
	E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT(36),

	/**
	 * Cannot take a [reference][ReferencePhraseDescriptor] to this
	 * [kind&#32;of&#32;declaration][DeclarationKind].
	 */
	E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE(37),

	/**
	 * An exclamation mark (!) may only occur after a guillemet group containing
	 * an alternation.
	 */
	E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP(38),

	/**
	 * An attempt was made to add a signature with the same argument types as an
	 * existing signature.
	 */
	E_REDEFINED_WITH_SAME_ARGUMENT_TYPES(39),

	/**
	 * A signature was added that had stronger argument types, but the result
	 * type was not correspondingly stronger.
	 */
	E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS(40),

	/**
	 * A [special&#32;atom][AvailRuntime.specialAtoms] was supplied where
	 * forbidden.
	 */
	E_SPECIAL_ATOM(41),

	/**
	 * A method's argument type was inconsistent with a
	 * [complex&#32;guillemet&#32;group's][MessageSplitter] specific
	 * requirements.  In particular, the corresponding argument position must be
	 * a tuple of tuples whose sizes range from the number of argument
	 * subexpressions left of the double-dagger, up to the number of argument
	 * subexpressions on both sides of the double-dagger.
	 */
	E_INCORRECT_TYPE_FOR_COMPLEX_GROUP(42),

	/**
	 * The method name is invalid because it uses the double-dagger (‡)
	 * incorrectly.
	 */
	E_INCORRECT_USE_OF_DOUBLE_DAGGER(43),

	/** The method name is invalid because it has unmatched guillemets («»). */
	E_UNBALANCED_GUILLEMETS(44),

	/**
	 * The method name is not well-formed because it does not have the
	 * canonically simplest representation.
	 */
	E_METHOD_NAME_IS_NOT_CANONICAL(45),

	/**
	 * The method name is invalid because an operator character did not follow
	 * a backquote (`).
	 */
	E_EXPECTED_OPERATOR_AFTER_BACKQUOTE(46),

	/**
	 * An argument type for a boolean group («...»?) must be a subtype of
	 * boolean.
	 */
	E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP(47),

	/**
	 * An argument type for a counting group («...»#) must be a subtype of
	 * boolean.
	 */
	E_INCORRECT_TYPE_FOR_COUNTING_GROUP(48),

	/**
	 * An octothorp (#) may only occur after a guillemet group which has no
	 * arguments or an ellipsis (…).
	 */
	E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS(49),

	/**
	 * A question mark (?) may only occur after a guillemet group which has no
	 * arguments or subgroups.
	 */
	E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP(50),

	/**
	 * An expression followed by a tilde (~) must contain only lower case
	 * characters.
	 */
	E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION(51),

	//	/**
	//	 * A tilde (~) must not follow an argument. It may only follow a keyword or
	//	 * a guillemet group.
	//	 */
	//	E_TILDE_MUST_NOT_FOLLOW_ARGUMENT (52),

	/**
	 * A double question mark (⁇) may only occur after a keyword, operator, or
	 * guillemet group which has no arguments or subgroups.
	 */
	E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP(53),

	/**
	 * An alternation must not contain arguments. It must comprise only simple
	 * expressions and simple groups.
	 */
	E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS(54),

	/**
	 * A vertical bar (|) may only occur after a keyword, operator, or
	 * guillemet group which has no arguments or subgroups.
	 */
	E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS(55),

	/**
	 * A [Double] [not-a-number][Double.NaN] or [Float]
	 * [not-a-number][Float.NaN] can not be converted to an extended
	 * integer (neither truncation, floor, nor ceiling).
	 */
	E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER(56),

	/**
	 * A [numbered&#32;choice&#32;expression][MessageSplitter] should have its
	 * corresponding argument typed as a subtype of [1..N] where N is the number
	 * of listed choices.
	 */
	E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE(57),

	/** A dollar sign ($) may only occur after an ellipsis (…). */
	E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS(58),

	/**
	 * A macro prefix function is invoked when a potential macro site reaches
	 * certain checkpoints.  Only the macro body may return a phrase.  One
	 * of the prefix functions did not have return type ⊤.
	 */
	E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP(59),

	// E_?? (60)

	/**
	 * A continuation was being constructed, but the wrong number of stack slots
	 * was provided for the given function.
	 */
	E_INCORRECT_CONTINUATION_STACK_SIZE(61),

	/**
	 * The module is currently in a setPhase where it's compiling (or loading)
	 * the next statement to execute, and as part of the compilation or loading
	 * it attempted to execute a primitive that would add a definition.
	 */
	E_CANNOT_DEFINE_DURING_COMPILATION(62),

	/**
	 * An attempt was made to add a prefix [function][A_Function] to a
	 * [message&#32;bundle][A_Bundle], but its index was not between 1 and the
	 * number of section markers (§) in the bundle's name.
	 */
	E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS(63),

	/**
	 * The [current&#32;fiber][FiberDescriptor.currentFiber] attempted to
	 * [accept&#32;the&#32;current&#32;parse][P_AcceptParsing], but it isn't
	 * actually running a semantic restriction.
	 */
	E_UNTIMELY_PARSE_ACCEPTANCE(64),

	/**
	 * The [current&#32;fiber][FiberDescriptor.currentFiber] attempted to
	 * determine the [current&#32;macro&#32;name][P_CurrentMacroName], the name
	 * (atom) of a send phrase which was undergoing macro substitution, but this
	 * fiber is not performing a macro substitution.
	 */
	E_NOT_EVALUATING_MACRO(65),

	/**
	 * The yield type specified for a [PhraseKind] was not a subtype of the
	 * [most&#32;general&#32;yield][PhraseKind.mostGeneralYieldType].
	 */
	E_BAD_YIELD_TYPE(66),

	/**
	 * A [macro][MacroDefinitionDescriptor]'s
	 * [prefix&#32;function][FunctionDescriptor] must restrict each parameter to
	 * be at least as specific as a [phrase][PhraseDescriptor].
	 */
	E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE(67),

	/**
	 * A [macro][MacroDefinitionDescriptor] [body][FunctionDescriptor] must
	 * produce a [phrase][PhraseDescriptor].
	 */
	E_MACRO_MUST_RETURN_A_PARSE_NODE(68),

	/**
	 * An attempt to read a field of an object or object type was unsuccessful
	 * because that field is not present.
	 */
	E_NO_SUCH_FIELD(69),

	/**
	 * Module loading is over. The interpreter is now operating in runtime mode.
	 * This usually means that an attempt was made to modify module metadata at
	 * runtime.
	 */
	E_LOADING_IS_OVER(70),

	/**
	 * The [current&#32;fiber][FiberDescriptor.currentFiber] attempted to
	 * [reject&#32;the&#32;current&#32;parse][P_RejectParsing], but it isn't
	 * actually running a semantic restriction.
	 */
	E_UNTIMELY_PARSE_REJECTION(71),

	/**
	 * The method is sealed at the specified
	 * [parameters&#32;type][TupleTypeDescriptor].
	 */
	E_METHOD_IS_SEALED(72),

	/**
	 * Cannot overwrite or clear an initialized
	 * [write-once&#32;variable][VariableSharedGlobalDescriptor].
	 */
	E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE(73),

	/**
	 * A [module][ModuleDescriptor] already has an [atom][AtomDescriptor]
	 * associated with a particular [name][StringDescriptor].
	 */
	E_ATOM_ALREADY_EXISTS(74),

	/**
	 * It seems that a prefix function did not set up things the way that the
	 * corresponding macro body expected.  Alternatively, a prefix function may
	 * notice that a previous prefix function behaved unexpectedly.
	 */
	E_INCONSISTENT_PREFIX_FUNCTION(75),

	/**
	 * The VM does not normally instantiate continuations for infallible
	 * primitive functions, so for conceptual consistency such continuations are
	 * disallowed.
	 */
	E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION(76),

	/**
	 * An attempt to transition the current [fiber][FiberDescriptor]'s trace
	 * mode was illegal.
	 */
	E_ILLEGAL_TRACE_MODE(77),

	/** An up arrow ("↑") must only occur after an underscore ("_"). */
	E_UP_ARROW_MUST_FOLLOW_ARGUMENT(78),

	/**
	 * The result of a [method][MethodDescriptor] send disagreed with the
	 * expected [type][TypeDescriptor].
	 */
	E_RESULT_DISAGREED_WITH_EXPECTED_TYPE(79)
	{
		override val isCausedByInstructionFailure: Boolean
			get() = true
	},

	/**
	 * The continuation whose primitive failure variable is set to this value is
	 * no longer eligible to run an exception handler (because it already has,
	 * is currently doing so, or has successfully run its guarded function to
	 * completion).
	 */
	E_HANDLER_SENTINEL(80),

	/**
	 * The continuation cannot be marked as ineligible to handle an exception
	 * (because its state is incorrect).
	 */
	E_CANNOT_MARK_HANDLER_FRAME(81),

	/**
	 * There are no exception handling continuations anywhere in the call chain.
	 */
	E_NO_HANDLER_FRAME(82),

	/**
	 * The continuation whose primitive failure variable is set to this value is
	 * no longer eligible to run an unwind handler (because it already has or is
	 * currently doing so).
	 */
	E_UNWIND_SENTINEL(83),

	/**
	 * No [method&#32;definition][MethodDefinitionDescriptor] satisfies the
	 * supplied criteria.
	 */
	@ReferencedInGeneratedCode
	E_NO_METHOD_DEFINITION(84),

	/**
	 * More than one [method definition][MethodDefinitionDescriptor] satisfies
	 * the supplied criteria.
	 */
	@ReferencedInGeneratedCode
	E_AMBIGUOUS_METHOD_DEFINITION(85),

	/**
	 * The resolved [definition][DefinitionDescriptor] is a
	 * [forward&#32;definition][ForwardDefinitionDescriptor].
	 */
	@ReferencedInGeneratedCode
	E_FORWARD_METHOD_DEFINITION(86)
	{
		override val isCausedByInstructionFailure: Boolean
			get() = true
	},

	/**
	 * The resolved [definition][DefinitionDescriptor] is an
	 * [abstract&#32;definition][AbstractDefinitionDescriptor].
	 */
	@ReferencedInGeneratedCode
	E_ABSTRACT_METHOD_DEFINITION(87)
	{
		override val isCausedByInstructionFailure: Boolean
			get() = true
	},

	/**
	 * A [variable][A_Variable] which has write reactors was written when write
	 * tracing was not active for the
	 * [current&#32;fiber][FiberDescriptor.currentFiber].
	 */
	E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED(88),

	/**
	 * The [fiber][FiberDescriptor] being examined has already been terminated.
	 */
	E_FIBER_IS_TERMINATED(89),

	/**
	 * The [fiber][FiberDescriptor] being interrogated has not (or did not)
	 * produce a result.
	 */
	E_FIBER_RESULT_UNAVAILABLE(90),

	/** A [fiber][FiberDescriptor] attempted to join itself. */
	E_FIBER_CANNOT_JOIN_ITSELF(91),

	/**
	 * A [fiber][FiberDescriptor] produced a result of an incorrect type, in
	 * violation of its [fiber type][FiberTypeDescriptor].
	 */
	E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT(92),

	/**
	 * An attempt was made to read through a valid handle that was not opened
	 * for read access.
	 */
	E_NOT_OPEN_FOR_READ(93),

	/**
	 * An attempt was made to perform some destructive operation with a valid
	 * handle that was not opened for write access.
	 */
	E_NOT_OPEN_FOR_WRITE(94),

	/**
	 * A value was passed that exceeded the allowed numeric range, either [Int],
	 * `Long`, or some other limit imposed by the operating system or virtual
	 * machine.
	 */
	E_EXCEEDS_VM_LIMIT(95),

	/** [Serialization][Serializer] failed. */
	E_SERIALIZATION_FAILED(96),

	/** [Deserialization][Deserializer] failed. */
	E_DESERIALIZATION_FAILED(97),

	/**
	 * [MessageSplitter] encountered inconsistent argument reordering indicators
	 * in a message name.
	 */
	E_INCONSISTENT_ARGUMENT_REORDERING(98),

	//	E_??? (99)

	/**
	 * A proposed [block expression][BlockPhraseDescriptor] contains one or more
	 * invalid statements.
	 */
	E_BLOCK_CONTAINS_INVALID_STATEMENTS(100),

	/** A [block expression][BlockPhraseDescriptor] is invalid. */
	E_BLOCK_IS_INVALID(101),

	/**
	 * The [block&#32;expression][BlockPhraseDescriptor] references outers, but
	 * must not.
	 */
	E_BLOCK_MUST_NOT_CONTAIN_OUTERS(102),

	/** The [block expression][BlockPhraseDescriptor] failed compilation. */
	E_BLOCK_COMPILATION_FAILED(103),

	//	E_??? (104),

	/**
	 * A proposed [sequence][SequencePhraseDescriptor] contains one or more
	 * invalid statements.
	 */
	E_SEQUENCE_CONTAINS_INVALID_STATEMENTS(105),

	/**
	 * Attempted to create a supercast from a base expression that yields a
	 * value of type top or bottom.
	 */
	E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM(106),

	/**
	 * Attempted to create a supercast whose base expression is also a supercast
	 * phrase.
	 */
	E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST(107),

	/**
	 * Attempted to create a supercast from a base expression that yields a
	 * value of type top or bottom.
	 */
	E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE(108),

	/** An external [process][Process] could not be launched. */
	E_NO_EXTERNAL_PROCESS(150),

	//	E_??? (109),

	/** The specified [path][Path] does not name an existing file. */
	E_NO_FILE(200),

	/** The specified [path][Path] names an existing file. */
	E_FILE_EXISTS(201),

	/** The specified [path][Path] names a directory that is not empty. */
	E_DIRECTORY_NOT_EMPTY(202),

	/** An aggregation non-atomic operation succeeded only partially. */
	E_PARTIAL_SUCCESS(203),

	/**
	 * At least one option was illegal, or possibly some combination of options
	 * were illegal.
	 */
	E_ILLEGAL_OPTION(204),

	/** A [path][Path] expression was invalid. */
	E_INVALID_PATH(205),

	/**
	 * A Java [class][Class] specified by name was either not found by the
	 * runtime system or not available for reflection.
	 */
	E_JAVA_CLASS_NOT_AVAILABLE(500),

	/**
	 * A [pojo&#32;type][PojoTypeDescriptor] is abstract and therefore cannot be
	 * instantiated or have a [constructor][Constructor] bound to a
	 * [function][FunctionDescriptor].
	 */
	E_POJO_TYPE_IS_ABSTRACT(501),

	/**
	 * The indicated Java [method][Method] or [constructor][Constructor] is not
	 * visible or does not exist.
	 */
	E_JAVA_METHOD_NOT_AVAILABLE(502),

	//	E_??? (503),

	/**
	 * Marshaling an [Avail object][AvailObject] to/from a Java counterpart
	 * failed.
	 */
	E_JAVA_MARSHALING_FAILED(504),

	/** The indicated Java [field][Field] is not visible or does not exist. */
	E_JAVA_FIELD_NOT_AVAILABLE(505),

	/**
	 * A reference to a [Java field][Field] is not uniquely resolvable for the
	 * given [pojo&#32;type][PojoTypeDescriptor].
	 */
	E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS(506),

	/**
	 * An attempt was made to modify a [final][Modifier.isFinal]
	 * [Java&#32;field][Field].
	 */
	E_CANNOT_MODIFY_FINAL_JAVA_FIELD(507),

	/**
	 * A reference to a [Java method][Method] is not uniquely resolvable for the
	 * given [pojo&#32;type][PojoTypeDescriptor] and parameter
	 * [types][TypeDescriptor].
	 */
	E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS(508);

	/**
	 * Can the `AvailErrorCode` result from failure of an [L1Operation]?
	 *
	 * @return
	 *   `true` if the error code can result from a failed instruction, `false`
	 *   otherwise.
	 */
	open val isCausedByInstructionFailure: Boolean
		get() = false

	/**
	 * Answer the numeric error code as a Java **Int**.
	 *
	 * @return
	 *   The numeric error code.
	 */
	fun nativeCode(): Int = code

	/**
	 * Answer the numeric error code as an [Avail][AvailObject].
	 *
	 * @return
	 *   The [numeric error code][AvailObject].
	 */
	@ReferencedInGeneratedCode
	fun numericCode(): A_Number = fromInt(code)

	companion object
	{

		/**
		 * The mapping from [numeric codes][code] to [AvailErrorCode]s.
		 */
		private val byNumericCode = HashMap<Int, AvailErrorCode>()

		// The enumeration values have been initialized, so build the map.
		init
		{
			for (errorCode in values())
			{
				assert(!byNumericCode.containsKey(errorCode.nativeCode()))
				byNumericCode[errorCode.nativeCode()] = errorCode
			}
		}

		/**
		 * Look up the `AvailErrorCode` with the given [numeric code][code].
		 *
		 * @param numericCode
		 *   The [Int] to look up as a numeric code.
		 * @return
		 *   The error code, or `null` if not defined.
		 */
		@JvmStatic
		fun byNumericCode(numericCode: Int): AvailErrorCode? =
			byNumericCode[numericCode]

		/**
		 * Answer all valid [numeric error codes][code].
		 *
		 * @return
		 *   A [list][List] of all valid numeric error codes.
		 */
		@JvmStatic
		fun allNumericCodes(): List<Int>
		{
			val codes = ArrayList<Int>(values().size)
			for (code in values())
			{
				// All right, not quite *all* of the numeric error codes, just
				// the ones that are encountered in typical ways.
				if (code.code > 0)
				{
					codes.add(code.code)
				}
			}
			return codes
		}
	}
}
