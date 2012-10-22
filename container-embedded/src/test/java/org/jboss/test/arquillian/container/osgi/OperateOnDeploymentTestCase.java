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

import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.osgi.bundle.SimpleService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test multiple bundle deployments
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Oct-2012
 */
@RunWith(Arquillian.class)
public class OperateOnDeploymentTestCase {

    static final String BUNDLE_A = "bundle-a";
    static final String BUNDLE_B = "bundle-b";

    @Deployment
    public static Archive<?> deployment() {
        // The default deployment is needed if we don't want to @RunAsClient
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "multiple-tests");
        archive.addClasses(SimpleService.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PackageAdmin.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_A)
    public static Archive<?> deploymentA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_A);
        archive.addClasses(SimpleService.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(SimpleService.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_B)
    public static Archive<?> deploymentB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_B);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(SimpleService.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    @OperateOnDeployment(BUNDLE_A)
    public void testBundleA(@ArquillianResource Bundle bundle) throws Exception {
        Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
        Assert.assertEquals(BUNDLE_A, bundle.getLocation());
        Assert.assertEquals(BUNDLE_A, bundle.getSymbolicName());
    }

    @Test
    @OperateOnDeployment(BUNDLE_B)
    public void testBundleB(@ArquillianResource Bundle bundle) throws Exception {
        Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
        Assert.assertEquals(BUNDLE_B, bundle.getLocation());
        Assert.assertEquals(BUNDLE_B, bundle.getSymbolicName());
    }
}
