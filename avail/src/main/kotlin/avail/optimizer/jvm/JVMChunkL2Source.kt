/*
 * JVMChunkL2Source.kt
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
package avail.optimizer.jvm

import avail.interpreter.levelTwo.L2Chunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.L2ControlFlowGraph
import java.nio.file.Path

/**
 * `JVMChunkL2Source` captures an [absolute][Path.isAbsolute] [path][Path] to
 * the [L2ControlFlowGraph] of the [L2Chunk] that serves as the basis for the
 * annotated [method][ExecutableChunk.runChunk], making it available in
 * generated class files. This annotation is only attached if
 * [JVMTranslator.debugJVM] is enabled.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property sourcePath
 *   An [absolute][Path.isAbsolute] [path][Path] to the [L2ControlFlowGraph] of
 *   the [L2Chunk] that serves as the basis for the annotated
 *   [method][ExecutableChunk.runChunk].
 *
 * @constructor
 * Construct a [JVMChunkL2Source].
 *
 * @param sourcePath
 *   An [absolute][Path.isAbsolute] [path][Path] to the [L2ControlFlowGraph] of
 *   the [L2Chunk] that serves as the basis for the annotated
 *   [method][ExecutableChunk.runChunk].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
	AnnotationTarget.FUNCTION,
	AnnotationTarget.PROPERTY_GETTER,
	AnnotationTarget.PROPERTY_SETTER)
annotation class JVMChunkL2Source constructor(val sourcePath: String)
