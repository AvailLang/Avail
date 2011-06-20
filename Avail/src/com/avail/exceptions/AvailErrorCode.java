/**
 * com.avail.exceptions/AvailErrorCode.java
 * Copyright (c) 2011, Mark van Gulik.
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

import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.node.*;
import com.avail.descriptor.*;

/**
 * {@code AvailErrorCode} is an enumeration of all possible failures of
 * operations on {@linkplain AvailObject Avail objects}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public enum AvailErrorCode
{
	/**
	 * Operation is required to fail.
	 */
	E_REQUIRED_FAILURE (0),

	/**
	 * Cannot {@linkplain AvailObject#plusCanDestroy(AvailObject, boolean)} add}
	 * {@linkplain InfinityDescriptor infinities} of unlike sign.
	 */
	E_CANNOT_ADD_UNLIKE_INFINITIES (1),

	/**
	 * Cannot {@linkplain AvailObject#minusCanDestroy(AvailObject, boolean)
	 * subtract} {@linkplain InfinityDescriptor infinities} of unlike sign.
	 */
	E_CANNOT_SUBTRACT_LIKE_INFINITIES (2),

	/**
	 * Cannot {@linkplain AvailObject#timesCanDestroy(AvailObject, boolean)
	 * multiply} {@linkplain IntegerDescriptor#zero() zero} and {@linkplain
	 * InfinityDescriptor infinity}.
	 */
	E_CANNOT_MULTIPLY_ZERO_AND_INFINITY (3),

	/**
	 * Cannot {@linkplain AvailObject#divideCanDestroy(AvailObject, boolean)
	 * divide} by {@linkplain IntegerDescriptor#zero() zero}.
	 */
	E_CANNOT_DIVIDE_BY_ZERO (4),

	/**
	 * Cannot {@linkplain AvailObject#divideCanDestroy(AvailObject, boolean)
	 * divide} two {@linkplain InfinityDescriptor infinities}.
	 */
	E_CANNOT_DIVIDE_INFINITIES (5),

	/**
	 * Cannot read from an unassigned {@linkplain ContainerDescriptor
	 * variable}.
	 */
	E_CANNOT_READ_UNASSIGNED_VARIABLE (6),

	/**
	 * Cannot write an incorrectly typed value into a {@linkplain
	 * ContainerDescriptor variable}.
	 */
	E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE_INTO_VARIABLE (7),

	/**
	 * Cannot swap the contents of two differently typed {@linkplain
	 * ContainerDescriptor variables}.
	 */
	E_CANNOT_SWAP_CONTENTS_OF_DIFFERENTLY_TYPED_VARIABLES (8),

	/**
	 * No such {@linkplain ProcessDescriptor process} variable.
	 */
	E_NO_SUCH_PROCESS_VARIABLE (9),

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
	 * {@linkplain ContinuationDescriptor Continuation} has no caller.
	 */
	E_CONTINUATION_HAS_NO_CALLER (13),

	/**
	 * {@linkplain ContinuationDescriptor Continuation} expected a stronger
	 * {@linkplain TypeDescriptor type}.
	 */
	E_CONTINUATION_EXPECTED_STRONGER_TYPE (14),

	/**
	 * The primitive is not currently supported on this platform.
	 */
	E_PRIMITIVE_NOT_SUPPORTED (15),

	/**
	 * Cannot uniquely determine instance of metatype smear that includes first
	 * level metatype.
	 */
	E_AMBIGUOUS_INSTANCE_OF_METATYPE_SMEAR (16),

	/**
	 * Metatypes must only have positive levels (>=1).
	 */
	E_NONPOSITIVE_METATYPE_LEVEL (17),

	/**
	 * A user-defined {@linkplain ObjectTypeDescriptor object type} has no
	 * assigned name.
	 */
	E_OBJECT_TYPE_HAS_NO_USER_DEFINED_NAME (18),

	/**
	 * No {@linkplain ImplementationSetDescriptor implementation set} exists
	 * for the specified {@linkplain CyclicTypeDescriptor name}.
	 */
	E_NO_IMPLEMENTATION_SET (19),

	/**
	 * The wrong number of outers were specified for creation of a {@linkplain
	 * ClosureDescriptor closure} from {@linkplain CompiledCodeDescriptor
	 * compiled code}.
	 */
	E_WRONG_NUMBER_OF_OUTERS (20),

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
	 * A primitive {@linkplain ClosureTypeDescriptor closure type} disagrees
	 * with the primitive's {@linkplain ClosureDescriptor restriction closure}.
	 */
	E_CLOSURE_DISAGREES_WITH_PRIMITIVE_RESTRICTION (27),

	/**
	 * A local type literal is not actually a {@linkplain TypeDescriptor type}.
	 */
	E_LOCAL_TYPE_LITERAL_IS_NOT_A_TYPE (28),

	/**
	 * An outer type literal is not actually a {@linkplain TypeDescriptor type}.
	 */
	E_OUTER_TYPE_LITERAL_IS_NOT_A_TYPE (29),

	/**
	 * Unhandled exception, i.e. no handler was found to accept a raised
	 * exception.
	 */
	E_UNHANDLED_EXCEPTION (30),

	/**
	 * The specific kind of {@linkplain SignatureDescriptor signature} does not
	 * support a {@linkplain ClosureDescriptor requires closure}.
	 */
	E_SIGNATURE_DOES_NOT_SUPPORT_REQUIRES_CLOSURE (31),

	/**
	 * The specific kind of {@linkplain SignatureDescriptor signature} does not
	 * support a {@linkplain ClosureDescriptor returns closure}.
	 */
	E_SIGNATURE_DOES_NOT_SUPPORT_RETURNS_CLOSURE (32),

	/**
	 * A {@linkplain AvailRuntime#specialObject(int) special object} number is
	 * invalid.
	 */
	E_INVALID_SPECIAL_OBJECT_NUMBER (33),

	/**
	 * A {@linkplain MacroSignatureDescriptor macro} {@linkplain
	 * ClosureDescriptor body} must restrict each parameter to be at least as
	 * specific as a {@linkplain ParseNodeDescriptor parse node}.
	 */
	E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE (34),

	/**
	 * There are multiple {@linkplain CyclicTypeDescriptor true names}
	 * associated with the string.
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
	E_DECLARATION_KIND_DOES_NOT_SUPPORT_REFERENCE (37);

	/** The numeric error code. */
	private final int code;

	/**
	 * Answer the numeric error code as a Java <strong>int</strong>.
	 *
	 * @return The numeric error code.
	 */
	public int nativeCode ()
	{
		return ordinal();
	}

	/**
	 * Answer the numeric error code as an {@linkplain AvailObject Avail
	 * object}.
	 *
	 * @return The {@linkplain AvailObject numeric error code}.
	 */
	public @NotNull AvailObject numericCode ()
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
}
