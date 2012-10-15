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
package org.jboss.arquillian.testenricher.osgi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.osgi.StartLevelAware;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

/**
 * The OSGi TestEnricher
 *
 * The enricher supports the injection of the system BundleContext and the test Bundle.
 *
 * <pre>
 * <code>
 *    @Inject
 *    BundleContext context;
 *
 *    @Inject
 *    Bundle bundle;
 *
 *    @Inject
 *    StartLevel startLevel;
 *
 *    @Inject
 *    PackageAdmin packageAdmin;
 * </code>
 * </pre>
 *
 * @author thomas.diesler@jboss.com
 */
public class OSGiTestEnricher implements TestEnricher {

    // Provide logging
    private static final Logger log = Logger.getLogger(OSGiTestEnricher.class.getName());

    public void enrich(Object testCase) {

        BundleContext bundleContext = BundleContextProvider.getBundleContext();
        if (bundleContext == null) {
            log.fine("System bundle context not available");
            return;
        }

        Class<? extends Object> testClass = testCase.getClass();
        for (Field field : testClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                if (field.getType().isAssignableFrom(BundleContext.class)) {
                    injectBundleContext(testCase, field);
                } else if (field.getType().isAssignableFrom(Bundle.class)) {
                    injectBundle(testCase, field);
                } else if (field.getType().isAssignableFrom(PackageAdmin.class)) {
                    injectPackageAdmin(testCase, field);
                } else if (field.getType().isAssignableFrom(StartLevel.class)) {
                    injectStartLevel(testCase, field);
                }
            }
        }
        // Process {@link StartLevelAware} on the {@link Deployment}
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Deployment.class)) {
                Deployment andep = method.getAnnotation(Deployment.class);
                if (andep.managed() && andep.testable() && method.isAnnotationPresent(StartLevelAware.class)) {
                    int bundleStartLevel = method.getAnnotation(StartLevelAware.class).startLevel();
                    StartLevel startLevel = getStartLevel();
                    Bundle bundle = getBundle(testCase);
                    log.fine("Setting bundle start level of " + bundle + " to: " + bundleStartLevel);
                    startLevel.setBundleStartLevel(bundle, bundleStartLevel);
                }
            }
        }
    }

    public Object[] resolve(Method method) {
        return null;
    }

    private void injectBundleContext(Object testCase, Field field) {
        try {
            BundleContext context = BundleContextProvider.getBundleContext();
            log.warning("Deprecated @Inject BundleContext, use @ArquillianResource BundleContext");
            field.set(testCase, context);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot inject BundleContext", ex);
        }
    }

    private void injectBundle(Object testCase, Field field) {
        try {
            Bundle bundle = getBundle(testCase);
            log.warning("Deprecated @Inject Bundle, use @ArquillianResource Bundle");
            field.set(testCase, bundle);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot inject Bundle", ex);
        }
    }

    private void injectPackageAdmin(Object testCase, Field field) {
        try {
            PackageAdmin packageAdmin = getPackageAdmin();
            log.warning("Deprecated @Inject PackageAdmin, use @ArquillianResource PackageAdmin");
            field.set(testCase, packageAdmin);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot inject PackageAdmin", ex);
        }
    }

    private void injectStartLevel(Object testCase, Field field) {
        try {
            StartLevel startLevel = getStartLevel();
            log.warning("Deprecated @Inject StartLevel, use @ArquillianResource StartLevel");
            field.set(testCase, startLevel);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot inject StartLevel", ex);
        }
    }

    private PackageAdmin getPackageAdmin() {
        BundleContext context = BundleContextProvider.getBundleContext();
        ServiceReference sref = context.getServiceReference(PackageAdmin.class.getName());
        PackageAdmin packageAdmin = (PackageAdmin) context.getService(sref);
        return packageAdmin;
    }

    private StartLevel getStartLevel() {
        BundleContext context = BundleContextProvider.getBundleContext();
        ServiceReference sref = context.getServiceReference(StartLevel.class.getName());
        StartLevel startLevel = (StartLevel) context.getService(sref);
        return startLevel;
    }

    private Bundle getBundle(Object testCase) {
        // [ARQ-459] Allow TestRunner to TestEnricher communication
        Bundle bundle = BundleAssociation.getBundle();
        if (bundle == null) {
            ClassLoader classLoader = testCase.getClass().getClassLoader();
            if (classLoader instanceof BundleReference) {
                BundleReference bref = (BundleReference) classLoader;
                bundle = bref.getBundle();
            }
        }
        return bundle;
    }
}
