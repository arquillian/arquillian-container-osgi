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

import org.jboss.arquillian.container.osgi.jmx.JMXDeployableContainer;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;

import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;
import org.osgi.jmx.framework.ServiceStateMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Remote deployable container for Karaf.
 * <p>
 * Should also work with any remote container with an OSGi Enterprise JMX MBeans implementation
 * (such as Apache Aries JMX) enabled.
 *
 * @author thomas.diesler@jboss.com
 * @author sbunciak@redhat.com
 * @author mbasovni@redhat.com
 */
public class KarafRemoteDeployableContainer<T extends KarafRemoteContainerConfiguration> extends
        JMXDeployableContainer<T> {

    //private KarafRemoteContainerConfiguration config;

    static final Logger logger = LoggerFactory.getLogger(KarafRemoteDeployableContainer.class.getPackage().getName());

    @Override
    public Class<T> getConfigurationClass() {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) KarafRemoteContainerConfiguration.class;
        return clazz;
    }

    @Override
    public void setup(T config) {
        super.setup(config);
        //this.config = config;
    }

    @Override
    public void start() throws LifecycleException {
        // In the case of remote container adapters, this is ideally the place
        // to verify if the container is running, along with any other necessary
        // validations.

        MBeanServerConnection mbeanServer = null;

        // Try to connect to an already running server
        try {
            mbeanServer = getMBeanServerConnection(30, TimeUnit.SECONDS);
            mbeanServerInstance.set(mbeanServer);
        } catch (TimeoutException e) {
            throw new LifecycleException("Error connecting to Karaf MBeanServer: ", e);
        }

        try {
            // Get the FrameworkMBean
            ObjectName oname = new ObjectName("osgi.core:type=framework,*");
            frameworkMBean = getMBeanProxy(mbeanServer, oname, FrameworkMBean.class, 30, TimeUnit.SECONDS);

            // Get the BundleStateMBean
            oname = new ObjectName("osgi.core:type=bundleState,*");
            bundleStateMBean = getMBeanProxy(mbeanServer, oname, BundleStateMBean.class, 30, TimeUnit.SECONDS);

            // Get the ServiceStateMBean
            oname = new ObjectName("osgi.core:type=serviceState,*");
            serviceStateMBean = getMBeanProxy(mbeanServer, oname, ServiceStateMBean.class, 30, TimeUnit.SECONDS);

            // Await bootsrap complete services
            awaitBootstrapCompleteServices();

        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new LifecycleException("Cannot start Karaf container", ex);
        }
    }

    @Override
    public void stop() throws LifecycleException {
        super.stop();
    }
}
