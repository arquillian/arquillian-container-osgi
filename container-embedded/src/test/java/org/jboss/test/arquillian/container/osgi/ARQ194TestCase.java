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

import java.io.InputStream;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.osgi.bundle.ARQ194Activator;
import org.jboss.test.arquillian.container.osgi.bundle.ARQ194Service;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * [ARQ-194] Support multiple bundle deployments
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Sep-2010
 */
@RunWith(Arquillian.class)
public class ARQ194TestCase {

    private static final String BUNDLE_NAME = "arq194-bundle";

    @Deployment
    public static JavaArchive create() {
        return ShrinkWrap.create(JavaArchive.class, "arq194-test");
    }

    @Inject
    public BundleContext context;

    @ArquillianResource
    public Deployer deployer;

    @Test
    public void testInstallBundleFromArchive() throws Exception {
        InputStream input = deployer.getDeployment(BUNDLE_NAME);
        Bundle bundle = context.installBundle(BUNDLE_NAME, input);

        assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
        assertEquals("arq194-bundle", bundle.getSymbolicName());

        bundle.start();
        assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

        bundle.stop();
        assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

        bundle.uninstall();
        assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
    }

    @Deployment(name = BUNDLE_NAME, managed = false, testable = false)
    public static JavaArchive getTestArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(BUNDLE_NAME);
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(ARQ194Activator.class.getName());
                builder.addExportPackages(ARQ194Service.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        archive.addClasses(ARQ194Activator.class, ARQ194Service.class);
        return archive;
    }
}
