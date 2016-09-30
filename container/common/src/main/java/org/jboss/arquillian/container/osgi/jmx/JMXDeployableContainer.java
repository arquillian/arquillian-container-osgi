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
package org.jboss.arquillian.container.osgi.jmx;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.jboss.arquillian.container.osgi.AbstractOSGiApplicationArchiveProcessor;
import org.jboss.arquillian.container.osgi.CommonDeployableContainer;
import org.jboss.arquillian.container.osgi.jmx.http.SimpleHTTPServer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.ContainerScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.osgi.framework.BundleException;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;
import org.osgi.jmx.framework.ServiceStateMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMXDeployableContainer
 *
 * @author mbasovni@redhat.com
 */
public abstract class JMXDeployableContainer<T extends JMXContainerConfiguration> extends CommonDeployableContainer<T> {

    static final Logger logger = LoggerFactory.getLogger(JMXDeployableContainer.class.getPackage().getName());

    protected final Map<String, BundleHandle> deployedBundles = new HashMap<String, BundleHandle>();
    private JMXContainerConfiguration config;

    @Inject
    @ContainerScoped
    protected InstanceProducer<MBeanServerConnection> mbeanServerInstance;
    protected FrameworkMBean frameworkMBean;
    protected BundleStateMBean bundleStateMBean;
    protected ServiceStateMBean serviceStateMBean;

    protected JMXContainerConfiguration getContainerConfiguration() {
        return config;
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("jmx-osgi");
    }

    @Override
    public void setup(T configuration) {
        super.setup(configuration);
        this.config = configuration;
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        try {
            BundleHandle handle = installBundle(archive);
            deployedBundles.put(archive.getName(), handle);

            //deploy fragment also
            JavaArchive fragment = archive.getAsType(JavaArchive.class, AbstractOSGiApplicationArchiveProcessor.FRAGMENT_PATH);

            if (fragment != null) {
                BundleHandle handleHandle = installBundle(fragment);

                deployedBundles.put(archive.getName() + "-fragment", handleHandle);
            }
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new DeploymentException("Cannot deploy: " + archive.getName(), ex);
        }
        MBeanServerConnection mbeanServer = mbeanServerInstance.get();
        return new ProtocolMetaData().addContext(new JMXContext(mbeanServer));
    }

    @Override
    public void deploy(Descriptor desc) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        undeploy(archive.getName());

        //undeploy fragment also
        JavaArchive fragment = archive.getAsType(JavaArchive.class, AbstractOSGiApplicationArchiveProcessor.FRAGMENT_PATH);

