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

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.osgi.StartLevelAware;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.startlevel.BundleStartLevel;

/**
 * The OSGi TestEnricher
 *
 * The enricher supports start level aware bundle deployments.
 *
 * <pre>
 * <code>
    @Deployment
    @StartLevelAware(startLevel = 3)
    public static JavaArchive create() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "start-level-bundle");
        ...
    }
 * </code>
 * </pre>
 *
 * @author thomas.diesler@jboss.com
 */
public class OSGiTestEnricher implements TestEnricher {

    // Provide logging
    private static final Logger log = Logger.getLogger(OSGiTestEnricher.class.getName());

    @Override
    public void enrich(Object testCase) {

        // Process {@link StartLevelAware} on the {@link Deployment}
        Class<? extends Object> testClass = testCase.getClass();
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Deployment.class)) {
                Deployment andep = method.getAnnotation(Deployment.class);
                if (andep.managed() && andep.testable() && method.isAnnotationPresent(StartLevelAware.class)) {
                    StartLevelAware startLevelAware = method.getAnnotation(StartLevelAware.class);
                    Bundle bundle = getBundle(testCase);
                    if (bundle != null) {
                        int bundleStartLevel = startLevelAware.startLevel();
                        log.fine("Setting bundle start level of " + bundle + " to: " + bundleStartLevel);
                        BundleStartLevel startLevel = bundle.adapt(BundleStartLevel.class);
                        startLevel.setStartLevel(bundleStartLevel);
                        if (startLevelAware.autostart()) {
                            try {
                                bundle.start();
                            } catch (BundleException ex) {
                                log.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public Object[] resolve(Method method) {
        return null;
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
