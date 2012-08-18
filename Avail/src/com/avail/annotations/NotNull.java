/**
 * NotNull.java
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

package com.avail.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code NotNull} annotation indicates that the annotated target must not
 * yield a {@code null} when evaluated. The precise meaning depends upon the
 * type of the target:
 *
 * <p><ul>
 * <li><strong>Static field.</strong> Once all {@code static} initializers have
 * run to completion, the field must not and must never again contain {@code
 * null}.</li>
 * <li><strong>Instance field.</strong> Once the invoked constructor chain and
 * all instance initializers have run to completion, the field must not and must
 * never again contain {@code null}.</li>
 * <li><strong>Method.</strong> No invocation of the method is permitted to
 * yield {@code null} as its return value.</li>
 * <li><strong>Method parameter.</strong> No invocation of the method is
 * permitted to bind a {@code null} argument to the formal parameter.</li>
 * <li><strong>Local variable.</strong> Once the local variable has been
 * initialized, it must not and must never contain {@code null}.</li>
 * </ul></p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Retention(RetentionPolicy.CLASS)
@Target({
	ElementType.FIELD,
	ElementType.LOCAL_VARIABLE,
	ElementType.METHOD,
	ElementType.PARAMETER
})
public @interface NotNull
{
	// No implementation required.
}
