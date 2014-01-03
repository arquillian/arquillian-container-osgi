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
package org.jboss.arquillian.container.osgi.karaf.embedded;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.karaf.main.Main;
import org.jboss.arquillian.container.osgi.EmbeddedDeployableContainer;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KarafEmbeddedDeployableContainer
 *
 * @author thomas.diesler@jboss.com
 */
public class KarafEmbeddedDeployableContainer extends EmbeddedDeployableContainer<KarafEmbeddedContainerConfiguration> {

    static final Logger LOGGER = LoggerFactory.getLogger(KarafEmbeddedDeployableContainer.class.getPackage().getName());

    private Main karaf;

    @Override
    public Class<KarafEmbeddedContainerConfiguration> getConfigurationClass() {
        return KarafEmbeddedContainerConfiguration.class;
    }

    @Override
    protected KarafEmbeddedContainerConfiguration getContainerConfiguration() {
        return (KarafEmbeddedContainerConfiguration) super.getContainerConfiguration();
    }

    @Override
    protected Framework createFramework(KarafEmbeddedContainerConfiguration conf) {

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
        Framework framework = karaf.getFramework();
        return framework.getBundleContext();
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
    protected void awaitArquillianBundleActive(BundleContext syscontext, long timeout, TimeUnit unit) throws LifecycleException {
        super.awaitArquillianBundleActive(syscontext, timeout, unit);
        KarafEmbeddedContainerConfiguration config = getContainerConfiguration();
        Integer beginningStartLevel = config.getKarafBeginningStartLevel();
        if (beginningStartLevel != null) {
            awaitKarafBeginningStartLevel(syscontext, beginningStartLevel, timeout, unit);
        }
    }

    @Override
    protected ContainerLogger getLogger() {
        return new AbstractContainerLogger() {
            @Override
            public void log(Level level, String message, Throwable th) {
                switch (level) {
                case DEBUG:
                    LOGGER.debug(message, th);
                    break;
                case INFO:
                    LOGGER.info(message, th);
                    break;
                case WARN:
                    LOGGER.warn(message, th);
                    break;
                case ERROR:
                    LOGGER.error(message, th);
                    break;
                }
            }
        };
    }

    protected void awaitKarafBeginningStartLevel(final BundleContext syscontext, final Integer beginningStartLevel, long timeout, TimeUnit unit) {
        final CountDownLatch latch = new CountDownLatch(1);
        final FrameworkStartLevel fwrkStartLevel = syscontext.getBundle().adapt(FrameworkStartLevel.class);
        FrameworkListener listener = new FrameworkListener() {
            @Override
            public void frameworkEvent(FrameworkEvent event) {
                if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED) {
                    int startLevel = fwrkStartLevel.getStartLevel();
                    if (startLevel == beginningStartLevel) {
                        latch.countDown();
                    }
                }
            }
        };
        syscontext.addFrameworkListener(listener);
        try {
            int startLevel = fwrkStartLevel.getStartLevel();
            if (startLevel < beginningStartLevel) {
                try {
                    if (!latch.await(timeout, unit))
                        throw new IllegalStateException("Giving up waiting to reach start level: " + beginningStartLevel);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        } finally {
            syscontext.removeFrameworkListener(listener);
        }
    }
}
