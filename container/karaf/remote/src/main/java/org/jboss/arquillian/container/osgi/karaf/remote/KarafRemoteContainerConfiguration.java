/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.osgi.karaf.remote;

import org.jboss.arquillian.container.osgi.jmx.JMXContainerConfiguration;
import org.jboss.arquillian.container.spi.ConfigurationException;

/**
 * KarafRemoteContainerConfiguration
 *
 * @author sbunciak
 *
 */
public class KarafRemoteContainerConfiguration extends JMXContainerConfiguration {

    public static final String DEFAULT_JMX_SERVICE_URL = "service:jmx:rmi://localhost:44444/jndi/rmi://localhost:1099/karaf-root";
    public static final String DEFAULT_JMX_USERNAME = "karaf";
    public static final String DEFAULT_JMX_PASSWORD = "karaf";

    @Override
    public void validate() throws ConfigurationException {
        super.validate();
        if (jmxServiceURL == null)
            setJmxServiceURL(DEFAULT_JMX_SERVICE_URL);
        if (jmxUsername == null)
            setJmxUsername(DEFAULT_JMX_USERNAME);
        if (jmxPassword == null)
            setJmxPassword(DEFAULT_JMX_PASSWORD);
    }
}
