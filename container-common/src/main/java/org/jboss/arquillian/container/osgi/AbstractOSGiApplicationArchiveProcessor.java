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
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
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

        Class<?> javaClass = testClass.getJavaClass();

        // Check if the application archive already contains the test class
        String path = javaClass.getName().replace('.', '/') + ".class";
        if (appArchive.contains(path) == false)
            ((ClassContainer<?>) appArchive).addClass(javaClass);

        final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
        Attributes attributes = manifest.getMainAttributes();
        for (Entry<Object, Object> entry : attributes.entrySet()) {
            String key = entry.getKey().toString();
            String value = (String) entry.getValue();
            if (key.equals("Manifest-Version"))
                continue;

            if (key.equals(Constants.IMPORT_PACKAGE)) {
                String[] imports = value.split(",");
                builder.addImportPackages(imports);
                continue;
            }

            if (key.equals(Constants.EXPORT_PACKAGE)) {
                String[] exports = value.split(",");
                builder.addExportPackages(exports);
                continue;
            }

            builder.addManifestHeader(key, value);
        }

        // Export the test class package
        builder.addExportPackages(javaClass);

        // Add the imports required by the test class
        addImportsForClass(builder, javaClass);

        // Add common test imports
        builder.addImportPackages("org.jboss.arquillian.container.test.api", "org.jboss.arquillian.junit", "org.jboss.arquillian.osgi", "org.jboss.arquillian.test.api");
        builder.addImportPackages("org.jboss.shrinkwrap.api", "org.jboss.shrinkwrap.api.asset", "org.jboss.shrinkwrap.api.spec");
        builder.addImportPackages("org.junit", "org.junit.runner", "javax.inject", "org.osgi.framework");

        // Add or replace the manifest in the archive
        appArchive.delete(ArchivePaths.create(JarFile.MANIFEST_NAME));
        appArchive.add(new Asset() {
            public InputStream openStream() {
                return builder.openStream();
            }
        }, JarFile.MANIFEST_NAME);
    }

    private void addImportsForClass(OSGiManifestBuilder builder, Class<?> javaClass) {
        // Interfaces
        for (Class<?> interf : javaClass.getInterfaces()) {
            addImportPackage(builder, interf);
        }
        // Annotations
        for (Annotation anno : javaClass.getDeclaredAnnotations()) {
            addImportPackage(builder, anno.annotationType());
        }
        // Declared fields
        for (Field field : javaClass.getDeclaredFields()) {
            Class<?> type = field.getType();
            addImportPackage(builder, type);
        }
        // Declared methods
        for (Method method : javaClass.getDeclaredMethods()) {
            Class<?> returnType = method.getReturnType();
            if (returnType != Void.TYPE)
                addImportPackage(builder, returnType);
            for (Class<?> paramType : method.getParameterTypes())
                addImportPackage(builder, paramType);
        }
        // Declared classes
        for (Class<?> declaredClass : javaClass.getDeclaredClasses()) {
            addImportsForClass(builder, declaredClass);
        }
    }

    private void addImportPackage(OSGiManifestBuilder builder, Class<?> type) {
        if (type.isArray())
            type = type.getComponentType();

        if (type.isPrimitive() == false && type.getName().startsWith("java.") == false)
            builder.addImportPackages(type);

        for (Annotation anno : type.getDeclaredAnnotations()) {
            Class<?> anType = anno.annotationType();
            if (anType.getName().startsWith("java.") == false)
                builder.addImportPackages(anType);
        }
    }

    private void assertValidBundleArchive(Archive<?> archive) {
        try {
            Manifest manifest = getBundleManifest(archive);
            BundleInfo.validateBundleManifest(manifest);
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

}
