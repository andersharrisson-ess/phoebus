/*******************************************************************************
 * Copyright (c) 2015-2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.runtime.script.internal;

import static org.csstudio.display.builder.runtime.WidgetRuntime.logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.logging.Level;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.runtime.pv.RuntimePV;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

/** JavaScript support
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
class JavaScriptSupport extends BaseScriptSupport
{
    private final ScriptSupport support;
    private final ScriptEngine engine;
    private final Bindings bindings;

    /** Create executor for java scripts
     *  @param support {@link ScriptSupport}
     */
    public JavaScriptSupport(final ScriptSupport support) throws Exception
    {
        this.support = support;
        // We always create the JS engine, even when not used, so disable
        // 'Warning: Nashorn engine is planned to be removed from a future JDK release':
        Context polyglotContext = Context.newBuilder()
                .allowHostAccess(HostAccess.ALL)
                .allowAllAccess(true)
                .allowExperimentalOptions(true).option("js.nashorn-compat", "true")
                .build();
        System.setProperty("nashorn.args", "--no-deprecation-warning");
        //engine = Objects.requireNonNull(new ScriptEngineManager().getEngineByName("graal.js"));
        engine = GraalJSScriptEngine.create(null,
                Context.newBuilder("js")
                        .allowHostAccess(HostAccess.ALL)
                        .allowExperimentalOptions(true).option("js.nashorn-compat", "true")
                        .allowHostClassLookup(s -> true));
        //engine.setContext(polyglotContext);
        bindings = engine.createBindings();
    }

    /** Parse and compile script file
    *
    *  @param name Name of script (file name, URL)
    *  @param stream Stream for the script content
    *  @return {@link Script}
    *  @throws Exception on error
    */
    public synchronized Script compile(final String name, final InputStream stream) throws Exception
    {
        // End users who actually _use_ JS do need a warning that it might undergo changes
        logger.log(Level.WARNING, "JavaScript support based on 'nashorn' is deprecated (" + name + ")");
        final CompiledScript code = ((Compilable) engine).compile(new InputStreamReader(stream));
        return new JavaScript(this, name, code);
    }

    /** Request that a script gets executed
     *  @param script {@link JavaScript}
     *  @param widget Widget that requests execution
     *  @param pvs PVs that are available to the script
     *  @return
     */
    public Future<Object> submit(final JavaScript script, final Widget widget, final RuntimePV... pvs)
    {
        // Skip script that's already in the queue.
        if (! markAsScheduled(script))
            return null;

        return support.submit(() ->
        {
            long start = System.currentTimeMillis();
            // Script may be queued again
            removeScheduleMarker(script);
            try
            {
                bindings.put("widget", widget);
                bindings.put("pvs", pvs);
                script.getCode().eval(bindings);
                long stop = System.currentTimeMillis();
                System.out.println("JS execution time " + (stop - start));
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Execution of '" + script + "' failed", ex);
            }
            return null;
        });
    }
}
