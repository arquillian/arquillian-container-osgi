/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.arquillian.osgi.bundle;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.ArquillianBundleActivator;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import org.osgi.framework.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * ArquillianBundleGenerator
 *
 * @author <a href="mailto:cristina.gonzalez@liferay.com">Cristina González Castellano</a>
 */
public class ArquillianBundleGenerator {

    private static final List<String> exportPackages = Arrays.asList(
            "org.jboss.arquillian.container.test.api",
            "org.jboss.arquillian.junit",
            "org.jboss.arquillian.osgi",
            "org.jboss.arquillian.test.api",
            "org.jboss.shrinkwrap.api",
            "org.jboss.shrinkwrap.api.asset",
            "org.jboss.shrinkwrap.api.spec",
            "junit.framework",
            "org.hamcrest",
            "org.hamcrest.core",
            "org.junit",
            "org.junit.matchers",
            "org.junit.rules",
            "org.junit.runner",
            "org.junit.runner.manipulation",
            "org.junit.runner.notification",
            "org.junit.runners",
            "org.junit.runners.model",
            "org.junit.runners.parameterized",
            "org.junit.validator",
            "org.osgi.framework",
            "org.jboss.osgi.metadata");

    public Archive<?> createArquillianBundle()
        throws Exception{

        JavaArchive arquillianOSGiBundleArchive = ShrinkWrap.create(
            JavaArchive.class, BUNDLE_SYMBOLIC_NAME + ".jar");

        arquillianOSGiBundleArchive.addClass(ArquillianBundleActivator.class);

        arquillianOSGiBundleArchive.addPackage(JMXTestRunner.class.getPackage());

        Properties properties = new Properties();

        properties.setProperty(Constants.BUNDLE_SYMBOLICNAME, BUNDLE_SYMBOLIC_NAME);
        properties.setProperty(Constants.BUNDLE_NAME, BUNDLE_NAME);
        properties.setProperty(Constants.BUNDLE_VERSION, BUNDLE_VERSION);
        properties.setProperty(Constants.BUNDLE_ACTIVATOR, ArquillianBundleActivator.class.getCanonicalName());
        properties.setProperty(Constants.IMPORT_PACKAGE, "*;resolution:=optional");

        properties.setProperty(Constants.EXPORT_PACKAGE, exportPackages.stream().collect(Collectors.joining(",")));

        List<Archive<?>> extensionArchives = loadAuxiliaryArchives();

        properties.setProperty(Constants.BUNDLE_CLASSPATH, getBundleClassPath(arquillianOSGiBundleArchive, extensionArchives));

        BundleGeneratorHelper.generateManifest(
            arquillianOSGiBundleArchive, properties);

        return arquillianOSGiBundleArchive;
    }

    private String getBundleClassPath(
        JavaArchive javaArchive, Collection<Archive<?>> auxiliaryArchives)
        throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append(".");

        for (Archive auxiliaryArchive : auxiliaryArchives) {
            Map<ArchivePath, Node> remoteLoadableExtensionMap =
                auxiliaryArchive.getContent(
                    Filters.include(_REMOTE_LOADABLE_EXTENSION_FILE));

            Collection<Node> remoteLoadableExtensions =
                remoteLoadableExtensionMap.values();

            if (remoteLoadableExtensions.size() > 1) {
                throw new RuntimeException(
                    "The archive " + auxiliaryArchive.getName() +
                        " contains more than one RemoteLoadableExtension file");
            }

            if (remoteLoadableExtensions.size() == 1) {
                Iterator<Node> remoteLoadableExtensionsIterator =
                    remoteLoadableExtensions.iterator();

                Node remoteLoadableExtensionsNext =
                    remoteLoadableExtensionsIterator.next();

                javaArchive.add(
                    remoteLoadableExtensionsNext.getAsset(),
                    _REMOTE_LOADABLE_EXTENSION_FILE);
            }

            InputStream auxiliaryArchiveInputStream = auxiliaryArchive.as(ZipExporter.class).exportAsInputStream();

            ByteArrayAsset byteArrayAsset = new ByteArrayAsset(auxiliaryArchiveInputStream);

            String path = "extension/" + auxiliaryArchive.getName();

            javaArchive.addAsResource(byteArrayAsset, path);

            sb.append(",");
            sb.append(path);
        }

        return sb.toString();
    }

    private List<Archive<?>> loadAuxiliaryArchives() {
        List<Archive<?>> archives = new ArrayList<Archive<?>>();

        // load based on the Containers ClassLoader
        ServiceLoader serviceLoader = _serviceLoaderInstance.get();

        Collection<AuxiliaryArchiveAppender> archiveAppenders = serviceLoader.all(AuxiliaryArchiveAppender.class);

        for (AuxiliaryArchiveAppender archiveAppender : archiveAppenders) {
            Archive<?> auxiliaryArchive = archiveAppender.createAuxiliaryArchive();

            if (auxiliaryArchive != null) {
                archives.add(auxiliaryArchive);
            }
        }

        return archives;
    }

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

    private static final String _REMOTE_LOADABLE_EXTENSION_FILE = "/META-INF/services/" + RemoteLoadableExtension.class.getCanonicalName();

    public static final String BUNDLE_SYMBOLIC_NAME = "arquillian-osgi-bundle";
    public static final String BUNDLE_NAME = "Arquillian Bundle";
    public static final String BUNDLE_VERSION = "1.0.0";

}

