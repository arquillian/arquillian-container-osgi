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
package org.jboss.test.arquillian.container.equinox;

import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * Test multiple bundle deployments
 *
 * @author thomas.diesler@jboss.com
 * @author th_janssen@outlook.com
 */
@RunWith(Arquillian.class)
public class MultipleBundleDeploymentsTestCase {

    static final String BUNDLE_A = "bundle-a";
    static final String BUNDLE_B = "bundle-b";

    @ArquillianResource
    BundleContext context;

    @Deployment
    public static Archive<?> deployment() {
        // The default deployment is needed if we don't want to @RunAsClient
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "multiple-tests");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(FrameworkWiring.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_A, testable = false)
    public static Archive<?> deploymentA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_A);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_B, testable = false)
    public static Archive<?> deploymentB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_B);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testBundleDeployments() throws Exception {
        Bundle bundleA = context.getBundle(BUNDLE_A);
        Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundleA.getState());

        Bundle bundleB = context.getBundle(BUNDLE_B);
        Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundleB.getState());
    }
}
