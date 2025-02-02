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

package org.finos.legend.pure.runtime.java.extension.functions.compiled.natives.anonymousCollections;

import org.finos.legend.pure.m3.coreinstance.meta.pure.functions.collection.TreeNode;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.AbstractNativeFunctionGeneric;

public class ReplaceTreeNode extends AbstractNativeFunctionGeneric
{
    public ReplaceTreeNode() {
        super("Pure.replaceTreeNode", new Class[]{TreeNode.class, TreeNode.class, TreeNode.class}, "replaceTreeNode_TreeNode_1__TreeNode_1__TreeNode_1__TreeNode_1_");
    }
}
