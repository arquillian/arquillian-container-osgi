/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.testenricher.osgi;

import java.lang.annotation.Annotation;
import org.jboss.arquillian.container.test.impl.enricher.resource.OperatesOnDeploymentAwareProvider;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.annotation.SuiteScoped;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * {@link OperatesOnDeploymentAwareProvider} implementation to
 * provide {@link PackageAdmin} injection to {@link ArquillianResource}-
 * annotated fields.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 15-Oct-2012
 */
public class PackageAdminProvider implements ResourceProvider {

    @Inject
    @SuiteScoped
    private InstanceProducer<PackageAdmin> packageAdminProducer;

    @Inject
    private Instance<PackageAdmin> packageAdmin;

    @Override
    public boolean canProvide(final Class<?> type) {
        return type.isAssignableFrom(PackageAdmin.class);
    }

    @Override
    public Object lookup(ArquillianResource resource, Annotation... qualifiers) {
        initialize();
        return packageAdmin.get();
    }

    private void initialize() {
            BundleContext syscontext = BundleContextProvider.getBundleContext();
            PackageAdmin service = getPackageAdmin(syscontext);
            if (service != null) {
                packageAdminProducer.set(service);
            }
    }

    private PackageAdmin getPackageAdmin(BundleContext syscontext) {
        PackageAdmin result = null;
        if (syscontext != null) {
            ServiceReference sref = syscontext.getServiceReference(PackageAdmin.class.getName());
            result = (PackageAdmin) syscontext.getService(sref);
        }
        return result;
    }
}
