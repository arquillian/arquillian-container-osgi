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
package org.jboss.test.arquillian.container.felix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.jboss.arquillian.container.osgi.felix.FelixLogger;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.felix.sub.A;
import org.jboss.test.arquillian.container.felix.sub.B;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.launch.Framework;

/**
 * The arquillian-osgi-bundle loads test cases dynamically from the test archive.
 * When the test archive gets undeployed the wiring to the test case may is still active.
 *
 * A subsequent class load of another test case in the same package would use the
 * wiring of the first test archive and result in ClassNotFoundException
 *
 * @author thomas.diesler@jboss.com
 */
public class DynamicImportPackageTestCase {

    BundleContext syscontext;

    @Before
    public void setUp() throws Exception {

        // Read the Framework properties
        URL url = getClass().getResource("/framework.properties");
        Properties props = new Properties();
        props.load(url.openStream());
        HashMap<String, Object> map = new HashMap<String, Object> ();
        for (Entry<Object, Object> entry :  props.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue().toString());
        }
        map.put(FelixConstants.LOG_LOGGER_PROP, new FelixLogger());

        // Create the framework instance
        Framework framework = new Felix(map);
        framework.init();
        framework.start();

        syscontext = framework.getBundleContext();
    }

    @Test @Ignore
    public void testBundleContextInjection() throws Exception {

        // The loader bundle has Dynamic-ImportPackage: *
        Bundle loader = installBundle(getLoaderBundle());

        // Contains ...sub.A
        Bundle bundleA = installBundle(getBundleA());

        Object objA = loader.loadClass(A.class.getName()).newInstance();
        Assert.assertNotNull("A not null", objA);
        Bundle fromA = ((BundleReference) objA.getClass().getClassLoader()).getBundle();
        Assert.assertSame(bundleA, fromA);

        refreshBundle(bundleA);

        bundleA.uninstall();

        // Contains ...sub.B
        Bundle bundleB = installBundle(getBundleB());

        Object objB = loader.loadClass(B.class.getName()).newInstance();
        Assert.assertNotNull("B not null", objB);
        Bundle fromB = ((BundleReference) objB.getClass().getClassLoader()).getBundle();
        Assert.assertSame(bundleB, fromB);
    }

    private void refreshBundle(Bundle bundle) throws TimeoutException {
        throw new UnsupportedOperationException("Bundle refreshing is not implemented");
//        final CountDownLatch latch = new CountDownLatch(1);
//        FrameworkListener listener = new FrameworkListener() {
//            @Override
//            public void frameworkEvent(FrameworkEvent event) {
//                if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
//                    latch.countDown();
//                }
//            }
//        };
//
//        FrameworkWiring fwWiring = syscontext.getBundle().adapt(FrameworkWiring.class);
//        fwWiring.refreshBundles(Collections.singleton(bundle), listener);
//
//        // Wait for the refresh to complete
//        try {
//            if (!latch.await(10, TimeUnit.SECONDS)) {
//                throw new TimeoutException();
//            }
//        } catch (InterruptedException ex) {
//            // ignore
//        }
    }

    private Bundle installBundle(JavaArchive archive) throws BundleException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.exportTo(baos);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
        return syscontext.installBundle(archive.getName(), inputStream);
    }

    private JavaArchive getLoaderBundle() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "loader-bundle.jar");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA.jar");
        archive.addClasses(A.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(A.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB.jar");
        archive.addClasses(B.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(B.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
