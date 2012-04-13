/**
 * L2OperationDispatcher.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo;


/**
 * {@code L2OperationDispatcher} enumerates the responsibilities that must be
 * satisfied in order to process the {@linkplain L2Instruction level two Avail
 * instruction set}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
interface L2OperationDispatcher
{
	/**
	 * Process an
	 * {@link L2Operation#L2_UNKNOWN_WORDCODE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_UNKNOWN_WORDCODE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_LABEL}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_LABEL ();

	/**
	 * Process an {@link L2Operation#L2_ENTER_L2_CHUNK} {@linkplain L2Operation
	 * operation}.
	 */
	public void L2_ENTER_L2_CHUNK ();

	/**
	 * Process an {@link L2Operation#L2_REENTER_L2_CHUNK} {@linkplain
	 * L2Operation operation}.
	 */
	public void L2_REENTER_L2_CHUNK ();

	/**
	 * Process an
	 * {@link L2Operation#L2_PREPARE_NEW_FRAME}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_PREPARE_NEW_FRAME ();

	/**
	 * Process an
	 * {@link L2Operation#L2_INTERPRET_UNTIL_INTERRUPT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_INTERPRET_UNTIL_INTERRUPT ();

	/**
	 * Process an {@link L2Operation#L2_REENTER_L1_CHUNK} {@linkplain
	 * L2Operation operation}.
	 */
	public void L2_REENTER_L1_CHUNK ();

	/**
	 * Process an
	 * {@link L2Operation#L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MOVE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MOVE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MOVE_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MOVE_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MOVE_OUTER_VARIABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MOVE_OUTER_VARIABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_VARIABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_VARIABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_GET_VARIABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_GET_VARIABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_GET_VARIABLE_CLEARING}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_GET_VARIABLE_CLEARING ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SET_VARIABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SET_VARIABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CLEAR_VARIABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CLEAR_VARIABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CLEAR_VARIABLES}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CLEAR_VARIABLES ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ADD_INTEGER_CONSTANT_TO_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ADD_INTEGER_CONSTANT_TO_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ADD_INTEGER_CONSTANT_TO_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ADD_INTEGER_CONSTANT_TO_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ADD_OBJECT_TO_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ADD_OBJECT_TO_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ADD_INT_TO_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ADD_INT_TO_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ADD_INT_TO_INT_MOD_32_BITS}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ADD_INT_TO_INT_MOD_32_BITS ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUBTRACT_CONSTANT_INTEGER_FROM_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUBTRACT_CONSTANT_INTEGER_FROM_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUBTRACT_CONSTANT_INTEGER_FROM_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUBTRACT_CONSTANT_INTEGER_FROM_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUBTRACT_OBJECT_FROM_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUBTRACT_OBJECT_FROM_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUBTRACT_INT_FROM_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUBTRACT_INT_FROM_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MULTIPLY_CONSTANT_OBJECT_BY_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MULTIPLY_CONSTANT_OBJECT_BY_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MULTIPLY_CONSTANT_OBJECT_BY_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MULTIPLY_CONSTANT_OBJECT_BY_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MULTIPLY_OBJECT_BY_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MULTIPLY_OBJECT_BY_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MULTIPLY_INT_BY_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MULTIPLY_INT_BY_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MULTIPLY_INT_BY_INT_MOD_32_BITS}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MULTIPLY_INT_BY_INT_MOD_32_BITS ();

	/**
	 * Process an
	 * {@link L2Operation#L2_DIVIDE_OBJECT_BY_CONSTANT_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_DIVIDE_OBJECT_BY_CONSTANT_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_DIVIDE_INT_BY_CONSTANT_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_DIVIDE_INT_BY_CONSTANT_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_DIVIDE_OBJECT_BY_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_DIVIDE_OBJECT_BY_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_DIVIDE_INT_BY_INT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_DIVIDE_INT_BY_INT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_OBJECTS_EQUAL}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_OBJECTS_EQUAL ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_EQUALS_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_EQUALS_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_OBJECTS_NOT_EQUAL}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_OBJECTS_NOT_EQUAL ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_DOES_NOT_EQUAL_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_DOES_NOT_EQUAL_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_LESS_THAN_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_LESS_THAN_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_LESS_THAN_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_LESS_THAN_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_LESS_THAN_OR_EQUAL_TO_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_GREATER_THAN_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_GREATER_THAN_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_GREATER_THAN_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_GREATER_THAN_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_GREATER_THAN_OR_EQUAL_TO_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_KIND_OF_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_KIND_OF_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_KIND_OF_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_KIND_OF_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_INTERRUPT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_INTERRUPT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_JUMP_IF_NOT_INTERRUPT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_JUMP_IF_NOT_INTERRUPT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_PROCESS_INTERRUPT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_PROCESS_INTERRUPT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_CONTINUATION}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_CONTINUATION ();

	/**
	 * Process an
	 * {@link L2Operation#L2_UPDATE_CONTINUATION_SLOT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_UPDATE_CONTINUATION_SLOT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_UPDATE_CONTINUATION_PC_AND_STACKP_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_UPDATE_CONTINUATION_PC_AND_STACKP_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SEND}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SEND ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SEND_AFTER_FAILED_PRIMITIVE_}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SEND_AFTER_FAILED_PRIMITIVE_ ();

	/**
	 * Process an
	 * {@link L2Operation#L2_SUPER_SEND}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_SUPER_SEND ();

	/**
	 * Process an
	 * {@link L2Operation#L2_EXPLODE_CONTINUATION}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_EXPLODE_CONTINUATION ();

	/**
	 * Process an
	 * {@link L2Operation#L2_GET_TYPE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_GET_TYPE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_TUPLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_TUPLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CONCATENATE_TUPLES}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CONCATENATE_TUPLES ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_SET}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_SET ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_MAP}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_MAP ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_OBJECT}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_OBJECT ();

	/**
	 * Process an
	 * {@link L2Operation#L2_CREATE_FUNCTION}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_CREATE_FUNCTION ();

	/**
	 * Process an
	 * {@link L2Operation#L2_RETURN}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_RETURN ();

	/**
	 * Process an
	 * {@link L2Operation#L2_EXIT_CONTINUATION}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_EXIT_CONTINUATION ();

	/**
	 * Process an
	 * {@link L2Operation#L2_RESUME_CONTINUATION}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_RESUME_CONTINUATION ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MAKE_IMMUTABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MAKE_IMMUTABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_MAKE_SUBOBJECTS_IMMUTABLE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_MAKE_SUBOBJECTS_IMMUTABLE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_ATTEMPT_INLINE_PRIMITIVE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_ATTEMPT_INLINE_PRIMITIVE ();

	/**
	 * Process an
	 * {@link L2Operation#L2_RUN_INFALLIBLE_PRIMITIVE}
	 * {@linkplain L2Operation operation}.
	 */
	public void L2_RUN_INFALLIBLE_PRIMITIVE ();
}
