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
package org.jboss.test.arquillian.container.osgi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * [ARQ-271] TestEnricher should be called in @BeforeClass
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Apr-2011
 */
@RunWith(Arquillian.class)
public class ARQ271BeforeTestCase {
    @Inject
    public BundleContext context;

    @Inject
    public Bundle bundle;

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "test.jar");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(OSGiTestHelper.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Before
    public void before() throws Exception {
        assertNotNull("BundleContext injected", context);
        assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
        assertNotNull("Bundle injected", bundle);
        OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
    }

    @After
    public void after() throws Exception {
        assertNotNull("BundleContext injected", context);
        assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
        assertNotNull("Bundle injected", bundle);
        OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
    }

    @Test
    public void testBundleInjection() throws Exception {
        assertNotNull("BundleContext injected", context);
        assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
        assertNotNull("Bundle injected", bundle);
        OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
    }
}
