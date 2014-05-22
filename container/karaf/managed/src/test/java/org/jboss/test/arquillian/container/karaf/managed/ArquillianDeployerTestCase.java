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
package org.jboss.test.arquillian.container.karaf.managed;

import java.io.InputStream;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * Test for the {@link Deployer}
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Sep-2010
 */
@RunWith(Arquillian.class)
public class ArquillianDeployerTestCase {

    private static final String GOOD_BUNDLE = "good-bundle.jar";
    private static final String BAD_BUNDLE = "bad-bundle.jar";

    @Deployment
    public static JavaArchive create() {
        return ShrinkWrap.create(JavaArchive.class, "deployer-tests.jar");
    }

    @ArquillianResource
    Deployer deployer;
    
    @ArquillianResource
    BundleContext context;

    @Test
    public void testInstallBundleFromArchive() throws Exception {
        InputStream input = deployer.getDeployment(GOOD_BUNDLE);
        Bundle bundle = context.installBundle(GOOD_BUNDLE, input);
        try {
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
            Assert.assertEquals(GOOD_BUNDLE, bundle.getSymbolicName());

            bundle.start();
            Assert.assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

            bundle.stop();
            Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
        } finally {
            bundle.uninstall();
            Assert.assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testDeployBundle() throws Exception {
        deployer.deploy(GOOD_BUNDLE);
        Bundle bundle = null;
        try {
            for (Bundle aux : context.getBundles()) {
                if (GOOD_BUNDLE.equals(aux.getSymbolicName())) {
                    bundle = aux;
                    break;
                }
            }
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());

            bundle.start();
            Assert.assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

            bundle.stop();
            Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());
        } finally {
            deployer.undeploy(GOOD_BUNDLE);
            Assert.assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testDeployBadBundle() throws Exception {
        deployer.deploy(BAD_BUNDLE);
        Bundle bundle = null;
        try {
            for (Bundle aux : context.getBundles()) {
                if (BAD_BUNDLE.equals(aux.getSymbolicName())) {
                    bundle = aux;
                    break;
                }
            }
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());

            try {
                bundle.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
        } finally {
            deployer.undeploy(BAD_BUNDLE);
            Assert.assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Deployment(name = GOOD_BUNDLE, managed = false, testable = false)
    public static JavaArchive getGoodBundle() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(GOOD_BUNDLE);
                builder.addBundleManifestVersion(2);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BAD_BUNDLE, managed = false, testable = false)
    public static JavaArchive getBadBundle() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(BAD_BUNDLE);
                builder.addBundleManifestVersion(2);
                builder.addImportPackages("org.acme.foo");
                return builder.openStream();
            }
        });
        return archive;
    }
}
