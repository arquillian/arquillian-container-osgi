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
package org.jboss.arquillian.container.osgi.karaf.embedded;

import java.io.File;

import org.jboss.arquillian.container.osgi.EmbeddedContainerConfiguration;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.osgi.metadata.spi.NotNullException;

/**
 * KarafContainerConfiguration
 *
 * @author thomas.diesler@jboss.com
 */
public class KarafEmbeddedContainerConfiguration extends EmbeddedContainerConfiguration {

    private String karafHome;
    private Integer karafBeginningStartLevel;

    public String getKarafHome() {
        return karafHome;
    }

    public void setKarafHome(String karafHome) {
        this.karafHome = karafHome;
    }

    public Integer getKarafBeginningStartLevel() {
        return karafBeginningStartLevel;
    }

    public void setKarafBeginningStartLevel(Integer startLevel) {
        this.karafBeginningStartLevel = startLevel;
    }

    @Override
    public void validate() throws ConfigurationException {
        super.validate();

        NotNullException.assertValue(karafHome, "karafHome");
        File karafHomeDir = new File(karafHome);
        if (!karafHomeDir.isDirectory())
            throw new IllegalStateException("Not a valid Karaf home dir: " + karafHomeDir);
    }
}
