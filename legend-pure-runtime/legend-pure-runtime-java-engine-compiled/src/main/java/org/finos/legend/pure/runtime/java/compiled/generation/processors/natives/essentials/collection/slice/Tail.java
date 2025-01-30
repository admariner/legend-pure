// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.essentials.collection.slice;

import org.eclipse.collections.api.RichIterable;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.AbstractNativeFunctionGeneric;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.support.CompiledSupport;


public class Tail extends AbstractNativeFunctionGeneric
{
    public Tail()
    {
        super(getMethod(CompiledSupport.class, "tail", RichIterable.class), "tail_T_MANY__T_MANY_");
    }
}
