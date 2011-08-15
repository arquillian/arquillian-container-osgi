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
package org.jboss.arquillian.container.osgi.remote;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.ContainerScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.logging.Logger;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.testing.internal.ManagementSupport;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.osgi.framework.BundleException;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;

/**
 * The remote OSGi container.
 *
 * @author thomas.diesler@jboss.com
 */
public class RemoteDeployableContainer implements DeployableContainer<RemoteContainerConfiguration> {
    // Provide logging
    private static final Logger log = Logger.getLogger(RemoteDeployableContainer.class.getName());

    @Inject
    @ContainerScoped
    private InstanceProducer<MBeanServerConnection> mbeanServerInst;

    private JMXConnector jmxConnector;
    private ManagementSupport jmxSupport;
    private Map<String, BundleHandle> deployedBundles = new HashMap<String, BundleHandle>();

    @Override
    public Class<RemoteContainerConfiguration> getConfigurationClass() {
        return RemoteContainerConfiguration.class;
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("jmx-osgi");
    }

    @Override
    public void setup(RemoteContainerConfiguration configuration) {
        // Create the JMXConnector that the test client uses to connect to the remote MBeanServer
        MBeanServerConnection mbeanServer = getMBeanServerConnection(configuration);
        mbeanServerInst.set(mbeanServer);

        jmxSupport = new ManagementSupport(mbeanServer);
    }

    @Override
    public void start() throws LifecycleException {
        List<BundleHandle> bundles = getBundles();
        if (getInstalledBundle(bundles, "arquillian-osgi-bundle") == null) {
            installBundle("arquillian-osgi-bundle", true);
        }
    }

    @Override
    public void stop() throws LifecycleException {
        // nothing to do
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            BundleHandle handle = installBundle(archive);
            deployedBundles.put(archive.getName(), handle);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new DeploymentException("Cannot deploy: " + archive.getName(), ex);
        }
        return new ProtocolMetaData()
              .addContext(new JMXContext(mbeanServerInst.get()));
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        BundleHandle handle = deployedBundles.remove(archive.getName());
        if (handle != null) {
            try {
                FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
                frameworkMBean.uninstallBundle(handle.getBundleId());
            } catch (IOException ex) {
                log.errorf(ex, "Cannot undeploy: %s" + archive.getName());
            }
        }
    }

    private BundleHandle installBundle(Archive<?> archive) throws BundleException, IOException {
        VirtualFile virtualFile = toVirtualFile(archive);
        try {
            return installBundle(virtualFile);
        } finally {
            VFSUtils.safeClose(virtualFile);
        }
    }

    private VirtualFile toVirtualFile(Archive<?> archive) throws IOException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        return AbstractVFS.toVirtualFile(archive.getName(), exporter.exportAsInputStream());
    }

    private BundleHandle installBundle(VirtualFile virtualFile) throws BundleException, IOException {
        BundleInfo info = BundleInfo.createBundleInfo(virtualFile);
        String streamURL = info.getRoot().getStreamURL().toExternalForm();
        FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
        long bundleId = frameworkMBean.installBundleFromURL(info.getLocation(), streamURL);
        return new BundleHandle(bundleId, info.getSymbolicName());
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("OSGi does not support Descriptor deployment");
    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("OSGi does not support Descriptor deployment");
    }

    private MBeanServerConnection getMBeanServerConnection(RemoteContainerConfiguration config) {
        String host = config.getHost();
        int port = config.getPort();
        String path = config.getUrlPath();

        MBeanServerConnection mbeanServer;
        String urlString = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/" + path;
        try {
            if (jmxConnector == null) {
                log.debugf("Connecting JMXConnector to: %s", urlString);
                JMXServiceURL serviceURL = new JMXServiceURL(urlString);
                jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
            }

            mbeanServer = jmxConnector.getMBeanServerConnection();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot obtain MBeanServerConnection to: " + urlString, ex);
        }
        return mbeanServer;
    }

    private List<BundleHandle> getBundles() throws LifecycleException {
        List<BundleHandle> bundleList = new ArrayList<BundleHandle>();
        try {
            BundleStateMBean bundleStateMBean = jmxSupport.getBundleStateMBean();
            TabularData listBundles = bundleStateMBean.listBundles();
            Iterator<?> iterator = listBundles.values().iterator();
            while (iterator.hasNext()) {
                CompositeData bundleType = (CompositeData) iterator.next();
                Long bundleId = (Long) bundleType.get(BundleStateMBean.IDENTIFIER);
                String symbolicName = (String) bundleType.get(BundleStateMBean.SYMBOLIC_NAME);
                bundleList.add(new BundleHandle(bundleId, symbolicName));
            }
        } catch (IOException ex) {
            throw new LifecycleException("Cannot list bundles", ex);
        }
        return bundleList;
    }

    private BundleHandle getInstalledBundle(List<BundleHandle> bundles, String symbolicName) {
        for (BundleHandle aux : bundles) {
            if (symbolicName.equals(aux.getSymbolicName()))
                return aux;
        }
        return null;
    }

    private BundleHandle installBundle(String artifactId, boolean startBundle) {
        String classPath = System.getProperty("java.class.path");
        if (classPath.contains(artifactId) == false) {
            log.debug("Class path does not contain '" + artifactId + "'");
            return null;
        }

        String[] paths = classPath.split("" + File.pathSeparatorChar);
        for (String path : paths) {
            if (path.contains(artifactId)) {
                try {
                    long bundleId = jmxSupport.getFrameworkMBean().installBundle(path);
                    String symbolicName = jmxSupport.getBundleStateMBean().getSymbolicName(bundleId);
                    BundleHandle handle = new BundleHandle(bundleId, symbolicName);
                    if (startBundle == true)
                        jmxSupport.getFrameworkMBean().startBundle(bundleId);

                    return handle;
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    static class BundleHandle {
        private long bundleId;
        private String symbolicName;

        public BundleHandle(long bundleId, String symbolicName) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
        }

        public long getBundleId() {
            return bundleId;
        }

        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public String toString() {
            return "[" + bundleId + "]" + symbolicName;
        }
    }
}
