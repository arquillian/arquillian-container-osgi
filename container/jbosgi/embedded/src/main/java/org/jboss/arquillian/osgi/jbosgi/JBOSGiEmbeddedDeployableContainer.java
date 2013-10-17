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
package org.jboss.arquillian.osgi.jbosgi;

import org.jboss.arquillian.container.osgi.OSGiContainerConfiguration;
import org.jboss.arquillian.container.osgi.AbstractEmbeddedDeployableContainer;
import org.jboss.logging.Logger;

/**
 * OSGi embedded container
 *
 * @author thomas.diesler@jboss.com
 */
public class JBOSGiEmbeddedDeployableContainer extends AbstractEmbeddedDeployableContainer<OSGiContainerConfiguration> {

    private final Logger logger = Logger.getLogger(JBOSGiEmbeddedDeployableContainer.class.getPackage().getName());

    @Override
    public Class<OSGiContainerConfiguration> getConfigurationClass() {
        return OSGiContainerConfiguration.class;
    }

    @Override
    protected ContainerLogger getLogger() {
        return new AbstractContainerLogger() {
            @Override
            public void log(Level level, String message, Throwable th) {
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
        };
    }
}
