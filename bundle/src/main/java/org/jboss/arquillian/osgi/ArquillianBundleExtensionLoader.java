/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
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
package org.jboss.arquillian.osgi;

import java.util.ArrayList;
import java.util.Collection;

import org.jboss.arquillian.container.test.impl.ContainerTestRemoteExtension;
import org.jboss.arquillian.core.impl.loadable.JavaSPIExtensionLoader;
import org.jboss.arquillian.core.spi.ExtensionLoader;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.protocol.jmx.JMXExtension;
import org.jboss.arquillian.testenricher.osgi.OSGiEnricherRemoteExtension;
import org.osgi.framework.BundleReference;

/**
 * An {@link ExtensionLoader} that works in the context of the installed Arquillian bundle. If so it uses a hardcoded list of
 * extensions instead of dynamic discovery via META-INF/services. The latter would load the wrong extensions from jars embedded
 * in the Arquillian bundle.
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @author Thomas.Diesler@jboss.com
 * @version $Revision: $
 */
public class ArquillianBundleExtensionLoader implements ExtensionLoader {
    @Override
    public Collection<LoadableExtension> load() {
        Collection<LoadableExtension> result;

        ClassLoader classLoader = ArquillianBundleExtensionLoader.class.getClassLoader();
        if (classLoader instanceof BundleReference) {
            // If this ExtensionLoader is used in the context of the installed bundle
            // use a hard coded list of extensions

            result = new ArrayList<LoadableExtension>();
            result.add(new ContainerTestRemoteExtension());
            result.add(new OSGiEnricherRemoteExtension());
            result.add(new JMXExtension());
        } else {
            // Otherwise (e.g. from the client class path) fall back to the default ExtensionLoader
            result = new JavaSPIExtensionLoader().load();
        }
        return result;
    }
}
