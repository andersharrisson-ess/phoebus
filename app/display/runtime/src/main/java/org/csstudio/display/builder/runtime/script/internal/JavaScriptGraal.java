/**
 * Copyright (C) 2019 European Spallation Source ERIC.
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.csstudio.display.builder.runtime.script.internal;

import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.runtime.pv.RuntimePV;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.util.concurrent.Future;

public class JavaScriptGraal implements Script {

    private final JavaScriptGraalSupport support;
    private final Source source;
    private final Context context;

    public JavaScriptGraal(Context context, JavaScriptGraalSupport support, Source source){
        this.context = context;
        this.support = support;
        this.source = source;
    }

    public Source getSource(){
        return source;
    }

    public Context getContext(){
        return context;
    }

    @Override
    public Future<Object> submit(final Widget widget, final RuntimePV... pvs)
    {
        return support.submit(this, widget, pvs);
    }
}
