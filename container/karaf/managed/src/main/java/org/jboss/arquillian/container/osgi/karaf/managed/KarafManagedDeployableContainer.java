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
package org.jboss.arquillian.container.osgi.karaf.managed;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.arquillian.container.osgi.EmbeddedDeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
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
 * KarafManagedDeployableContainer
 *
 * @author thomas.diesler@jboss.com
 */
public class KarafManagedDeployableContainer implements DeployableContainer<KarafManagedContainerConfiguration> {

    static final Logger LOGGER = LoggerFactory.getLogger(KarafManagedDeployableContainer.class.getPackage().getName());

    @Inject
    @ContainerScoped
    private InstanceProducer<MBeanServerConnection> mbeanServerInstance;

    private final Map<String, BundleHandle> deployedBundles = new HashMap<String, BundleHandle>();
    private KarafManagedContainerConfiguration config;
    private FrameworkMBean frameworkMBean;
    private BundleStateMBean bundleStateMBean;
    private ServiceStateMBean serviceStateMBean;
    private Process process;

    @Override
    public Class<KarafManagedContainerConfiguration> getConfigurationClass() {
        return KarafManagedContainerConfiguration.class;
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("jmx-osgi");
    }

    @Override
    public void setup(KarafManagedContainerConfiguration config) {
        this.config = config;
    }

    @Override
    public void start() throws LifecycleException {

        // Try to connect to an already running server
        MBeanServerConnection mbeanServer = null;
        try {
            mbeanServer = getMBeanServerConnection(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // ignore
        }

        if (mbeanServer != null && !config.isAllowConnectingToRunningServer()) {
            throw new LifecycleException(
                    "The server is already running! Managed containers does not support connecting to running server instances due to the " +
                    "possible harmful effect of connecting to the wrong server. Please stop server before running or change to another type of container.\n" +
                    "To disable this check and allow Arquillian to connect to a running server, set allowConnectingToRunningServer to true in the container configuration");
        }

        // Start the Karaf process
        if (mbeanServer == null) {
            String karafHome = config.getKarafHome();
            if (karafHome == null)
                throw new IllegalStateException("karafHome cannot be null");

            File karafHomeDir = new File(karafHome).getAbsoluteFile();
            if (!karafHomeDir.isDirectory())
                throw new IllegalStateException("Not a valid Karaf home dir: " + karafHomeDir);

            List<String> cmd = new ArrayList<String>();
            cmd.add("java");

            // JavaVM args
            String javaArgs = config.getJavaVmArguments();
            if (!javaArgs.contains("-Xmx")) {
                javaArgs = KarafManagedContainerConfiguration.DEFAULT_JAVAVM_ARGUMENTS + " " + javaArgs;
            }
            cmd.addAll(Arrays.asList(javaArgs.split("\\s")));

            // Karaf properties
            cmd.add("-Dkaraf.home=" + karafHomeDir);
            cmd.add("-Dkaraf.base=" + karafHomeDir);
            cmd.add("-Dkaraf.etc=" + karafHomeDir + "/etc");
            cmd.add("-Dkaraf.data=" + karafHomeDir + "/data");
            cmd.add("-Dkaraf.instances=" + karafHomeDir + "/instances");
            cmd.add("-Dkaraf.startLocalConsole=false");
            cmd.add("-Dkaraf.startRemoteShell=false");

            // Java properties
            cmd.add("-Djava.io.tmpdir=" + new File(karafHomeDir, "data/tmp"));
            cmd.add("-Djava.util.logging.config.file=" + new File(karafHomeDir, "etc/java.util.logging.properties"));
            cmd.add("-Djava.endorsed.dirs=" + new File(karafHomeDir, "lib/endorsed"));

            // Classpath
            StringBuffer classPath = new StringBuffer();
            File karafLibDir = new File(karafHomeDir, "lib");
            String[] libs = karafLibDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("karaf");
                }
            });
            for (String lib : libs) {
                String separator = classPath.length() > 0 ? File.pathSeparator : "";
                classPath.append(separator + new File(karafHomeDir, "lib/" + lib));
            }
            cmd.add("-classpath");
            cmd.add(classPath.toString());

            // Main class
            cmd.add("org.apache.karaf.main.Main");

            // Output the startup command
            StringBuffer cmdstr = new StringBuffer();
            for (String tok : cmd) {
                cmdstr.append(tok + " ");
            }
            LOGGER.debug("Starting Karaf with: {}", cmdstr);

            try {
                ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                processBuilder.directory(karafHomeDir);
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                new Thread(new ConsoleConsumer()).start();
            } catch (Exception ex) {
                throw new LifecycleException("Cannot start managed Karaf container", ex);
            }

