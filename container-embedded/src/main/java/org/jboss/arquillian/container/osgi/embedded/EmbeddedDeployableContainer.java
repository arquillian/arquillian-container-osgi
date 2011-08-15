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
package org.jboss.arquillian.container.osgi.embedded;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.ContainerScoped;
import org.jboss.arquillian.container.spi.context.annotation.DeploymentScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.logging.Logger;
import org.jboss.osgi.spi.framework.OSGiBootstrap;
import org.jboss.osgi.spi.framework.OSGiBootstrapProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.resolver.api.DependencyResolvers;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;
import org.jboss.shrinkwrap.resolver.api.maven.filter.StrictFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * OSGi embedded container
 *
 * @author thomas.diesler@jboss.com
 */
public class EmbeddedDeployableContainer implements DeployableContainer<EmbeddedContainerConfiguration> {
    // Provide logging
    private static final Logger log = Logger.getLogger(EmbeddedDeployableContainer.class);

    @Inject
    @ContainerScoped
    private InstanceProducer<Framework> frameworkInst;

    @Inject
    @ContainerScoped
    private InstanceProducer<BundleContext> bundleContextInst;

    @Inject
    @DeploymentScoped
    private InstanceProducer<Bundle> bundleInst;

    @Inject
    @ContainerScoped
    private InstanceProducer<MBeanServerConnection> mbeanServerInst;

    @Override
    public Class<EmbeddedContainerConfiguration> getConfigurationClass() {
        return EmbeddedContainerConfiguration.class;
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("jmx-osgi");
    }

    @Override
    public void setup(EmbeddedContainerConfiguration configuration) {
        OSGiBootstrapProvider provider = OSGiBootstrap.getBootstrapProvider();
        frameworkInst.set(provider.getFramework());

        MBeanServerConnection mbeanServer = getMBeanServerConnection();
        mbeanServerInst.set(mbeanServer);
    }

    public void start() throws LifecycleException {
        try {
            Framework framework = frameworkInst.get();
            framework.start();
            bundleContextInst.set(framework.getBundleContext());

            Bundle[] bundles = framework.getBundleContext().getBundles();
            if (getInstalledBundle(bundles, "arquillian-osgi-bundle") == null) {
                // Note, the bundle does not have an ImplementationVersion, we use the one of the container.
                String arqVersion = EmbeddedDeployableContainer.class.getPackage().getImplementationVersion();
                installBundle("org.jboss.arquillian.osgi", "arquillian-osgi-bundle", arqVersion, true);
            }
        } catch (BundleException ex) {
            throw new LifecycleException("Cannot start embedded OSGi Framework", ex);
        }
    }

    public void stop() throws LifecycleException {
        try {
            Framework framework = frameworkInst.get();
            framework.stop();
            framework.waitForStop(3000);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new LifecycleException("Cannot stop embedded OSGi Framework", ex);
        }
    }

    public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException {
        try {
            // Export the bundle bytes
            ZipExporter exporter = archive.as(ZipExporter.class);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.exportTo(baos);

            BundleContext context = bundleContextInst.get();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
            Bundle bundle = context.installBundle(archive.getName(), inputStream);

            ServiceReference sref = context.getServiceReference(PackageAdmin.class.getName());
            PackageAdmin pa = (PackageAdmin) context.getService(sref);
            if (pa.resolveBundles(new Bundle[] { bundle }) == false)
                throw new IllegalStateException("Cannot resolve test bundle - see framework log");

            bundleInst.set(bundle);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new DeploymentException("Cannot deploy: " + archive, ex);
        }

        return new ProtocolMetaData()
                 .addContext(new JMXContext(mbeanServerInst.get()));
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        try {
            Bundle bundle = bundleInst.get();
            if (bundle != null) {
                int state = bundle.getState();
                if (state != Bundle.UNINSTALLED)
                    bundle.uninstall();
            }
        } catch (BundleException ex) {
            log.error("Cannot undeploy: " + archive, ex);
        }
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("OSGi does not support Descriptor deployment");
    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("OSGi does not support Descriptor deployment");
    }

    private Bundle getInstalledBundle(Bundle[] bundles, String symbolicName) {
        for (Bundle aux : bundles) {
            if (symbolicName.equals(aux.getSymbolicName()))
                return aux;
        }
        return null;
    }

    private Bundle installBundle(String groupId, String artifactId, String version, boolean startBundle) throws BundleException {
        String filespec = groupId + ":" + artifactId + ":jar:" + version;
        MavenDependencyResolver resolver = DependencyResolvers.use(MavenDependencyResolver.class);
        File[] resolved = resolver.artifact(filespec).resolveAsFiles(new StrictFilter());
        if (resolved == null || resolved.length == 0)
            throw new BundleException("Cannot obtain maven artifact: " + filespec);
        if (resolved.length > 1)
            throw new BundleException("Multiple maven artifacts for: " + filespec);

        File bundleFile = resolved[0];
        try {
            BundleContext sysContext = bundleContextInst.get();
            Bundle bundle = sysContext.installBundle(bundleFile.toURI().toString());
            if (startBundle == true)
                bundle.start();

            return bundle;
        } catch (BundleException ex) {
            log.error("Cannot install/start bundle: " + bundleFile, ex);
        }
        return null;
    }

    private MBeanServerConnection getMBeanServerConnection() {
        MBeanServer mbeanServer = null;

        ArrayList<MBeanServer> serverArr = MBeanServerFactory.findMBeanServer(null);
        if (serverArr.size() > 1)
            log.warnf("Multiple MBeanServer instances: %s", serverArr);

        if (serverArr.size() > 0) {
            mbeanServer = serverArr.get(0);
            log.debugf("Found MBeanServer:%s ", mbeanServer.getDefaultDomain());
        }

        if (mbeanServer == null) {
            log.debugf("No MBeanServer, create one ...");
            mbeanServer = MBeanServerFactory.createMBeanServer();
        }

        return mbeanServer;
    }
}
