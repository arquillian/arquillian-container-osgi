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
package org.jboss.arquillian.container.osgi.equinox;

import java.util.HashMap;
import java.util.Map;

import org.jboss.arquillian.container.osgi.EmbeddedContainerConfiguration;
import org.jboss.arquillian.container.osgi.EmbeddedDeployableContainer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Equinox deployable container
 *
 * @author th_janssen@outlook.com
 * @author sebastian.p.lorenz@googlemail.com
 */
public class EquinoxEmbeddedDeployableContainer extends
        EmbeddedDeployableContainer<EmbeddedContainerConfiguration> {

    static final Logger logger = LoggerFactory.getLogger(EquinoxEmbeddedDeployableContainer.class.getPackage().getName());

    @Override
    public Class<EmbeddedContainerConfiguration> getConfigurationClass() {
        return EmbeddedContainerConfiguration.class;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Framework createFramework(EmbeddedContainerConfiguration conf) {

        // Set the configuration properties as system properties
        Map config = new HashMap(conf.getFrameworkConfiguration());
        for (Object key : config.keySet()) {
            System.setProperty((String) key, config.get(key).toString());
        }

        FrameworkFactory factory = conf.getFrameworkFactory();
        return factory.newFramework(config);
    }

    @Override
    protected BundleContext startFramework() throws BundleException {
        BundleContext bundleContext = super.startFramework();

        return bundleContext;
    }

    @Override
    protected ContainerLogger getLogger() {
        return new AbstractContainerLogger() {
            @Override
            public void log(Level level, String message, Throwable th) {
                if (logger != null) {
                    switch (level) {
                    case DEBUG:
                        logger.debug(message, th);
                        break;
                    case INFO:
                        logger.info(message, th);
                        break;
                    case WARN:
                        logger.warn(message, th);
                        break;
                    case ERROR:
                        logger.error(message, th);
                        break;
                    }
                }
            }
        };
    }
}
