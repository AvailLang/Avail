/**
 * AvailErrorCode.java
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

package com.avail.exceptions;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.compiler.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelOne.L1Instruction;
import com.avail.interpreter.primitive.P_352_RejectParsing;
import com.avail.serialization.Deserializer;
import com.avail.serialization.Serializer;

/**
 * {@code AvailErrorCode} is an enumeration of all possible failures of
 * operations on {@linkplain AvailObject Avail objects}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum AvailErrorCode
{
	/**
	 * Operation is required to fail.
	 */
	E_REQUIRED_FAILURE (0),

	/**
	 * Cannot {@linkplain A_Number#plusCanDestroy(A_Number, boolean)} add}
	 * {@linkplain InfinityDescriptor infinities} of unlike sign.
	 */
	E_CANNOT_ADD_UNLIKE_INFINITIES (1),

	/**
	 * Cannot {@linkplain A_Number#minusCanDestroy(A_Number, boolean)
	 * subtract} {@linkplain InfinityDescriptor infinities} of unlike sign.
	 */
	E_CANNOT_SUBTRACT_LIKE_INFINITIES (2),

	/**
	 * Cannot {@linkplain A_Number#timesCanDestroy(A_Number, boolean)
	 * multiply} {@linkplain IntegerDescriptor#zero() zero} and {@linkplain
	 * InfinityDescriptor infinity}.
	 */
	E_CANNOT_MULTIPLY_ZERO_AND_INFINITY (3),

	/**
	 * Cannot {@linkplain A_Number#divideCanDestroy(A_Number, boolean)
	 * divide} by {@linkplain IntegerDescriptor#zero() zero}.
	 */
	E_CANNOT_DIVIDE_BY_ZERO (4),

	/**
	 * Cannot {@linkplain A_Number#divideCanDestroy(A_Number, boolean)
	 * divide} two {@linkplain InfinityDescriptor infinities}.
	 */
	E_CANNOT_DIVIDE_INFINITIES (5),

	/**
	 * Cannot read from an unassigned {@linkplain VariableDescriptor
	 * variable}.
	 */
	E_CANNOT_READ_UNASSIGNED_VARIABLE (6),

	/**
	 * Cannot write an incorrectly typed value into a {@linkplain
	 * VariableDescriptor variable} or {@linkplain PojoTypeDescriptor pojo
	 * array}.
	 */
	E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE (7),

	/**
	 * Cannot swap the contents of two differently typed {@linkplain
	 * VariableDescriptor variables}.
	 */
	E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES (8),

	/**
	 * No such {@linkplain FiberDescriptor fiber} variable.
	 */
	E_NO_SUCH_FIBER_VARIABLE (9),

	/**
	 * Subscript out of bounds.
	 */
	E_SUBSCRIPT_OUT_OF_BOUNDS (10),

	/**
	 * Incorrect number of arguments.
	 */
	E_INCORRECT_NUMBER_OF_ARGUMENTS (11),

	/**
	 * Incorrect argument {@linkplain TypeDescriptor type}.
	 */
	E_INCORRECT_ARGUMENT_TYPE (12),

	/**
	 * A {@linkplain MethodDescriptor method definition} did not declare
	 * the same return type as its {@linkplain ForwardDefinitionDescriptor
	 * forward declaration}.
	 */
	E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED (13),

	/**
	 * {@linkplain ContinuationDescriptor Continuation} expected a stronger
	 * {@linkplain TypeDescriptor type}.
	 */
	E_CONTINUATION_EXPECTED_STRONGER_TYPE (14),

	/**
	 * The requested operation is not currently supported on this platform.
	 */
	E_OPERATION_NOT_SUPPORTED (15),

	/**
	 * The specified type is not a finite {@linkplain EnumerationTypeDescriptor
	 * enumeration} of values.
	 */
	E_NOT_AN_ENUMERATION (17),

	/**
	 * The shift and truncate operation operates on non-negative integers.
	 */
	E_SHIFT_AND_TRUNCATE_REQUIRES_NON_NEGATIVE(18),

	/**
	 * No {@linkplain MethodDescriptor method} exists for the specified
	 * {@linkplain AtomDescriptor name}.
	 */
	E_NO_METHOD (19),

	/**
	 * The wrong number or {@linkplain TypeDescriptor types} of outers were
	 * specified for creation of a {@linkplain FunctionDescriptor function} from
	 * a {@linkplain CompiledCodeDescriptor raw function}.
	 */
	E_WRONG_OUTERS (20),

	/**
	 * A key was not present in a {@linkplain MapDescriptor map}.
	 */
	E_KEY_NOT_FOUND (21),

	/**
	 * A size {@linkplain IntegerRangeTypeDescriptor range}'s lower bound must
	 * be nonnegative (>=0).
	 */
	E_NEGATIVE_SIZE (22),

	/**
	 * An I/O error has occurred.
	 */
	E_IO_ERROR (23),

	/**
	 * The operation was forbidden by the platform or the Java {@linkplain
	 * SecurityManager security manager} because of insufficient user privilege.
	 */
	E_PERMISSION_DENIED (24),

	/**
	 * A resource handle was invalid for some particular use.
	 */
	E_INVALID_HANDLE (25),

	/**
	 * A primitive number is invalid.
	 */
	E_INVALID_PRIMITIVE_NUMBER (26),

	/**
	 * A primitive {@linkplain FunctionTypeDescriptor function type} disagrees
	 * with the primitive's {@linkplain FunctionDescriptor restriction function}.
	 */
	E_FUNCTION_DISAGREES_WITH_PRIMITIVE_RESTRICTION (27),

	/**
	 * A local type literal is not actually a {@linkplain TypeDescriptor type}.
	 */
	E_LOCAL_TYPE_LITERAL_IS_NOT_A_TYPE (28),

	/**
	 * An outer type literal is not actually a {@linkplain TypeDescriptor type}.
	 */
	E_OUTER_TYPE_LITERAL_IS_NOT_A_TYPE (29),

	/**
	 * A computation would produce a value too large to represent.
	 */
	E_TOO_LARGE_TO_REPRESENT (30),

	/**
	 * The specified type restriction function should expect types as arguments
	 * in order to check the validity (and specialize the result) of a call
	 * site.
	 */
	E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES (31),

	/**
	 * A method's argument type was inconsistent with a {@link
	 * MessageSplitter guillemet group's} specific requirements.
	 */
	E_INCORRECT_TYPE_FOR_GROUP (32),

	/**
	 * A {@linkplain AvailRuntime#specialObject(int) special object} number is
	 * invalid.
	 */
	E_NO_SPECIAL_OBJECT (33),

	/**
	 * A {@linkplain MacroDefinitionDescriptor macro} {@linkplain
	 * FunctionDescriptor body} must restrict each parameter to be at least as
	 * specific as a {@linkplain ParseNodeDescriptor parse node}.
	 */
	E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE (34),

	/**
	 * There are multiple {@linkplain AtomDescriptor true names} associated with
	 * the string.
	 */
	E_AMBIGUOUS_NAME (35),

	/**
	 * Cannot assign to this {@linkplain DeclarationKind kind of declaration}.
	 */
	E_DECLARATION_KIND_DOES_NOT_SUPPORT_ASSIGNMENT (36),

	/**
	 * Cannot take a {@linkplain ReferenceNodeDescriptor reference} to this
	 * {@linkplain DeclarationKind kind of declaration}.
	 */
	E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE (37),

	/**
	 * An exclamation mark (!) may only occur after a guillemet group containing
	 * an alternation.
	 */
	E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP (38),

	/**
	 * An attempt was made to add a signature with the same argument types as an
	 * existing signature.
	 */
	E_REDEFINED_WITH_SAME_ARGUMENT_TYPES (39),

	/**
	 * A signature was added that had stronger argument types, but the result
	 * type was not correspondingly stronger.
	 */
	E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS (40),

	/**
	 * A {@linkplain AvailRuntime#specialAtoms() special atom} was supplied
	 * where forbidden.
	 */
	E_SPECIAL_ATOM (41),

	/**
	 * A method's argument type was inconsistent with a {@link
	 * MessageSplitter complex guillemet group's} specific requirements.  In
	 * particular, the corresponding argument position must be a tuple of tuples
	 * whose sizes range from the number of argument subexpressions left of the
	 * double-dagger, up to the number of argument subexpressions on both sides
	 * of the double-dagger.
	 */
	E_INCORRECT_TYPE_FOR_COMPLEX_GROUP (42),

	/**
	 * The method name is invalid because it uses the double-dagger (‡)
	 * incorrectly.
	 */
	E_INCORRECT_USE_OF_DOUBLE_DAGGER (43),

	/**
	 * The method name is invalid because it has unmatched guillemets («»).
	 */
	E_UNBALANCED_GUILLEMETS (44),

	/**
	 * The method name is not well-formed because it does not have the
	 * canonically simplest representation.
	 */
	E_METHOD_NAME_IS_NOT_CANONICAL (45),

	/**
	 * The method name is invalid because an operator character did not follow
	 * a backquote (`).
	 */
	E_EXPECTED_OPERATOR_AFTER_BACKQUOTE (46),

	/**
	 * An argument type for a boolean group («...»?) must be a subtype of
	 * boolean.
	 */
	E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP (47),

	/**
	 * An argument type for a counting group («...»#) must be a subtype of
	 * boolean.
	 */
	E_INCORRECT_TYPE_FOR_COUNTING_GROUP (48),

	/**
	 * An octothorp (#) may only occur after a guillemet group which has no
	 * arguments.
	 */
	E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP (49),

	/**
	 * A question mark (?) may only occur after a guillemet group which has no
	 * arguments or subgroups.
	 */
	E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP (50),

	/**
	 * An expression followed by a tilde (~) must contain only lower case
	 * characters.
	 */
	E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION (51),

	/**
	 * A tilde (~) must not follow an argument. It may only follow a keyword or
	 * a guillemet group.
	 */
	E_TILDE_MUST_NOT_FOLLOW_ARGUMENT (52),

	/**
	 * A double question mark (⁇) may only occur after a keyword, operator, or
	 * guillemet group which has no arguments or subgroups.
	 */
	E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP (53),

	/**
	 * An alternation must not contain arguments. It must comprise only simple
	 * expressions and simple groups.
	 */
	E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS (54),

	/**
	 * A vertical bar (|) may only occur after a keyword, operator, or
	 * guillemet group which has no arguments or subgroups.
	 */
	E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS (55),

	/**
	 * A {@link Double} {@linkplain Double#NaN not-a-number} or {@link Float}
	 * {@linkplain Float#NaN not-a-number} can not be converted to an extended
	 * integer (neither truncation, floor, nor ceiling).
	 */
	E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER (56),

	/**
	 * A {@linkplain MessageSplitter numbered choice expression} should have its
	 * corresponding argument typed as a subtype of [1..N] where N is the number
	 * of listed choices.
	 */
	E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE (57),

	/**
	 * A {@link Primitive} has the flag {@link Flag#CannotFail}, so it should
	 * not have any statements to run in the event of a failed primitive.
	 */
	E_INFALLIBLE_PRIMITIVE_MUST_NOT_HAVE_STATEMENTS (58),

	/**
	 * A macro prefix function is invoked when a potential macro site reaches
	 * certain checkpoints.  Only the macro body may return a parse node.  One
	 * of the prefix functions did not have return type ⊤.
	 */
	E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP (59),

	/**
	 * A {@link Primitive} either has the flag {@linkplain Flag#CannotFail} and
	 * has a failure variable, or it does not have that flag and has no failure
	 * variable.
	 */
	E_PRIMITIVE_FALLIBILITY_DISAGREES_WITH_FAILURE_VARIABLE (60),

	/**
	 * The type of value returned by the implicit return at the end of a
	 * {@linkplain BlockNodeDescriptor block} should be a subtype of the block's
	 * explicit return type declaration.
	 */
	E_FINAL_EXPRESSION_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE (61),

	/**
	 * The type of value potentially returned by a block's {@link Primitive}
	 * should be a subtype of the block's explicit return type declaration.
	 */
	E_PRIMITIVE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE (62),

	/**
	 * A label's return type should be a subtype of the containing block's
	 * declared return type.
	 */
	E_LABEL_TYPE_SHOULD_AGREE_WITH_DECLARED_RETURN_TYPE (63),

	/**
	 * If a primitive declaration or label is specified, then a block must
	 * have an explicitly declared return type.
	 */
	E_RETURN_TYPE_IS_MANDATORY_WITH_PRIMITIVES_OR_LABELS (64),

	/**
	 * A {@link DeclarationNodeDescriptor declaration} is of type {@linkplain
	 * TopTypeDescriptor top} or {@linkplain BottomTypeDescriptor bottom}, which
	 * is not allowed.
	 */
	E_DECLARATION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM (65),

	/**
	 * A {@LINK DeclarationNodeDescriptor declaration} has the same name as
	 * another declaration in the current scope or an enclosing scope.  This is
	 * not allowed.
	 */
	E_LOCAL_DECLARATION_SHADOWS_ANOTHER (66),

	/**
	 * A {@linkplain MacroDefinitionDescriptor macro}'s {@linkplain
	 * FunctionDescriptor prefix function} must restrict each parameter to be at
	 * least as specific as a {@linkplain ParseNodeDescriptor parse node}.
	 */
	E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE (67),

	/**
	 * A {@linkplain MacroDefinitionDescriptor macro} {@linkplain
	 * FunctionDescriptor body} must produce a {@linkplain ParseNodeDescriptor
	 * parse node}.
	 */
	E_MACRO_MUST_RETURN_A_PARSE_NODE (68),

	/**
	 * An attempt to read a field of an object or object type was unsuccessful
	 * because that field is not present.
	 */
	E_NO_SUCH_FIELD (69),

	/**
	 * Module loading is over. The interpreter is now operating in runtime mode.
	 * This usually means that an attempt was made to modify module metadata at
	 * runtime.
	 */
	E_LOADING_IS_OVER (70),

	/**
	 * The {@linkplain FiberDescriptor#current() current fiber} attempted to
	 * {@linkplain P_352_RejectParsing reject the current parse}, but it isn't
	 * actually running a semantic restriction.
	 */
	E_UNTIMELY_PARSE_REJECTION (71),

	/**
	 * The method is sealed at the specified {@linkplain TupleTypeDescriptor
	 * parameters type}.
	 */
	E_METHOD_IS_SEALED (72),

	/**
	 * Cannot overwrite or clear an initialized {@linkplain
	 * VariableSharedWriteOnceDescriptor write-once variable}.
	 */
	E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE (73),

	/**
	 * A {@linkplain ModuleDescriptor module} already has an {@linkplain
	 * AtomDescriptor atom} associated with a particular {@linkplain
	 * StringDescriptor name}.
	 */
	E_ATOM_ALREADY_EXISTS (74),

	/**
	 * An attempt was made to add both a method definition and a macro
	 * definition in the same method.  This is not allowed.
	 */
	E_CANNOT_MIX_METHOD_AND_MACRO_DEFINITIONS (75),

	/**
	 * The VM does not normally instantiate continuations for infallible
	 * primitive functions, so for conceptual consistency such continuations are
	 * disallowed.
	 */
	E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION (76),

	/**
	 * An attempt to transition the current {@linkplain FiberDescriptor fiber}'s
	 * trace mode was illegal.
	 */
	E_ILLEGAL_TRACE_MODE (77),

	/**
	 * An up arrow ("↑") must only occur after an underscore ("_").
	 */
	E_UP_ARROW_MUST_FOLLOW_ARGUMENT (78),

	/**
	 * The result of a {@linkplain MethodDescriptor method} send disagreed with
	 * the expected {@linkplain TypeDescriptor type}.
	 */
	E_RESULT_DISAGREED_WITH_EXPECTED_TYPE (79)
	{
		@Override
		public boolean isCausedByInstructionFailure ()
		{
			return true;
		}
	},

	/**
	 * The continuation whose primitive failure variable is set to this value is
	 * no longer eligible to run an exception handler (because it already has,
	 * is currently doing so, or has successfully run its guarded function to
	 * completion).
	 */
	E_HANDLER_SENTINEL (80),

	/**
	 * The continuation cannot be marked as ineligible to handle an exception
	 * (because its state is incorrect).
	 */
	E_CANNOT_MARK_HANDLER_FRAME (81),

	/**
	 * There are no exception handling continuations anywhere in the call chain.
	 */
	E_NO_HANDLER_FRAME (82),

	/**
	 * The continuation whose primitive failure variable is set to this value is
	 * no longer eligible to run an unwind handler (because it already has or is
	 * currently doing so).
	 */
	E_UNWIND_SENTINEL (83),

	/**
	 * No {@linkplain MethodDefinitionDescriptor method definition} satisfies
	 * the supplied criteria.
	 */
	E_NO_METHOD_DEFINITION (84),

	/**
	 * More than one {@linkplain MethodDefinitionDescriptor method definition}
	 * satisfies the supplied criteria.
	 */
	E_AMBIGUOUS_METHOD_DEFINITION (85),

	/**
	 * The resolved {@linkplain DefinitionDescriptor definition} is a
	 * {@linkplain ForwardDefinitionDescriptor forward definition}.
	 */
	E_FORWARD_METHOD_DEFINITION (86)
	{
		@Override
		public boolean isCausedByInstructionFailure ()
		{
			return true;
		}
	},

	/**
	 * The resolved {@linkplain DefinitionDescriptor definition} is a
	 * {@linkplain AbstractDefinitionDescriptor abstract definition}.
	 */
	E_ABSTRACT_METHOD_DEFINITION (87)
	{
		@Override
		public boolean isCausedByInstructionFailure ()
		{
			return true;
		}
	},

	/**
	 * A {@linkplain A_Variable variable} which has write reactors was written
	 * when write tracing was not active for the {@linkplain
	 * FiberDescriptor#current() current fiber}.
	 */
	E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED (88),

	/**
	 * The {@linkplain FiberDescriptor fiber} being examined has already been
	 * terminated.
	 */
	E_FIBER_IS_TERMINATED (89),

	/**
	 * The {@linkplain FiberDescriptor fiber} being interrogated has not (or
	 * did not) produce a result.
	 */
	E_FIBER_RESULT_UNAVAILABLE (90),

	/**
	 * A {@linkplain FiberDescriptor fiber} attempted to join itself.
	 */
	E_FIBER_CANNOT_JOIN_ITSELF (91),

	/**
	 * A {@linkplain FiberDescriptor fiber} produced a result of an incorrect
	 * type, in violation of its {@linkplain FiberTypeDescriptor fiber type}.
	 */
	E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT (92),

	/**
	 * An attempt was made to read through a valid handle that was not opened
	 * for read access.
	 */
	E_NOT_OPEN_FOR_READ (93),

	/**
	 * An attempt was made to perform some destructive operation with a valid
	 * handle that was not opened for write access.
	 */
	E_NOT_OPEN_FOR_WRITE (94),

	/**
	 * A value was passed that exceeded the allowed numeric range, either {@code
	 * int}, {@code long}, or some other limit imposed by the operating system
	 * or virtual machine.
	 */
	E_EXCEEDS_VM_LIMIT (95),

	/**
	 * {@linkplain Serializer Serialization} failed.
	 */
	E_SERIALIZATION_FAILED (96),

	/**
	 * {@linkplain Deserializer Deserialization} failed.
	 */
	E_DESERIALIZATION_FAILED (97),

	/**
	 * {@linkplain MessageSplitter} encountered inconsistent argument reordering
	 * indicators in a message name.
	 */
	E_INCONSISTENT_ARGUMENT_REORDERING (98),

	//	E_??? (99)

	/**
	 * A proposed {@linkplain BlockNodeDescriptor block expression} contains
	 * one or more invalid statements.
	 */
	E_BLOCK_CONTAINS_INVALID_STATEMENTS (100),

	/**
	 * A {@linkplain BlockNodeDescriptor block expression} is invalid.
	 */
	E_BLOCK_IS_INVALID (101),

	/**
	 * The {@linkplain BlockNodeDescriptor block expression} references outers,
	 * but must not.
	 */
	E_BLOCK_MUST_NOT_CONTAIN_OUTERS (102),

	/**
	 * The {@linkplain BlockNodeDescriptor block expression} failed compilation.
	 */
	E_BLOCK_COMPILATION_FAILED (103),

