package org.jboss.arquillian.container.osgi;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;

public abstract class CommonDeployableContainer <T extends CommonContainerConfiguration> implements DeployableContainer<T> {

    private CommonContainerConfiguration config;

    /**
     * @return Returns true if container starts bundles after deployment automaticly otherwise returns false
     */
    public boolean isAutostartBundle() {
        return config.isAutostartBundle();
    }

    /**
     * Start a bundle identified by <code>symbolicName</code> and <code>version</code>
     * @param symbolicName Bundle symbolic name
     * @param version Bundle version
     * @throws Exception If an error occured and therefore bundle was not started
     */
    public abstract void startBundle(String symbolicName, String version) throws Exception;

    @Override
    public void setup(T configuration) {
        this.config = configuration;
    }
}
