/*
 * L2Visualizable.kt
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
package avail.optimizer

import avail.optimizer.values.L2SemanticValue

/**
 * The [L2Visualizable] interrface suupports visualization operations through
 * [L2ControlFlowGraphVisualizer], ultimately derived from an
 * [L2ControlFlowGraph]'s information.
 *
 * @author Mark van Gulik &lt;wark@availlang.org&gt;
 */
interface L2Visualizable
{
	/**
	 * Answer a visualization of this [L2ControlFlowGraph]. This is a
	 * debug method, intended to be called via evaluation during debugging.
	 *
	 * @param generator
	 *   The [L2Generator], if any, that is in the process of populating this
	 *   graph.
	 * @param focusValue
	 *   An optional [L2SemanticValue] to focus on in the visualization.
	 */
	fun visualize(
		generator: L2Generator? = null,
		focusValue: L2SemanticValue<*>? = null
	): Unit

	/**
	 * Answer a visualization of this [L2ControlFlowGraph]. This is a
	 * debug method, intended to be called via evaluation during debugging.
	 *
	 * @param generator
	 *   The [L2Generator], if any, that is in the process of populating this
	 *   graph.
	 * @param focusValue
	 *   An optional [L2SemanticValue] to focus on in the visualization.
	 */
	fun simplyVisualize(
		generator: L2Generator? = null,
		focusValue: L2SemanticValue<*>? = null
	): Unit
}
