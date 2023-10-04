/*
 * MathExtensions.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.utility

/** The value 0x0101010101010101L, which has the low bit set in each byte. */
const val lowBitsOfBytes: Long = 0x0101010101010101L

/** The value 0x8080808080808080L, which has the high bit set in each byte. */
const val highBitsOfBytes: Long = lowBitsOfBytes.shl(7)

/** Answer true iff any byte of the receiver is zero. */
fun Long.hasZeroByte(): Boolean =
	(this - lowBitsOfBytes) and this.inv() and highBitsOfBytes != 0L

/** The value 0x0001000100010001L, which has the low bit set in each short. */
const val lowBitsOfShorts: Long = 0x0001000100010001L

/** The value 0x8000800080008000L, which has the high bit set in each short. */
const val highBitsOfShorts: Long = lowBitsOfShorts.shl(15)

/** Answer true iff any short of the receiver is zero. */
fun Long.hasZeroShort(): Boolean =
	(this - lowBitsOfShorts) and this.inv() and highBitsOfShorts != 0L
