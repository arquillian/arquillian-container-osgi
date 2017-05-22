/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.container.osgi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.bundle.ArquillianFragmentGenerator;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.container.ClassContainer;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.osgi.framework.Constants;

/**
 * Generates the test bundle.
 *
 * @author Thomas.Diesler@jboss.com
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractOSGiApplicationArchiveProcessor implements ApplicationArchiveProcessor {

    @Override
    public void process(Archive<?> appArchive, TestClass testClass) {
        Manifest manifest = getBundleManifest(appArchive);
        if (manifest == null) {
            manifest = createBundleManifest(appArchive.getName());
        }
        if (manifest != null) {
            enhanceApplicationArchive(appArchive, testClass, manifest);
            assertValidBundleArchive(appArchive);
        }
    }

    protected abstract Manifest createBundleManifest(String symbolicName);

    private void enhanceApplicationArchive(Archive<?> appArchive, TestClass testClass, Manifest manifest) {

        if (ClassContainer.class.isAssignableFrom(appArchive.getClass()) == false)
            throw new IllegalArgumentException("ClassContainer expected: " + appArchive);

        ServiceLoader serviceLoader = _serviceLoaderInstance.get();

        ArquillianFragmentGenerator arquillianFragmentGenerator = serviceLoader.onlyOne(ArquillianFragmentGenerator.class);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            manifest.write(baos);

            ByteArrayAsset byteArrayAsset = new ByteArrayAsset(baos.toByteArray());

            appArchive.delete(MANIFEST_PATH);

            appArchive.add(byteArrayAsset, MANIFEST_PATH);

            Archive<?> arquillianFragment = arquillianFragmentGenerator.createArquillianFragment(manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME), manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION), testClass);

            ZipExporter arquillianBundleArchiveZipExporter = arquillianFragment.as(ZipExporter.class);

            InputStream arquillianBundleArchiveInputStream = arquillianBundleArchiveZipExporter.exportAsInputStream();

            byteArrayAsset = new ByteArrayAsset(
                arquillianBundleArchiveInputStream);

            appArchive.add(byteArrayAsset, FRAGMENT_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Can't create the Arquillian Fragment", e);
        }
    }

    private void assertValidBundleArchive(Archive<?> archive) {
        try {
            Manifest manifest = getBundleManifest(archive);
            OSGiManifestBuilder.validateBundleManifest(manifest);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Not a valid OSGi bundle: " + archive, ex);
        }
    }

    private Manifest getBundleManifest(Archive<?> archive) {
        try {
            Node node = archive.get(JarFile.MANIFEST_NAME);
            if (node == null)
                return null;

            Manifest manifest = new Manifest(node.getAsset().openStream());
            return manifest;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String[] splitWithComma(String value) {
        // Header clauses are split with comma but comma can also appear in version parameter or in a custom parameter for "Attribute Matching"
        // e.g. Import-Package: org.jboss.arquillian.junit;version="[X.0.0,Y.0.0)";extra="A,B",...

        // After each comma must be even number of double-quotes
        return value.split(
            "(?x)       " +   // Free-Spacing Mode
            ",          " +   // Split with comma
            "(?=        " +   // Followed by
            "  (?:      " +   // Start a non-capture group
            "    [^\"]* " +   // 0 or more non-quote characters
            "    \"     " +   // 1 quote
            "    [^\"]* " +   // 0 or more non-quote characters
            "    \"     " +   // 1 quote
            "  )*       " +   // 0 or more repetition of non-capture group (multiple of 2 quotes will be even)
            "  [^\"]*   " +   // Finally 0 or more non-quotes
            "  $        " +   // Till the end  (This is necessary, else every comma will satisfy the condition)
            ")          "     // End look-ahead
        );
    }


    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

    public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    public static final String FRAGMENT_PATH = "deployment/fragment.jar";
}
