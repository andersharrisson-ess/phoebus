/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.ui;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import org.phoebus.framework.util.ResourceParser;

/** Alarm URI helpers
 *
 *  <p>Alarm tools use a URI "alarm://host:port/config_name".
 *  For example, an alarm tree for the "Accelerator" configuration
 *  can be opened with the resource "alarm://localhost/Accelerator?app=alarm_tree"
 *
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class AlarmURI
{
    /** URI schema used to refer to an alarm config */
    public static final String SCHEMA = "alarm";

    /** @param server Kafka server host:port
     *  @param config_name Alarm configuration root
     *  @return URI used to access that alarm configuration, "alarm://host:port/config_name"
     */
    public static URI createURI(final String server, final String config_name)
    {
        return URI.create(SCHEMA + "://" + ResourceParser.encode(server + "/" + config_name));
	//return URI.create(SCHEMA + "://" + URLEncoder.encode(server + "/" + config_name), "UTF_8");
    }

    /** Parse alarm configuration parameters from URI
     *  @param resource "alarm://localhost:9092/Accelerator"
     *  @return [ "localhost:9092", "Accelerator" ]
     *  @throws Exception on error
     */
    public static String[] parseAlarmURI(final URI resource) throws Exception
    {
        if (! SCHEMA.equals(resource.getScheme()))
            throw new Exception("Cannot parse " + resource + ", expecting " + SCHEMA + "://{host}:{port}/{config_name}");

	String[] splitURI = (resource.toString()).split("://");
	String decodedAuthorityPath = ResourceParser.decode(splitURI[1]);
	//String decodedAuthorityPath = URLDecoder.decode(splitURI[1], "UTF_8");

	// Re-create the URI with decoded authority & path
	URI recResource = URI.create(SCHEMA + "://" + decodedAuthorityPath);

	String config_name = recResource.getPath();
        if (config_name.startsWith("/"))
            config_name = config_name.substring(1);
        if (config_name.isEmpty())
            throw new Exception("Missing alarm config name in " + resource + ", expecting " + SCHEMA + "://{host}:{port}/{config_name}");

	// Check if user provided multiple kafka hosts i.e. host1:port1,host2:port2,host3:port3
	if (decodedAuthorityPath.contains(","))
	{
	    String[] splitAuthorityPath = decodedAuthorityPath.split("/");
	    return new String[] { splitAuthorityPath[0], splitAuthorityPath[1] };
	}

        // Default to port 9092
        int port = resource.getPort();
        if (port < 0)
            port = 9092;
        
        return new String[] { resource.getHost() + ":" + port, config_name };
    }
}
