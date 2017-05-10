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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.osgi.bundle.ArquillianFragmentGenerator;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner.TestClassLoader;
import org.jboss.arquillian.testenricher.osgi.BundleAssociation;
import org.jboss.arquillian.testenricher.osgi.BundleContextAssociation;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

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

    public void start(final BundleContext context) throws Exception {

        final BundleContext syscontext = context.getBundle(0).getBundleContext();
        final TestClassLoader testClassLoader = new TestClassLoader() {

            @Override
            public Class<?> loadTestClass(String className) throws ClassNotFoundException {
                // Load the the test class from the bundle that contains the entry
                return context.getBundle().loadClass(className);
            }
        };

        // Register the JMXTestRunner
        MBeanServer mbeanServer = findOrCreateMBeanServer();
        testRunner = new JMXTestRunner(testClassLoader) {
            @Override
            public byte[] runTestMethod(String className, String methodName) {
                Thread thread = Thread.currentThread();

                ClassLoader contextClassLoader = thread.getContextClassLoader();

                try {
                    Bundle bundle = context.getBundle();

                    thread.setContextClassLoader(
                        bundle.adapt(BundleWiring.class).getClassLoader());

                    return super.runTestMethod(className, methodName);
                }
                finally {
                    thread.setContextClassLoader(contextClassLoader);
                }
            }

            @Override
            public byte[] runTestMethod(String className, String methodName, Map<String, String> protocolProps) {
                Class<?> testClass;
                try {
                    testClass = testClassLoader.loadTestClass(className);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException(ex);
                }
                Bundle fragmentBundle = getFragmentBundle(context);

                BundleAssociation.setBundle(getTestBundle(syscontext, fragmentBundle.getHeaders().get(ArquillianFragmentGenerator.TEST_BUNDLE_SYMBOLIC_NAME), testClass, methodName));
                BundleContextAssociation.setBundleContext(syscontext);
                return super.runTestMethod(className, methodName, protocolProps);
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
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        return mbeanServer;
    }

    private Bundle getFragmentBundle(BundleContext context) {
        BundleWiring bundleWiring = context.getBundle().adapt(BundleWiring.class );

        List<Bundle> fragmentBundles = new ArrayList<Bundle>();

        if (bundleWiring != null) {
            List<BundleWire> providedWires = bundleWiring.getProvidedWires(HostNamespace.HOST_NAMESPACE);

            for (BundleWire providedWire : providedWires) {
                fragmentBundles.add(providedWire.getRequirerWiring().getRevision().getBundle());
            }
        }

        if (fragmentBundles.isEmpty()) {
            throw new RuntimeException("There are not fragment associated with the context");
        }

        if (fragmentBundles.size() > 1) {
            throw new RuntimeException("There are more than one fragment for the Arquilian Bundle");
        }

        return fragmentBundles.get(0);

    }

    private Bundle getTestBundle(BundleContext syscontext, String testBundleSymbolicName, Class<?> testClass, String methodName) {
        Bundle testBundle = null;

        for (Bundle aux : syscontext.getBundles()) {
            if (aux.getSymbolicName().equals(testBundleSymbolicName)) {
                testBundle = aux;

                break;
            }
        }

        for (Method method : testClass.getMethods()) {
            OperateOnDeployment opon = method.getAnnotation(OperateOnDeployment.class);
            if (opon != null && methodName.equals(method.getName())) {
                for (Bundle aux : syscontext.getBundles()) {
                    if (aux.getLocation().equals(opon.value())) {
                        testBundle = aux;
                        break;
                    }
                }
            }
        }

        return testBundle;
    }
}