            // Get the MBeanServerConnection
            try {
                mbeanServer = getMBeanServerConnection(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                destroyKarafProcess();
                throw new LifecycleException("Cannot obtain MBean server connection", ex);
            }
        }

        mbeanServerInstance.set(mbeanServer);

        try {
            // Get the FrameworkMBean
            ObjectName oname = ObjectNameFactory.create("osgi.core:type=framework,*");
            frameworkMBean = getMBeanProxy(mbeanServer, oname, FrameworkMBean.class, 30, TimeUnit.SECONDS);

            // Get the BundleStateMBean
            oname = ObjectNameFactory.create("osgi.core:type=bundleState,*");
            bundleStateMBean = getMBeanProxy(mbeanServer, oname, BundleStateMBean.class, 30, TimeUnit.SECONDS);

            // Get the BundleStateMBean
            oname = ObjectNameFactory.create("osgi.core:type=serviceState,*");
            serviceStateMBean = getMBeanProxy(mbeanServer, oname, ServiceStateMBean.class, 30, TimeUnit.SECONDS);

            // Install the arquillian bundle to become active
            installArquillianBundle();

            // Await the arquillian bundle to become active
            awaitArquillianBundleActive(30, TimeUnit.SECONDS);

            // Await the beginning start level
            Integer beginningStartLevel = config.getKarafBeginningStartLevel();
            if (beginningStartLevel != null)
                awaitKarafBeginningStartLevel(beginningStartLevel, 30, TimeUnit.SECONDS);

            // Await the bootstrap complete marker service to become available
            String completeServices = config.getBootstrapCompleteService();
            if (completeServices != null) {
                List<String> completeServicesList = Arrays.asList(completeServices.split(","));
                for (String completeService : completeServicesList) {
                    awaitBootstrapCompleteService(completeService, 30, TimeUnit.SECONDS);
                }
            }

        } catch (RuntimeException rte) {
            destroyKarafProcess();
            throw rte;
        } catch (Exception ex) {
            destroyKarafProcess();
            throw new LifecycleException("Cannot start Karaf container", ex);
        }
    }

    @Override
    public void stop() throws LifecycleException {
        destroyKarafProcess();
    }

    private void destroyKarafProcess() {
        if (process != null) {
            process.destroy();
        }
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
    public void undeploy(Archive<?> archive) throws DeploymentException {
        BundleHandle handle = deployedBundles.remove(archive.getName());
        if (handle != null) {
            String bundleState = null;
            try {
                long bundleId = handle.getBundleId();
                CompositeData bundleType = getBundle(bundleId);
                if (bundleType != null) {
                    bundleState = (String) bundleType.get(BundleStateMBean.STATE);
                }
            } catch (IOException e) {
                // ignore if the operation fails
                return;
            } catch (IllegalArgumentException e) {
                // ignore non-existent bundle
                return;
            }
            if (bundleState != null && !bundleState.equals(BundleStateMBean.UNINSTALLED)) {
                try {
                    long bundleId = handle.getBundleId();
                    frameworkMBean.uninstallBundle(bundleId);
                } catch (IOException ex) {
                    LOGGER.error("Cannot undeploy: " + archive.getName(), ex);
                }
            }
        }
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    private MBeanServerConnection getMBeanServerConnection(final long timeout, final TimeUnit unit) throws TimeoutException {
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

    private MBeanServerConnection getMBeanServerConnection() throws IOException {
        String[] credentials = new String[] { config.getJmxUsername(), config.getJmxPassword() };
        Map<String, ?> env = Collections.singletonMap(JMXConnector.CREDENTIALS, credentials);
        JMXServiceURL serviceURL = new JMXServiceURL(config.getJmxServiceURL());
        JMXConnector connector = JMXConnectorFactory.connect(serviceURL, env);
        return connector.getMBeanServerConnection();
    }

    private <T> T getMBeanProxy(final MBeanServerConnection mbeanServer, final ObjectName oname, final Class<T> type, final long timeout, final TimeUnit unit)
            throws TimeoutException {
        Callable<T> callable = new Callable<T>() {
            @Override
            public T call() throws Exception {
                IOException lastException = null;
                long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
                while (System.currentTimeMillis() < timeoutMillis) {
                    Set<ObjectName> names = mbeanServer.queryNames(oname, null);
                    if (names.size() == 1) {
                        ObjectName instanceName = names.iterator().next();
                        return MBeanProxy.get(mbeanServer, instanceName, type);
                    } else {
                        Thread.sleep(500);
                    }
                }
                LOGGER.warn("Cannot get MBean proxy for type: " + oname, lastException);
                throw new TimeoutException();
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(callable);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected void installArquillianBundle() throws LifecycleException, IOException {
        List<BundleHandle> bundleList = listBundles("arquillian-osgi-bundle");
        if (bundleList.isEmpty()) {
            try {
                // Note, the bundle does not have an ImplementationVersion, we use the one of the container.
                String arqVersion = EmbeddedDeployableContainer.class.getPackage().getImplementationVersion();
                if (arqVersion == null) {
                    arqVersion = System.getProperty("arquillian.osgi.version");
                }
                installBundle("org.jboss.arquillian.osgi", "arquillian-osgi-bundle", arqVersion, true);
            } catch (BundleException ex) {
                throw new LifecycleException("Cannot install arquillian-osgi-bundle", ex);
            }
        }
    }

    private void awaitArquillianBundleActive(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {

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

    private void awaitKarafBeginningStartLevel(final Integer beginningStartLevel, long timeout, TimeUnit unit) throws IOException, TimeoutException,
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

    private void awaitBootstrapCompleteService(String serviceName, long timeout, TimeUnit unit) throws TimeoutException, InterruptedException, IOException {
        long timeoutMillis = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < timeoutMillis) {
            TabularData list = listServices(serviceName);
            if (list.size() > 0) {
                return;
            } else {
                Thread.sleep(500);
            }
        }
        throw new TimeoutException("Cannot obtain service: " + serviceName);
    }

    private BundleHandle installBundle(Archive<?> archive) throws BundleException, IOException {
        VirtualFile virtualFile = toVirtualFile(archive);
        try {
            return installBundle(archive.getName(), virtualFile);
        } finally {
            VFSUtils.safeClose(virtualFile);
        }
    }

    private VirtualFile toVirtualFile(Archive<?> archive) throws IOException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        return AbstractVFS.toVirtualFile(archive.getName(), exporter.exportAsInputStream());
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

    private BundleHandle installBundle(String groupId, String artifactId, String version, boolean startBundle) throws BundleException, IOException {
        String filespec = groupId + ":" + artifactId + ":jar:" + version;
        File[] resolved = Maven.resolver().resolve(filespec).withoutTransitivity().asFile();
        if (resolved == null || resolved.length == 0)
            throw new BundleException("Cannot obtain maven artifact: " + filespec);
        if (resolved.length > 1)
            throw new BundleException("Multiple maven artifacts for: " + filespec);

        URL fileURL = resolved[0].toURI().toURL();
        BundleHandle handle = installBundle(filespec, fileURL);
        if (startBundle) {
            frameworkMBean.startBundle(handle.getBundleId());
        }
        return handle;
    }

    private List<BundleHandle> listBundles(String symbolicName) throws IOException {
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

    private CompositeData getBundle(long bundleIdentifier) throws IOException {
        TabularData listBundles = bundleStateMBean.listBundles();
        Iterator<?> iterator = listBundles.values().iterator();
        while (iterator.hasNext()) {
            CompositeData bundleType = (CompositeData) iterator.next();
            Long bundleId = (Long) bundleType.get(BundleStateMBean.IDENTIFIER);
            if (bundleId.longValue() == bundleIdentifier) {
                return bundleType;
            }
        }
        throw new IllegalArgumentException("Indicated bundle (id=" + bundleIdentifier + ") does not exist");
    }

    private TabularData listServices(String clazz) throws IOException {
        TabularData list = serviceStateMBean.listServices();
        Iterator<?> iterator = list.values().iterator();
        while (iterator.hasNext()) {
            CompositeData serviceType = (CompositeData) iterator.next();
            String [] serviceIntfsArray = (String []) serviceType.get(ServiceStateMBean.OBJECT_CLASS);
            List<String> serviceIntfsList = Arrays.asList(serviceIntfsArray);
            if (!serviceIntfsList.contains(clazz)) {
                iterator.remove();
            }
        }
        return list;
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

    /**
     * Runnable that consumes the output of the process. If nothing consumes the output the AS will hang on some platforms
     *
     * @author Stuart Douglas
     */
    private class ConsoleConsumer implements Runnable {

        @Override
        public void run() {
            final InputStream stream = process.getInputStream();
            final boolean writeOutput = config.isOutputToConsole();
            try {
                byte[] buf = new byte[32];
                int num;
                // Do not try reading a line cos it considers '\r' end of line
                while ((num = stream.read(buf)) != -1) {
                    if (writeOutput)
                        System.out.write(buf, 0, num);
                }
            } catch (IOException e) {
            }
        }
    }
}
