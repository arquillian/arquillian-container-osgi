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
package org.jboss.arquillian.protocol.osgi;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.osgi.framework.Constants;

/**
 * Generates the test bundle.
 *
 * @author Thomas.Diesler@jboss.com
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OSGiApplicationArchiveProcessor implements ApplicationArchiveProcessor {
    public ArchivePath MANIFEST_PATH = ArchivePaths.create("META-INF/MANIFEST.MF");

    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        enhanceApplicationArchive(applicationArchive, testClass);
        assertValidBundleArchive(applicationArchive);
    }

    private void enhanceApplicationArchive(Archive<?> applicationArchive, TestClass testClass) {
        if (JavaArchive.class.isAssignableFrom(applicationArchive.getClass()) == false)
            throw new IllegalArgumentException("JavaArchive expected: " + applicationArchive);

        JavaArchive appArchive = JavaArchive.class.cast(applicationArchive);
        Class<?> javaClass = testClass.getJavaClass();

        // Check if the application archive already contains the test class
        String path = javaClass.getName().replace('.', '/') + ".class";
        if (appArchive.contains(path) == false)
            appArchive.addClass(javaClass);

        final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
        Manifest manifest = getBundleManifest(appArchive);
        if (manifest != null) {
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
        } else {
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(appArchive.getName());
        }

        // Export the test class package
        builder.addExportPackages(javaClass);

        // Add the imports required by the test class
        addImportsForClass(builder, javaClass);

        // Add framework imports
        // [TODO] use bnd or another tool to do this more intelligently
        builder.addImportPackages("org.jboss.arquillian.test.api", "org.jboss.arquillian.junit");
        builder.addImportPackages("org.jboss.shrinkwrap.api", "org.jboss.shrinkwrap.api.asset", "org.jboss.shrinkwrap.api.spec");
        builder.addImportPackages("org.junit", "org.junit.runner", "javax.inject", "org.osgi.framework");

        // SHRINKWRAP-187, to eager on not allowing overrides, delete it first
        appArchive.delete(MANIFEST_PATH);
        // Add the manifest to the archive
        appArchive.setManifest(new Asset() {
            public InputStream openStream() {
                return builder.openStream();
            }
        });
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
            Node node = archive.get(MANIFEST_PATH);
            if (node == null)
                return null;

            Manifest manifest = new Manifest(node.getAsset().openStream());
            return manifest;
        } catch (Exception ex) {
            return null;
        }
    }

}
