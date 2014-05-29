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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

import org.jboss.arquillian.container.osgi.CommonDeployableContainer;
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
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
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

    static final Logger _logger = LoggerFactory.getLogger(JMXDeployableContainer.class.getPackage().getName());

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
        BundleHandle handle = deployedBundles.remove(archive.getName());
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
                    _logger.error("Cannot undeploy: " + archive.getName(), ex);
                }
            }
        }
    }

    @Override
    public void undeploy(Descriptor desc) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    protected BundleHandle installBundle(String groupId, String artifactId, String version, boolean startBundle)
        throws BundleException, IOException {
        String filespec = groupId + ":" + artifactId + ":jar:" + version;
        File[] resolved = Maven.resolver().resolve(filespec).withoutTransitivity().asFile();
        if (resolved == null || resolved.length == 0)
            throw new BundleException("Cannot obtain maven artifact: " + filespec);
        if (resolved.length > 1)
            throw new BundleException("Multiple maven artifacts for: " + filespec);

        URL fileURL = resolved[0].toURI().toURL();

        long bundleId = frameworkMBean.installBundleFromURL(filespec, fileURL.toExternalForm());
        String symbolicName = bundleStateMBean.getSymbolicName(bundleId);
        BundleHandle handle = new BundleHandle(bundleId, symbolicName);

        if (startBundle) {
            frameworkMBean.startBundle(handle.getBundleId());
        }
        return handle;
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
        long bundleId = frameworkMBean.installBundleFromURL(location, streamURL.toExternalForm());
        String symbolicName = bundleStateMBean.getSymbolicName(bundleId);
        return new BundleHandle(bundleId, symbolicName);
    }

    protected void installArquillianBundle() throws LifecycleException, IOException {

        List<BundleHandle> bundleList = listBundles("arquillian-osgi-bundle");
        if (bundleList.isEmpty()) {
            try {
                // Note, the bundle does not have an ImplementationVersion, we
                // use the one of the container.
                String arqVersion = JMXDeployableContainer.class.getPackage().getImplementationVersion();
                if (arqVersion == null) {
                    arqVersion = System.getProperty("arquillian.osgi.version");
                }
                installBundle("org.jboss.arquillian.osgi", "arquillian-osgi-bundle", arqVersion, true);
            } catch (BundleException ex) {
                throw new LifecycleException("Cannot install arquillian-osgi-bundle", ex);
            }
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

    protected void awaitArquillianBundleActive(long timeout, TimeUnit unit) throws IOException, TimeoutException,
        InterruptedException {
        String symbolicName = "arquillian-osgi-bundle";
        List<BundleHandle> list = listBundles(symbolicName);
        if (list.size() != 1)
            throw new IllegalStateException("Cannot obtain: " + symbolicName);

        String bundleState = null;
        long bundleId = list.get(0).getBundleId();
        long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
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
                _logger.warn("Cannot get MBean proxy for type: " + oname, lastException);
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

    protected List<BundleHandle> listBundles(String symbolicName) throws IOException {
        List<BundleHandle> bundleList = new ArrayList<BundleHandle>();
        TabularData listBundles = bundleStateMBean.listBundles();
        Iterator<?> iterator = listBundles.values().iterator();
        while (iterator.hasNext()) {
            CompositeData bundleType = (CompositeData) iterator.next();
            Long bundleId = (Long) bundleType.get(BundleStateMBean.IDENTIFIER);
            String auxName = (String) bundleType.get(BundleStateMBean.SYMBOLIC_NAME);
            if (symbolicName == null || symbolicName.equals(auxName)) {
                bundleList.add(new BundleHandle(bundleId, symbolicName));
            }
        }
        return bundleList;
    }

    @Override
    public void startBundle(String symbolicName, String version) throws Exception {
        BundleHandle bHandle = getBundle(symbolicName, version);
        if (bHandle == null) {
            throw new IllegalStateException("Bundle '" + symbolicName + ":" + version + "' was not found");
        }
        frameworkMBean.startBundle(bHandle.getBundleId());
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
                return new BundleHandle(bundleId, symbolicName);
            }
        }
        return null;
    }

    static class BundleHandle {
        private long bundleId;
        private String symbolicName;

        BundleHandle(long bundleId, String symbolicName) {
            this.bundleId = bundleId;
            this.symbolicName = symbolicName;
        }

        long getBundleId() {
            return bundleId;
        }

        String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public String toString() {
            return "[" + bundleId + "]" + symbolicName;
        }
    }
}
