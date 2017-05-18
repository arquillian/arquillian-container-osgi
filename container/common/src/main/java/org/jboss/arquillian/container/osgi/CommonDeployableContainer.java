package org.jboss.arquillian.container.osgi;

import java.util.List;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.shrinkwrap.api.Archive;

public abstract class CommonDeployableContainer <T extends CommonContainerConfiguration> implements DeployableContainer<T> {

    private CommonContainerConfiguration config;

    /**
     * @return Returns true if container starts bundles after deployment automaticly otherwise returns false
     */
    public boolean isAutostartBundle() {
        return config.isAutostartBundle();
    }

    /**
     * Install a bundle from an Archive
     * @param archive Archive
     * @return Returns the bundleId of the bundle that has been just installed
     * @throws Exception If an error occured and therefore bundle was not installed
     */
    public abstract long installBundle(Archive<?> archive, boolean start) throws Exception;

    /**
     * Perform a "refresh packages" operation
     * @throws Exception If an error occured
     */
    public abstract void refresh() throws Exception;

    /**
     * Start a bundle identified by <code>symbolicName</code> and <code>version</code>
     * @param symbolicName Bundle symbolic name
     * @param version Bundle version
     * @throws Exception If an error occured and therefore bundle was not started
     */
    public abstract void startBundle(String symbolicName, String version) throws Exception;

    /**
     * Uninstall a bundle identified by <code>bundleId</code>
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
     * @param name
     * @throws IllegalStateException If bootstrap service was not started
     */
    protected abstract void awaitBootstrapCompleteService(String name);

    @Override
    public void setup(T configuration) {
        this.config = configuration;
    }
}
