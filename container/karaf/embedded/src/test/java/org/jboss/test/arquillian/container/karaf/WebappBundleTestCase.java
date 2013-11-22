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
package org.jboss.test.arquillian.container.karaf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * Test the embedded OSGi framework
 *
 * @author thomas.diesler@jboss.com
 */
@RunWith(Arquillian.class)
public class WebappBundleTestCase {

    @ArquillianResource
    BundleContext context;

    @Deployment
    public static WebArchive createdeployment() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, "test.war");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(Bundle.class);
                builder.addBundleClasspath("WEB-INF/classes");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testBundleContextInjection() throws Exception {
        assertNotNull("BundleContext injected", context);
        assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
    }

    @Test
    public void testBundleInjection(@ArquillianResource Bundle bundle) throws Exception {
        // Assert that the bundle is injected
        assertNotNull("Bundle injected", bundle);

        // Assert that the bundle is in state RESOLVED
        // Note when the test bundle contains the test case it
        // must be resolved already when this test method is called
        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

        // Start the bundle
        bundle.start();
        assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

        // Assert the bundle context
        BundleContext context = bundle.getBundleContext();
        assertNotNull("BundleContext available", context);

        // Stop the bundle
        bundle.stop();
        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
    }
}
