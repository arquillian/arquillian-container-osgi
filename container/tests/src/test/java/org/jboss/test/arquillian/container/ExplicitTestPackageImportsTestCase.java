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
package org.jboss.test.arquillian.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.bundle.HelloClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * Test bundle with explicit test package imports
 *
 * @author thomas.diesler@jboss.com
 * @author sbunciak
 * @since 31-Aug-2010
 */
@RunWith(Arquillian.class)
public class ExplicitTestPackageImportsTestCase {

    @Deployment
    public static Archive<?> createDeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "arq139-explicit");
        // This class is automatically added by AbstractOSGiApplicationArchiveProcessor
        //archive.addClass(ExplicitTestPackageImportsTestCase.class);
        archive.addClass(HelloClass.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(HelloClass.class);
                // These packages are added automatically by AbstractOSGiApplicationArchiveProcessor
                // builder.addImportPackages("org.jboss.arquillian.test.api", "org.jboss.arquillian.junit");
                // builder.addImportPackages("org.jboss.shrinkwrap.api", "org.jboss.shrinkwrap.api.asset", "org.jboss.shrinkwrap.api.spec");
                // builder.addImportPackages("org.junit", "org.junit.runner", "org.osgi.framework");
                // Adding new Import Packages
                builder.addImportPackages("org.osgi.service.startlevel", "org.osgi.service.url");
                return builder.openStream();
            }
        });
        
        return archive;
    }

    @ArquillianResource
    Bundle bundle;

    @Test
    public void testBundleInjection() throws Exception {
        
        assertNotNull("Bundle injected", bundle);
        assertEquals("Bundle INSTALLED", Bundle.RESOLVED, bundle.getState());

        bundle.start();
        assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

        // The injected bundle is the one that contains the test case
        assertEquals("arq139-explicit", bundle.getSymbolicName());
        bundle.loadClass(HelloClass.class.getName());

        // The application bundle is installed before the generated test bundle
        BundleContext context = bundle.getBundleContext();
        for (Bundle bundle : context.getBundles()) {
            if (bundle.getSymbolicName().equals(ExplicitTestPackageImportsTestCase.class.getSimpleName()))
                fail("Unexpected generated bundle: " + bundle);
        }
        
        bundle.stop();
        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

        bundle.uninstall();
        assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
    }
}
