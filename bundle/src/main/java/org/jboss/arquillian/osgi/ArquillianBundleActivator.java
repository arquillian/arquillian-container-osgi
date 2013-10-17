/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.osgi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner.TestClassLoader;
import org.jboss.arquillian.testenricher.osgi.BundleAssociation;
import org.jboss.arquillian.testenricher.osgi.BundleContextAssociation;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

/**
 * This is the Arquillian {@link BundleActivator}.
 *
 * It unconditionally starts the {@link JMXTestRunner}.
 *
 * @author thomas.diesler@jboss.com
 * @since 17-May-2009
 */
public class ArquillianBundleActivator implements BundleActivator {
    // Provide logging
    private static Logger log = Logger.getLogger(ArquillianBundleActivator.class.getName());

    private JMXTestRunner testRunner;
    private long arqBundleId;

    public void start(final BundleContext context) throws Exception {

        arqBundleId = context.getBundle().getBundleId();

        final BundleContext syscontext = context.getBundle(0).getBundleContext();
        final TestClassLoader testClassLoader = new TestClassLoader() {

            @Override
            public Class<?> loadTestClass(String className) throws ClassNotFoundException {
                String namePath = className.replace('.', '/') + ".class";

                // Get all installed bundles and remove some
                List<Bundle> bundles = new ArrayList<Bundle>(Arrays.asList(syscontext.getBundles()));
                Iterator<Bundle> iterator = bundles.iterator();
                while(iterator.hasNext()) {
                    Bundle aux = iterator.next();
                    if (aux.getBundleId() <= arqBundleId || aux.getState() == Bundle.UNINSTALLED) {
                        iterator.remove();
                    }
                }

                // Load the the test class from the bundle that contains the entry
                for (Bundle aux : bundles) {
                    if (aux.getEntry(namePath) != null) {
                        return aux.loadClass(className);
                    }
                }

                throw new ClassNotFoundException("Test '" + className + "' not found in: " + bundles);
            }
        };

        // Register the JMXTestRunner
        MBeanServer mbeanServer = findOrCreateMBeanServer();
        testRunner = new JMXTestRunner(testClassLoader) {
            @Override
            public byte[] runTestMethod(String className, String methodName) {
                Class<?> testClass;
                try {
                    testClass = testClassLoader.loadTestClass(className);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException(ex);
                }
                BundleAssociation.setBundle(getTestBundle(syscontext, testClass, methodName));
                BundleContextAssociation.setBundleContext(syscontext);
                return super.runTestMethod(className, methodName);
            }
        };
        testRunner.registerMBean(mbeanServer);
    }

    public void stop(BundleContext context) throws Exception {
        // Unregister the JMXTestRunner
        MBeanServer mbeanServer = findOrCreateMBeanServer();
        testRunner.unregisterMBean(mbeanServer);
    }

    private MBeanServer findOrCreateMBeanServer() {
        MBeanServer mbeanServer = null;

        ArrayList<MBeanServer> serverArr = MBeanServerFactory.findMBeanServer(null);
        if (serverArr.size() > 1)
            log.warning("Multiple MBeanServer instances: " + serverArr);

        if (serverArr.size() > 0) {
            mbeanServer = serverArr.get(0);
            log.fine("Found MBeanServer: " + mbeanServer.getDefaultDomain());
        }

        if (mbeanServer == null) {
            log.fine("No MBeanServer, create one ...");
            mbeanServer = MBeanServerFactory.createMBeanServer();
        }

        return mbeanServer;
    }

    private Bundle getTestBundle(BundleContext syscontext, Class<?> testClass, String methodName) {
        Bundle bundle = ((BundleReference) testClass.getClassLoader()).getBundle();
        for (Method method : testClass.getMethods()) {
            OperateOnDeployment opon = method.getAnnotation(OperateOnDeployment.class);
            if (opon != null && methodName.equals(method.getName())) {
                for (Bundle aux : syscontext.getBundles()) {
                    if (aux.getLocation().equals(opon.value())) {
                        bundle = aux;
                        break;
                    }
                }
            }
        }
        return bundle;
    }
}