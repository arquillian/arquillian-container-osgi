package org.jboss.arquillian.container.osgi;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.bundle.ArquillianBundleGenerator;
import org.jboss.shrinkwrap.api.Archive;

import java.util.List;

public abstract class CommonDeployableContainer<T extends CommonContainerConfiguration> implements DeployableContainer<T> {

    private CommonContainerConfiguration config;
    private Long arquillianBundleId;

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

    /**
     * @return Returns true if container starts bundles after deployment automaticly otherwise returns false
     */
    public boolean isAutostartBundle() {
        return config.isAutostartBundle();
    }

    /**
     * Install a bundle from an Archive
     *
     * @param archive Archive
     * @return Returns the bundleId of the bundle that has been just installed
     * @throws Exception If an error occured and therefore bundle was not installed
     */
    public abstract long installBundle(Archive<?> archive, boolean start) throws Exception;

    /**
     * Perform a "refresh packages" operation
     *
     * @throws Exception If an error occured
     */
    public abstract void refresh() throws Exception;

    /**
     * Start a bundle identified by <code>symbolicName</code> and <code>version</code>
     *
     * @param symbolicName Bundle symbolic name
     * @param version      Bundle version
     * @throws Exception If an error occured and therefore bundle was not started
     */
    public abstract void startBundle(String symbolicName, String version) throws Exception;

    /**
     * Uninstall a bundle identified by <code>bundleId</code>
     *
     * @param bundleId Bundle id
     * @throws Exception If an error occured and therefore bundle was not uninstalled
     */
    public abstract void uninstallBundle(long bundleId) throws Exception;

    /**
     * Await bootstrap complete services
     */
    public void awaitBootstrapCompleteServices() {
        List<String> services = config.getBootstrapCompleteServices();
        if (services != null) {
            for (String service : services) {
                awaitBootstrapCompleteService(service);
            }
        }
    }

    /**
     * Await for bootstrap service
     *
     * @param name
     * @throws IllegalStateException If bootstrap service was not started
     */
    protected abstract void awaitBootstrapCompleteService(String name);

    protected long installArquillianBundle() throws Exception {
        ServiceLoader serviceLoader = _serviceLoaderInstance.get();
        ArquillianBundleGenerator arquillianBundleGenerator = serviceLoader.onlyOne(ArquillianBundleGenerator.class);

        Archive arquillianBundle = arquillianBundleGenerator.createArquillianBundle();
        arquillianBundleId = installBundle(arquillianBundle, true);
        return arquillianBundleId;
    }

    protected void uninstallArquillianBundle() throws Exception {
        if(arquillianBundleId == null)
        {
            throw new IllegalStateException("Arquillian bundle is not installed");
        }
        uninstallBundle(arquillianBundleId);
    }

    @Override
    public void setup(T configuration) {
        this.config = configuration;
    }
}
