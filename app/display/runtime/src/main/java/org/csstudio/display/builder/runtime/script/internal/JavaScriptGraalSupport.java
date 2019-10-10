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
import org.graalvm.polyglot.*;

import javax.script.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static org.csstudio.display.builder.runtime.WidgetRuntime.logger;

/** JavaScript support for Graal JS engine
 *  @author Georg Weiss
 */
@SuppressWarnings("nls")
class JavaScriptGraalSupport extends BaseScriptSupport
{
    private final ScriptSupport support;
    //private Context polyglotContext;
    private Value mainFunction;
    private final ScriptEngine engine;

    /** Create executor for java scripts
     *  @param support {@link ScriptSupport}
     */
    public JavaScriptGraalSupport(final ScriptSupport support) throws Exception
    {
        this.support = support;
        engine = Objects.requireNonNull(new ScriptEngineManager().getEngineByName("graal.js"));
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

        Context polyglotContext = Context.newBuilder()
                .allowHostAccess(HostAccess.ALL)
                .allowAllAccess(true)
                .allowExperimentalOptions(true).option("js.nashorn-compat", "true")
                .build();


        Source source = Source.newBuilder("js", new BufferedReader(new InputStreamReader(stream)), name).build();

        polyglotContext.eval(source);

        return new JavaScriptGraal(polyglotContext,this, source);
    }

    /** Request that a script gets executed
     *  @param script {@link JavaScript}
     *  @param widget Widget that requests execution
     *  @param pvs PVs that are available to the script
     *  @return
     */
    public Future<Object> submit(final JavaScriptGraal script, final Widget widget, final RuntimePV... pvs)
    {
        // Skip script that's already in the queue.
        if (! markAsScheduled(script))
            return null;

        return support.submit(() ->
        {

            // Script may be queued again
            removeScheduleMarker(script);
            try
            {
                Context polyglotContext = script.getContext();
                polyglotContext.getBindings("js").putMember("widget", widget);
                polyglotContext.getBindings("js").putMember("pvs", pvs);
                long start = System.currentTimeMillis();
                polyglotContext.getBindings("js").getMember("func").execute();
                long stop = System.currentTimeMillis();
                System.out.println("JS execution time for " + script.getSource().getCharacters().toString().substring(300,350) + " "  + (stop - start));
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Execution of '" + script + "' failed", ex);
            }

            return null;
        });
    }
}