//	E_??? (104),

	/**
	 * A proposed {@linkplain SequenceNodeDescriptor sequence} contains one or
	 * more invalid statements.
	 */
	E_SEQUENCE_CONTAINS_INVALID_STATEMENTS (105),

	/**
	 * Attempted to create a supercast from a base expression that yields a
	 * value of type top or bottom.
	 */
	E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM (106),

	/**
	 * Attempted to create a supercast whose base expression is also a supercast
	 * phrase.
	 */
	E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST (107),

	/**
	 * Attempted to create a supercast from a base expression that yields a
	 * value of type top or bottom.
	 */
	E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE (108),

//	E_??? (109),

	/**
	 * The specified {@linkplain Path path} does not name an existing file.
	 */
	E_NO_FILE (200),

	/**
	 * The specified {@linkplain Path path} names an existing file.
	 */
	E_FILE_EXISTS (201),

	/**
	 * The specified {@linkplain Path path} names a directory that is not empty.
	 */
	E_DIRECTORY_NOT_EMPTY (202),

	/**
	 * An aggregation non-atomic operation succeeded only partially.
	 */
	E_PARTIAL_SUCCESS (203),

	/**
	 * At least one option was illegal, or possibly some combination of options
	 * were illegal.
	 */
	E_ILLEGAL_OPTION (204),

	/**
	 * A {@linkplain Path path} expression was invalid.
	 */
	E_INVALID_PATH (205),

	/**
	 * A Java {@linkplain Class class} specified by name was either not found by
	 * the runtime system or not available for reflection.
	 */
	E_JAVA_CLASS_NOT_AVAILABLE (500),

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} is abstract and therefore
	 * cannot be instantiated or have a {@linkplain Constructor constructor}
	 * bound to a {@linkplain FunctionDescriptor function}.
	 */
	E_POJO_TYPE_IS_ABSTRACT (501),

	/**
	 * The indicated Java {@linkplain Method method} or {@linkplain Constructor
	 * constructor} is not visible or does not exist.
	 */
	E_JAVA_METHOD_NOT_AVAILABLE (502),

