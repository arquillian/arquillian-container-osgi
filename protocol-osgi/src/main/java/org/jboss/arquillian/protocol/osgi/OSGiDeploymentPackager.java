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
package org.jboss.arquillian.protocol.osgi;

import java.util.Collection;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;

/**
 * Packager for running Arquillian against OSGi containers.
 *
 * @author thomas.diesler@jboss.com
 * @version $Revision: $
 */
public class OSGiDeploymentPackager implements DeploymentPackager {

    public Archive<?> generateDeployment(TestDeployment testDeployment, Collection<ProtocolArchiveProcessor> processors) {
        Archive<?> bundleArchive = testDeployment.getApplicationArchive();
        return handleArchive(bundleArchive, testDeployment.getAuxiliaryArchives());
    }

    private Archive<?> handleArchive(Archive<?> archive, Collection<Archive<?>> auxiliaryArchives) {
        try {
            validateBundleArchive(archive);
            return archive;
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Not a valid OSGi bundle: " + archive, ex);
        }
    }

    private void validateBundleArchive(Archive<?> archive) throws Exception {
        Manifest manifest = null;
        Node node = archive.get(JarFile.MANIFEST_NAME);
        if (node != null) {
            manifest = new Manifest(node.getAsset().openStream());
        }
        BundleInfo.validateBundleManifest(manifest);
    }
}
