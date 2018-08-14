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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.bundle.ArquillianBundleGenerator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.ServiceTracker;

/**
 * EmbeddedDeployableContainer
 *
 * @author thomas.diesler@jboss.com
 */
public abstract class EmbeddedDeployableContainer<T extends EmbeddedContainerConfiguration> extends CommonDeployableContainer<T> {

    public interface ContainerLogger {

        enum Level {
            DEBUG, INFO, WARN, ERROR
        }

        void debug(String message);

        void debug(String message, Throwable th);

        void info(String message);

        void info(String message, Throwable th);

        void error(String message);

        void error(String message, Throwable th);

        void warn(String message);

        void warn(String message, Throwable th);

        void log(Level level, String message);

        void log(Level level, String message, Throwable th);
    }

    private ContainerLogger log;
    private Framework framework;
    private BundleContext syscontext;
    private MBeanServerConnection mbeanServer;
    private EmbeddedContainerConfiguration configuration;

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("jmx-osgi");
    }

    @Override
    public void setup(T configuration) {
        super.setup(configuration);
        this.configuration = configuration;
        this.log = getLogger();
        this.framework = createFramework(configuration);
        this.mbeanServer = getMBeanServerConnection();
    }

    protected EmbeddedContainerConfiguration getContainerConfiguration() {
        return configuration;
    }

    protected ContainerLogger getLogger() {
        return new AbstractContainerLogger() {
            @Override
            public void log(Level level, String message, Throwable th) {
                System.out.println(message);
                if (th != null)
                    th.printStackTrace();
            }
        };
    }

    protected Framework createFramework(T conf) {
        FrameworkFactory factory = conf.getFrameworkFactory();
        if (factory == null)
            throw new IllegalStateException("Cannot obtain " + FrameworkFactory.class.getName());
        Map<String, String> config = conf.getFrameworkConfiguration();
        return factory.newFramework(config);
    }

    protected Framework getFramework() {
        return framework;
    }

    protected BundleContext startFramework() throws BundleException {
        framework.start();
        return framework.getBundleContext();
    }

    protected void stopFramework() throws BundleException {
        framework.stop();
    }

    @Override
    public long installBundle(Archive<?> archive, boolean start) throws Exception {
        try {
            ZipExporter exporter = archive.as(ZipExporter.class);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exporter.exportTo(baos);

            String location = archive.getName();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(baos.toByteArray());
            log.info("Installing bundle: " + location);

            Bundle bundle = installBundle(location, inputStream);

            if (start) {
                bundle.start();

                awaitBundleActive(bundle.getSymbolicName(), syscontext, 1, TimeUnit.MINUTES);
            }

            return bundle.getBundleId();
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new DeploymentException("Cannot deploy: " + archive, ex);
        }
    }

    protected Bundle installBundle(String location, InputStream inputStream) throws BundleException {
        return syscontext.installBundle(location, inputStream);
    }

    protected void uninstallBundle(Bundle bundle) throws BundleException {
        bundle.uninstall();
    }

    @Override
    public void uninstallBundle(long bundleId) throws Exception {
        Bundle bundle = syscontext.getBundle(bundleId);

        bundle.uninstall();
    }

    @Override
    public void startBundle(String symbolicName, String version) throws Exception {
        for (Bundle bundle : syscontext.getBundles()) {
            if (bundle.getSymbolicName().equals(symbolicName) && bundle.getVersion().toString().equals(version)) {
                bundle.start();
                return;
            }
        }
        throw new IllegalStateException("Bundle '" + symbolicName + ":" + version + "' was not found");
    }

    @Override
    public void start() throws LifecycleException {
        log.debug("Starting OSGi embedded container: " + getClass().getName());
        try {
            syscontext = startFramework();
        } catch (BundleException ex) {
            throw new LifecycleException("Cannot start embedded OSGi Framework", ex);
        }

        installArquillianBundle();
        awaitBundleActive(ArquillianBundleGenerator.BUNDLE_SYMBOLIC_NAME, syscontext, 30, TimeUnit.SECONDS);

        log.info("Started OSGi embedded container: " + getClass().getName());
    }

    protected void awaitBundleActive(final String symbolicName, BundleContext syscontext, long timeout, TimeUnit unit) throws LifecycleException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bundle> bundleRef = new AtomicReference<Bundle>();
        int states = Bundle.INSTALLED | Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE;
        BundleTracker<Bundle> tracker = new BundleTracker<Bundle>(syscontext, states, null) {
            @Override
            public Bundle addingBundle(Bundle bundle, BundleEvent event) {
                if (symbolicName.equals(bundle.getSymbolicName())) {
                    bundleRef.set(bundle);
                    return bundle;
                } else {
                    return null;
                }
            }

            @Override
            public void modifiedBundle(Bundle bundle, BundleEvent event, Bundle tracked) {
                if (event != null && event.getType() == BundleEvent.STARTED) {
                    latch.countDown();
                }
            }
        };
        tracker.open();

        try {
            Bundle arqBundle = bundleRef.get();
            if (arqBundle == null || arqBundle.getState() != Bundle.ACTIVE) {
                try {
                    if (!latch.await(timeout, unit)) {
                        throw new LifecycleException("The bundle " + symbolicName + " can't be started on time");
                    }
                } catch (InterruptedException ex) {
                    throw new LifecycleException("Framework startup interupted", ex);
                }
            }
        } finally {
            tracker.close();
        }
    }

    @Override
    protected void awaitBootstrapCompleteService(String service) {
        try {
            awaitBootstrapCompleteService(syscontext, service, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot obtain bootsrap complete service: " + service, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void awaitBootstrapCompleteService(BundleContext syscontext, String serviceName, long timeout, TimeUnit unit) {
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceTracker<?, ?> tracker = new ServiceTracker(syscontext, serviceName, null) {
            @Override
            public Object addingService(ServiceReference sref) {
                Object service = super.addingService(sref);
                latch.countDown();
                return service;
            }
        };
        tracker.open();
        try {
            if (!latch.await(timeout, unit))
                throw new IllegalStateException("Giving up waiting for bootstrap service: " + serviceName);
        } catch (InterruptedException e) {
            // ignore
        } finally {
            tracker.close();
        }
    }

    @Override
    public void refresh() throws Exception {
        FrameworkWiring frameworkWiring = syscontext.getBundle().adapt(FrameworkWiring.class);

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        frameworkWiring.refreshBundles(null, new FrameworkListener() {

            @Override
            public void frameworkEvent(FrameworkEvent frameworkEvent) {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws LifecycleException {
        try {
            stopFramework();
            framework.waitForStop(3000);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new LifecycleException("Cannot stop embedded OSGi Framework", ex);
        } finally {
            syscontext = null;
        }
    }

    @Override
    public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException {
        try {
            installBundle(archive, false);
        } catch (Exception e) {
            throw new DeploymentException("Can't deploy archive", e);
        }

        return new ProtocolMetaData().addContext(new JMXContext(mbeanServer));
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        try {
            String location = archive.getName();
            log.info("Uninstalling bundle: " + location);

            Bundle bundle = syscontext.getBundle(location);
            if (bundle != null && bundle.getState() != Bundle.UNINSTALLED) {
                uninstallBundle(bundle);
            }
        } catch (BundleException ex) {
            log.warn("Cannot undeploy: " + archive, ex);
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

    private MBeanServerConnection getMBeanServerConnection() {
        MBeanServer mbeanServer = null;

        ArrayList<MBeanServer> serverArr = MBeanServerFactory.findMBeanServer(null);
        if (serverArr.size() > 1)
            log.warn("Multiple MBeanServer instances: " + serverArr);

        if (serverArr.size() > 0) {
            mbeanServer = serverArr.get(0);
            log.debug("Found MBeanServer: " + mbeanServer.getDefaultDomain());
        }

        if (mbeanServer == null) {
            log.debug("No MBeanServer, create one ...");
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        return mbeanServer;
    }

    private Long installArquillianBundle() {
        ServiceLoader serviceLoader = _serviceLoaderInstance.get();
        ArquillianBundleGenerator arquillianBundleGenerator = serviceLoader.onlyOne(ArquillianBundleGenerator.class);
        try {
            Archive arquillianBundle = arquillianBundleGenerator.createArquillianBundle();
            return installBundle(arquillianBundle, true);
        } catch (Exception e) {
            log.error("Can't generate " + ArquillianBundleGenerator.BUNDLE_NAME);
        }
        return null;
    }

    public abstract static class AbstractContainerLogger implements ContainerLogger {

        @Override
        public void debug(String message) {
            log(Level.DEBUG, message, null);
        }

        @Override
        public void debug(String message, Throwable th) {
            log(Level.DEBUG, message, th);
        }

        @Override
        public void info(String message) {
            log(Level.INFO, message, null);
        }

        @Override
        public void info(String message, Throwable th) {
            log(Level.INFO, message, th);
        }

        @Override
        public void warn(String message) {
            log(Level.WARN, message, null);
        }

        @Override
        public void warn(String message, Throwable th) {
            log(Level.WARN, message, th);
        }

        @Override
        public void error(String message) {
            log(Level.ERROR, message, null);
        }

        @Override
        public void error(String message, Throwable th) {
            log(Level.ERROR, message, th);
        }

        @Override
        public void log(Level level, String message) {
            log(level, message, null);
        }
    }

}