//	E_??? (503),

	/**
	 * Marshaling an {@linkplain AvailObject Avail object} to a Java counterpart
	 * failed.
	 */
	E_JAVA_MARSHALING_FAILED (504),

	/**
	 * The indicated Java {@linkplain Field field} is not visible or does not
	 * exist.
	 */
	E_JAVA_FIELD_NOT_AVAILABLE (505),

	/**
	 * A reference to a {@linkplain Field Java field} is not uniquely
	 * resolvable for the given {@linkplain PojoTypeDescriptor pojo type}.
	 */
	E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS (506),

	/**
	 * An attempt was made to modify a {@linkplain Modifier#isFinal(int) final}
	 * {@linkplain Field Java field}.
	 */
	E_CANNOT_MODIFY_FINAL_JAVA_FIELD (507),

	/**
	 * A reference to a {@linkplain Method Java method} is not uniquely
	 * resolvable for the given {@linkplain PojoTypeDescriptor pojo type} and
	 * parameter {@linkplain TypeDescriptor types}.
	 */
	E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS (508);

	/** An array of all {@link AvailErrorCode} enumeration values. */
	private static AvailErrorCode[] all = values();

	/**
	 * Answer an array of all {@link AvailErrorCode} enumeration values.
	 *
	 * @return An array of all {@link AvailErrorCode} enum values.  Do not
	 *         modify the array.
	 */
	public static AvailErrorCode[] all ()
	{
		return all;
	}

	/** The numeric error code. */
	private final int code;

	/**
	 * Answer the numeric error code as a Java <strong>int</strong>.
	 *
	 * @return The numeric error code.
	 */
	public int nativeCode ()
	{
		return code;
	}

	/**
	 * Answer the numeric error code as an {@linkplain AvailObject Avail
	 * object}.
	 *
	 * @return The {@linkplain AvailObject numeric error code}.
	 */
	public A_Number numericCode ()
	{
		return IntegerDescriptor.fromInt(code);
	}

	/**
	 * Construct a new {@link AvailErrorCode} with the specified numeric error
	 * code.
	 *
	 * @param code
	 *        The numeric error code.
	 */
	private AvailErrorCode (final int code)
	{
		this.code = code;
	}

	/**
	 * Can the {@linkplain AvailErrorCode error code} result from failure of an
	 * {@link L1Instruction}?
	 *
	 * @return {@code true} if the error code can result from a failed
	 *         instruction, {@code false} otherwise.
	 */
	public boolean isCausedByInstructionFailure ()
	{
		return false;
	}

	/**
	 * The mapping from {@linkplain #code numeric codes} to {@link
	 * AvailErrorCode}s.
	 */
	private final static Map<Integer, AvailErrorCode> byNumericCode
		= new HashMap<Integer, AvailErrorCode>();

	// The enumeration values have been initialized, so build the map.
	static
	{
		for (final AvailErrorCode errorCode : values())
		{
			assert !byNumericCode.containsKey(errorCode.numericCode());
			byNumericCode.put(
				Integer.valueOf(errorCode.nativeCode()),
				errorCode);
		}
	}

	/**
	 * Look up the {@link AvailErrorCode} with the given {@linkplain #code
	 * numeric code}.
	 *
	 * @param numericCode The {@code int} to look up as a numeric code.
	 * @return The error code, or {@code null} if not defined.
	 */
	public static AvailErrorCode byNumericCode (final int numericCode)
	{
		return byNumericCode.get(numericCode);
	}

	/**
	 * Answer all valid {@linkplain #code numeric error codes}.
	 *
	 * @return A {@linkplain List list} of all valid numeric error codes.
	 */
	public static List<Integer> allNumericCodes ()
	{
		final List<Integer> codes = new ArrayList<>(values().length);
		for (final AvailErrorCode code : values())
		{
			// All right, not quite *all* of the numeric error codes, just the
			// ones that are encountered in typical ways.
			if (code.code > 0)
			{
				codes.add(code.code);
			}
		}
		return codes;
	}
}
