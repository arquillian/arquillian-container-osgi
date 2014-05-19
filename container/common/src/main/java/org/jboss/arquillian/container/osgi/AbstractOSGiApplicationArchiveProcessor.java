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

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.container.ClassContainer;
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

        //System.out.println(appArchive.toString(true));

        // Get the test class and its super classes
        Class<?> javaClass = testClass.getJavaClass();
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(javaClass);
        Class<?> superclass = javaClass.getSuperclass();
        while (superclass != Object.class) {
            classes.add(superclass);
            superclass = superclass.getSuperclass();
        }

        // Check if the application archive already contains the test classes
        if (!appArchive.getName().endsWith(".war")) {
            for (Class<?> clazz : classes) {
                boolean testClassFound = false;
                String path = clazz.getName().replace('.', '/') + ".class";
                for (ArchivePath auxpath : appArchive.getContent().keySet()) {
                    if (auxpath.toString().endsWith(path)) {
                        testClassFound = true;
                        break;
                    }
                }
                if (testClassFound == false) {
                    ((ClassContainer<?>) appArchive).addClass(clazz);
                }
            }
        }

        final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
        Attributes attributes = manifest.getMainAttributes();
        for (Entry<Object, Object> entry : attributes.entrySet()) {
            String key = entry.getKey().toString();
            String value = (String) entry.getValue();
            if (key.equals("Manifest-Version"))
                continue;

            if (key.equals(Constants.IMPORT_PACKAGE)) {
                String[] imports = splitWithComma(value);
                builder.addImportPackages(imports);
                continue;
            }

            if (key.equals(Constants.EXPORT_PACKAGE)) {
                String[] exports = splitWithComma(value);
                builder.addExportPackages(exports);
                continue;
            }

            builder.addManifestHeader(key, value);
        }

        // Export the test class package otherwise the arq-bundle cannot load the test class
        builder.addExportPackages(javaClass);

        // Add common test imports
        builder.addImportPackages("org.jboss.arquillian.container.test.api", "org.jboss.arquillian.junit", "org.jboss.arquillian.osgi", "org.jboss.arquillian.test.api");
        builder.addImportPackages("org.jboss.shrinkwrap.api", "org.jboss.shrinkwrap.api.asset", "org.jboss.shrinkwrap.api.spec");
        builder.addImportPackages("org.junit", "org.junit.runner", "org.osgi.framework");

        // Add or replace the manifest in the archive
        appArchive.delete(ArchivePaths.create(JarFile.MANIFEST_NAME));
        appArchive.add(new Asset() {
            public InputStream openStream() {
                return builder.openStream();
            }
        }, JarFile.MANIFEST_NAME);
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
}
