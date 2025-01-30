// Copyright 2024 Goldman Sachs
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

package org.finos.legend.pure.runtime.java.interpreted;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m3.pct.shared.provider.PCTReportProvider;
import org.finos.legend.pure.m3.pct.reports.model.AdapterReport;
import org.finos.legend.pure.m3.pct.functions.model.Functions;
import org.finos.legend.pure.m3.pct.shared.provider.PCTReportProviderTool;

public class Core_Interpreted_PCTReportProvider implements PCTReportProvider
{
    @Override
    public MutableList<Functions> getFunctions()
    {
        return Lists.mutable.empty();
    }

    @Override
    public MutableList<AdapterReport> getAdapterReports()
    {
        return PCTReportProviderTool.load(Core_Interpreted_PCTReportProvider.class.getClassLoader(), AdapterReport.class,
                "pct-reports/ADAPTER_essential_interpreted_Native.json",
                "pct-reports/ADAPTER_grammar_interpreted_Native.json"
        );
    }
}
