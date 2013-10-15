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
package org.jboss.arquillian.container.osgi.felix;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.main.AutoProcessor;
import org.jboss.arquillian.container.osgi.OSGiContainerConfiguration;
import org.jboss.arquillian.container.osgi.AbstractEmbeddedDeployableContainer;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * FelixDeployableContainer
 *
 * @author thomas.diesler@jboss.com
 */
public class FelixDeployableContainer extends AbstractEmbeddedDeployableContainer<OSGiContainerConfiguration> {

    @Override
    public Class<OSGiContainerConfiguration> getConfigurationClass() {
        return OSGiContainerConfiguration.class;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Framework createFramework(OSGiContainerConfiguration conf) {

        // Add the logger if not given
        Map config = new HashMap(conf.getFrameworkConfiguration());
        if (config.get(FelixConstants.LOG_LOGGER_PROP) == null) {
            config.put(FelixConstants.LOG_LOGGER_PROP, new FelixLogger());
        }

        FrameworkFactory factory = conf.getFrameworkFactory();
        return factory.newFramework(config);
    }

    @Override
    @SuppressWarnings("static-access")
    public void start() throws LifecycleException {
        super.start();

        // Process the auto install settings
        AutoProcessor processor = new AutoProcessor();
        Map<String, String> config = getContainerConfiguration().getFrameworkConfiguration();
        processor.process(config, getSystemContext());

        installArquillianBundle();
    }

}
