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
package org.jboss.arquillian.container.osgi.karaf;

import org.apache.karaf.main.Main;
import org.jboss.arquillian.container.osgi.AbstractEmbeddedDeployableContainer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KarafDeployableContainer
 *
 * @author thomas.diesler@jboss.com
 */
public class KarafDeployableContainer extends AbstractEmbeddedDeployableContainer<KarafContainerConfiguration> {

    static final Logger logger = LoggerFactory.getLogger(KarafDeployableContainer.class.getPackage().getName());

    private Main karaf;

    @Override
    public Class<KarafContainerConfiguration> getConfigurationClass() {
        return KarafContainerConfiguration.class;
    }

    @Override
    protected Framework createFramework(KarafContainerConfiguration conf) {

        String karafHome = System.getProperty(Main.PROP_KARAF_HOME);
        if (karafHome == null && conf.getKarafHome() != null) {
            System.setProperty(Main.PROP_KARAF_HOME, conf.getKarafHome());
        }

        karaf = new Main(null);
        try {
            karaf.launch();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        return karaf.getFramework();
    }

    @Override
    protected BundleContext startFramework() throws BundleException {
        return karaf.getFramework().getBundleContext();
    }

    @Override
    protected void stopFramework() throws BundleException {
        try {
            karaf.destroy();
        } catch (Exception ex) {
            throw new BundleException("Cannot stop Karaf", ex);
        }
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
