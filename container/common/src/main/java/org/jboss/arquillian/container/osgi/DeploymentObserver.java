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
package org.jboss.arquillian.container.osgi;

import java.util.jar.Manifest;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.AfterStart;
import org.jboss.arquillian.container.spi.event.container.BeforeSetup;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.bundle.ArquillianBundleGenerator;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeploymentObserver
 *
 * @author mbasovni@redhat.com
 */
public class DeploymentObserver {

    static final Logger logger = LoggerFactory.getLogger(DeploymentObserver.class.getPackage().getName());

    public void buildArquillianOSGiBundle(@Observes BeforeSetup event) throws Exception {
        if (_arquillianOSGiBundle == null) {
            ServiceLoader serviceLoader = _serviceLoaderInstance.get();

            ArquillianBundleGenerator arquillianBundleGenerator =
                serviceLoader.onlyOne(ArquillianBundleGenerator.class);

            _arquillianOSGiBundle = arquillianBundleGenerator.createArquillianBundle();
        }
    }

    public void startContainer(@Observes AfterStart event) throws Exception {
        if (event.getDeployableContainer() instanceof CommonDeployableContainer) {
            container = (CommonDeployableContainer<?>) event.getDeployableContainer();
        }
    }

    public void installArquillianBundle(@Observes Before event) throws Exception {
        _arquillianOSGiBundleId = container.installBundle(_arquillianOSGiBundle, true);
    }

    public void uninstallArquillianBundle(@Observes After event) throws Exception {
        container.uninstallBundle(_arquillianOSGiBundleId);

        container.refresh();
    }

    public void autostartBundle(@Observes AfterDeploy event) throws Exception {
        if (event.getDeployableContainer() instanceof CommonDeployableContainer) {
            CommonDeployableContainer<?> container = (CommonDeployableContainer<?>) event.getDeployableContainer();
            if (container.isAutostartBundle()) {
                Manifest manifest = new Manifest(event.getDeployment().getArchive().get("/META-INF/MANIFEST.MF").getAsset().openStream());
                OSGiMetaData metadata = OSGiMetaDataBuilder.load(manifest);
                if (metadata.getFragmentHost() == null) {
                    container.startBundle(metadata.getBundleSymbolicName(), metadata.getBundleVersion().toString());
                } else {
                    logger.debug("Fragment bundle cannot be started");
                }
            }
        }
    }

    private CommonDeployableContainer<?> container;

    private Archive<?> _arquillianOSGiBundle;

    private long _arquillianOSGiBundleId;

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;
}
