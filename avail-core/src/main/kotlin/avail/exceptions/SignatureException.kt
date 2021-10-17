/*
 * SignatureException.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.methods.DefinitionDescriptor

/**
 * A `SignatureException` is thrown when a [definition][DefinitionDescriptor] of
 * a method is invalid.  This might indicate a compatibility problem between the
 * argument signature and the message name, or perhaps an inconsistency between
 * the signature and other signatures already installed in the system.
 *
 * Note that this is distinct from a [MalformedMessageException], which
 * indicates a syntactic error in the message name itself.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @see DefinitionDescriptor
 * @see MalformedMessageException
 *
 * @constructor
 * Construct a new `SignatureException` with the specified
 * [error&#32;code][AvailErrorCode].
 *
 * @param errorCode
 *   The [error&#32;code][AvailErrorCode].
 */
class SignatureException constructor(errorCode: AvailErrorCode)
	: AvailException(errorCode)
