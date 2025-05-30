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

package org.finos.legend.pure.runtime.java.compiled.metadata;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.map.MapIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

public interface Metadata
{
    void startTransaction();

    void commitTransaction();

    void rollbackTransaction();

    CoreInstance getMetadata(String classifier, String id);

    MapIterable<String, CoreInstance> getMetadata(String classifier);

    default RichIterable<CoreInstance> getClassifierInstances(String classifier)
    {
        return getMetadata(classifier).valuesView();
    }

    CoreInstance getEnum(String enumerationName, String enumName);
}
