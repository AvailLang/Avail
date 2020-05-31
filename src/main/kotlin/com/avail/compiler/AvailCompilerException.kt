/*
 * AvailCompilerException.kt
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

package com.avail.compiler

import com.avail.builder.ModuleName
import com.avail.descriptor.module.ModuleDescriptor

/**
 * An `AvailCompilerException` is thrown by the
 * [Avail&#32;compiler][AvailCompiler] when compilation fails for any reason.
 *
 * @property moduleName
 *   The [fully-qualified name][ModuleName] of the [module][ModuleDescriptor]
 *   undergoing [compilation][AvailCompiler].
 * @property position
 *   The position within the [module][ModuleDescriptor] undergoing
 *   [compilation][AvailCompiler] at which the error was detected.
 * @property endOfErrorLine
 *   The position within the [module][ModuleDescriptor] undergoing
 *   [compilation][AvailCompiler] of the first line break after the position at
 *   which the error was detected.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [AvailCompilerException].
 *
 * @param moduleName
 *   The [fully-qualified name][ModuleName] of the [module][ModuleDescriptor]
 *   undergoing [compilation][AvailCompiler].
 * @param position
 *   The position within the [module][ModuleDescriptor] undergoing
 *   [compilation][AvailCompiler] at which the error was detected.
 * @param endOfErrorLine
 *   The position within the [module][ModuleDescriptor]'s source of the line
 *   break following the error.  Useful for displaying the error in context.
 * @param errorText
 *   The text of the error message, intended for display at the encapsulated
 *   position.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class AvailCompilerException internal constructor(
	val moduleName: ModuleName,
	val position: Long,
	val endOfErrorLine: Long,
	errorText: String) : Exception(errorText)
