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
package org.jboss.arquillian.container.osgi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * OSGi container configuration
 *
 * @author thomas.diesler@jboss.com
 */
public class OSGiContainerConfiguration implements ContainerConfiguration {

    private final Map<String, String> frameworkConfiguration = new HashMap<String, String>();
    private FrameworkFactory frameworkFactory;
    private String frameworkProperties;
    private String bootstrapCompleteService;

    @Override
    public void validate() throws ConfigurationException {

        // Get the framework configuration
        if (frameworkProperties != null) {
            File file = new File(frameworkProperties);
            try {
                FileInputStream input = new FileInputStream(file);
                Properties props = new Properties();
                props.load(input);
                for (String key : props.stringPropertyNames()) {
                    frameworkConfiguration.put(key, props.getProperty(key));
                }
            } catch (IOException ex) {
                throw new ConfigurationException("Cannot read: " + file.getAbsolutePath());
            }
        }

        // Get the {@link FrameworkFactory}
        Iterator<FrameworkFactory> factories = ServiceLoader.load(FrameworkFactory.class).iterator();
        if (factories.hasNext()) {
            frameworkFactory = factories.next();
        }
    }

    public String getFrameworkProperties() {
        return frameworkProperties;
    }

    public void setFrameworkProperties(String frameworkProperties) {
        this.frameworkProperties = frameworkProperties;
    }

    public String getBootstrapCompleteService() {
        return bootstrapCompleteService;
    }

    public void setBootstrapCompleteService(String bootstrapCompleteService) {
        this.bootstrapCompleteService = bootstrapCompleteService;
    }

    public FrameworkFactory getFrameworkFactory() {
        return frameworkFactory;
    }

    public Map<String, String> getFrameworkConfiguration() {
        return Collections.unmodifiableMap(frameworkConfiguration);
    }
}
