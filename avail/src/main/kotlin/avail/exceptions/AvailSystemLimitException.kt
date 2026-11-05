/*
 * AvailSystemLimitException.kt
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
package avail.exceptions

/**
 * An [AvailSystemLimitException] reports that the VM has reached a system
 * limit, such as the largest integer it can represent.  It is a panic, not an
 * ordinary primitive failure: it terminates the fiber that hit the limit, and
 * that fiber's failure continuation receives this exception.  Ordinary
 * semantic failures are reported through the failure mechanism instead, and
 * primitives that can't otherwise fail never need to handle a system limit.
 *
 * It extends [PrimitiveThrownException] so that it takes the controlled fiber
 * termination path rather than the unrecoverable VM error path.  It does not
 * extend [AvailRuntimeException], which is the family of ordinary failures.
 *
 * @property message
 *   A description of the limit that was reached.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AvailSystemLimitException(
	override val message: String
) : PrimitiveThrownException()