        if (fragment != null) {
            undeploy(archive.getName() + "-fragment");
        }

    }

    private void undeploy(String name) throws DeploymentException {
        BundleHandle handle = deployedBundles.remove(name);

        if (handle != null) {
            String bundleState = null;
            try {
                long bundleId = handle.getBundleId();
                CompositeData bundleType = bundleStateMBean.getBundle(bundleId);
                if (bundleType != null) {
                    bundleState = (String) bundleType.get(BundleStateMBean.STATE);
                }
            } catch (IOException e) {
                // ignore non-existent bundle
                return;
            }
            if (bundleState != null && !bundleState.equals(BundleStateMBean.UNINSTALLED)) {
                try {
                    long bundleId = handle.getBundleId();
                    frameworkMBean.uninstallBundle(bundleId);
                } catch (IOException ex) {
                    logger.error("Cannot undeploy: " + name, ex);
                }
            }
        }
    }

    @Override
    public void refresh() throws Exception {
    }

    @Override
    public void undeploy(Descriptor desc) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstallBundle(long bundleId) throws Exception {
        try {
            frameworkMBean.uninstallBundle(bundleId);
            logger.info("Bundle '" + bundleId + " was uninstalled");
        } catch (Exception ex) {
            throw new LifecycleException("Cannot uninstall " + bundleId, ex);
        }
    }

    @Override
    public void stop() throws LifecycleException {
    }

    @Override
    public long installBundle(Archive<?> archive, boolean start) throws Exception {
        BundleHandle bundleHandle = installBundle(archive);

        if (start) {
            startBundle(bundleHandle.getBundleId());

            awaitBundleActive(bundleHandle.getBundleId(), 30, TimeUnit.SECONDS);
        }

        return bundleHandle.getBundleId();
    }

    private BundleHandle installBundle(Archive<?> archive) throws BundleException, IOException {
        VirtualFile virtualFile = toVirtualFile(archive);
        try {
            return installBundle(archive.getName(), virtualFile);
        } finally {
            VFSUtils.safeClose(virtualFile);
        }
    }

    private BundleHandle installBundle(String location, VirtualFile virtualFile) throws BundleException, IOException {
        BundleInfo info = BundleInfo.createBundleInfo(virtualFile);
        URL streamURL = info.getRoot().getStreamURL();
        return installBundle(location, streamURL);
    }

    private BundleHandle installBundle(String location, URL streamURL) throws BundleException, IOException {
        URL serverUrl = streamURL;

        // Adapt URL to remote system by serving over HTTP
        SimpleHTTPServer server = null;
        if (!isLocalHost(config)) {
            server = new SimpleHTTPServer();
            serverUrl = server.serve(streamURL);
            server.start();
        }

        try {
            long bundleId = frameworkMBean.installBundleFromURL(location, serverUrl.toExternalForm());
            String symbolicName = bundleStateMBean.getSymbolicName(bundleId);
            String version = bundleStateMBean.getVersion(bundleId);
            return new BundleHandle(bundleId, symbolicName, version);
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    private static boolean isLocalHost(JMXContainerConfiguration config) {
        try {
            JMXServiceURL serviceURL = new JMXServiceURL(config.getJmxServiceURL());
            InetAddress addr = InetAddress.getByName(serviceURL.getHost());

            // Any localhost address
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
                return true;
            }

            // Address of a local network interface
            return (NetworkInterface.getByInetAddress(addr) != null);
        } catch (IOException e) {
            // Assume name lookups imply not local
            return false;
        }
    }

    private VirtualFile toVirtualFile(Archive<?> archive) throws IOException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        return AbstractVFS.toVirtualFile(archive.getName(), exporter.exportAsInputStream());
    }

    protected void awaitBeginningStartLevel(final Integer beginningStartLevel, long timeout, TimeUnit unit) throws IOException, TimeoutException,
        InterruptedException {
        int startLevel = 0;
        long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < timeoutMillis) {
            startLevel = frameworkMBean.getFrameworkStartLevel();
            if (startLevel >= beginningStartLevel) {
                return;
            } else {
                Thread.sleep(500);
            }
        }
        throw new TimeoutException("Beginning start level [" + beginningStartLevel + "] not reached: " + startLevel);
    }

    @Override
    protected void awaitBootstrapCompleteService(String service) {
        try {
            awaitBootstrapCompleteService(service, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot obtain bootsrap complete service: " + service, e);
        }
    }

    protected void awaitBootstrapCompleteService(String serviceName, long timeout, TimeUnit unit) throws TimeoutException, InterruptedException, IOException {
        long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < timeoutMillis) {
            TabularData list = serviceStateMBean.listServices(serviceName, null);
            if (list.size() > 0) {
                return;
            } else {
                Thread.sleep(500);
            }
        }
        throw new TimeoutException("Timeout while waiting for service: " + serviceName);
    }

    protected void awaitBundleActive(long bundleId, long timeout, TimeUnit unit) throws IOException, TimeoutException,
        InterruptedException {

        long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);

        String bundleState = null;

        while (System.currentTimeMillis() < timeoutMillis) {
            bundleState = bundleStateMBean.getState(bundleId);
            if (BundleStateMBean.ACTIVE.equals(bundleState)) {
                return;
            } else {
                Thread.sleep(500);
            }
        }
        throw new TimeoutException("Arquillian bundle [" + bundleId + "] not started: " + bundleState);
    }

    protected MBeanServerConnection getMBeanServerConnection(final long timeout, final TimeUnit unit)
            throws TimeoutException {
        Callable<MBeanServerConnection> callable = new Callable<MBeanServerConnection>() {
            @Override
            public MBeanServerConnection call() throws Exception {
                Exception lastException = null;
                long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
                while (System.currentTimeMillis() < timeoutMillis) {
                    try {
                        return getMBeanServerConnection();
                    } catch (Exception ex) {
                        lastException = ex;
                        Thread.sleep(500);
                    }
                }
                TimeoutException timeoutException = new TimeoutException();
                timeoutException.initCause(lastException);
                throw timeoutException;
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MBeanServerConnection> future = executor.submit(callable);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected MBeanServerConnection getMBeanServerConnection() throws IOException {
        String[] credentials = new String[] { config.getJmxUsername(), config.getJmxPassword() };
        Map<String, ?> env = Collections.singletonMap(JMXConnector.CREDENTIALS, credentials);
        JMXServiceURL serviceURL = new JMXServiceURL(config.getJmxServiceURL());
        JMXConnector connector = JMXConnectorFactory.connect(serviceURL, env);
        return connector.getMBeanServerConnection();
    }

    protected <U> U getMBeanProxy(final MBeanServerConnection mbeanServer, final ObjectName oname, final Class<U> type,
        final long timeout, final TimeUnit unit) throws TimeoutException {

        Callable<U> callable = new Callable<U>() {
            @Override
            public U call() throws Exception {
                IOException lastException = null;
                long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
                while (System.currentTimeMillis() < timeoutMillis) {
                    Set<ObjectName> names = mbeanServer.queryNames(oname, null);
                    if (names.size() == 1) {
                        ObjectName instanceName = names.iterator().next();
                        return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, instanceName, type, false);
                    } else {
                        Thread.sleep(500);
                    }
                }
                logger.warn("Cannot get MBean proxy for type: " + oname, lastException);
                throw new TimeoutException();
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<U> future = executor.submit(callable);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void startBundle(String symbolicName, String version) throws Exception {
        BundleHandle bHandle = getBundle(symbolicName, version);
        if (bHandle == null) {
            throw new IllegalStateException("Bundle '" + symbolicName + ":" + version + "' was not found");
        }
        startBundle(bHandle.getBundleId());
    }

    public void startBundle(long bundleId) throws Exception {
        frameworkMBean.startBundle(bundleId);
    }

    protected BundleHandle getBundle(String symbolicName, String version) throws Exception {
        TabularData listBundles = bundleStateMBean.listBundles();
        Iterator<?> iterator = listBundles.values().iterator();
        while (iterator.hasNext()) {
            CompositeData bundleType = (CompositeData) iterator.next();
            Long bundleId = (Long) bundleType.get(BundleStateMBean.IDENTIFIER);
            String auxName = (String) bundleType.get(BundleStateMBean.SYMBOLIC_NAME);
            String auxVersion = (String) bundleType.get(BundleStateMBean.VERSION);
            if (symbolicName.equals(auxName) && version.equals(auxVersion)) {
                return new BundleHandle(bundleId, symbolicName, auxVersion);
            }
        }
        return null;
    }

    static class BundleHandle {
        private long bundleId;
        private String symbolicName;
        private String version;

        BundleHandle(long bundleId, String symbolicName, String version) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
            this.version = version;
        }

        long getBundleId() {
            return bundleId;
        }

        String getSymbolicName() {
            return symbolicName;
        }

        String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "[" + bundleId + "]" + symbolicName + ":" + version;
        }
    }
}
