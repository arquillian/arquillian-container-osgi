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
package org.jboss.arquillian.container.osgi.arq198;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import javax.inject.Inject;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * [ARQ-198] Install bundle from maven dependencies
 *
 * @author thomas.diesler@jboss.com
 * @since 03-Sep-2010
 */
@RunWith(Arquillian.class)
@Ignore("[ARQ-198] Install bundle from maven dependencies")
public class ARQ198TestCase
{
   private static String ARQUILLIAN_OSGI_BUNDLE = "arquillian-osgi-bundle";

   @Inject
   public static BundleContext context;

   //@Inject
   //public DeploymentProvider provider;

   @Test
   public void testArtifactFromClasspath() throws Exception
   {
      String artifactId = "org.apache.aries.jmx.api";
      String classPath = System.getProperty("java.class.path");
      assertTrue("java.class.path contains " + artifactId, classPath.contains(artifactId));

      URL artifactURL = null; //provider.getArchiveURL(artifactId);
      assertNotNull("artifactURL not null: " + artifactId, artifactURL);
   }

   @Test
   public void testArtifactFromRepository() throws Exception
   {
      // This must be some artifact that surely gets pulled in the local repo by
      // 'mvn -pl containers/osgi-embedded/ -am clean install' but is not part of the test classpath
      // Mockito is currently used by arquillian-protocol-osgi, but not arquillian-osgi-embedded.
      String groupId = "org.mockito";
      String artifactId = "mockito-all";
      String version = "1.8.3";

      String classPath = System.getProperty("java.class.path");
      assertFalse("java.class.path does not contain " + artifactId, classPath.contains(artifactId));

      URL artifactURL = null; //provider.getArchiveURL(groupId, artifactId, version);
      assertNotNull("artifactURL not null: " + artifactId, artifactURL);
   }

   @Test
   public void testGetBundle() throws Exception
   {
      Bundle bundle = getBundle(ARQUILLIAN_OSGI_BUNDLE, null);
      assertNotNull("ARQ bundle installed", bundle);

      bundle = getBundle(ARQUILLIAN_OSGI_BUNDLE, bundle.getVersion());
      assertNotNull("ARQ bundle installed", bundle);

      bundle = getBundle(ARQUILLIAN_OSGI_BUNDLE, Version.parseVersion("0.0.0"));
      assertNull("ARQ bundle not installed", bundle);
   }

   @Test
   public void testInstallBundleAlreadyInstalled() throws Exception
   {
      URL url1 = null; //provider.getArchiveURL(ARQUILLIAN_OSGI_BUNDLE);
      assertNotNull("Bundle URL found", url1);

      URL url2 = null; //provider.getArchiveURL("org.jboss.arquillian.protocol", ARQUILLIAN_OSGI_BUNDLE, getArquilianVersion());
      assertEquals(url1, url2);
   }

   @Test
   public void testInstallBundleNotYetInstalled() throws Exception
   {
      Bundle jmxBundle = getBundle("org.apache.aries.jmx", null);
      assertNull("Aries JMX not installed", jmxBundle);

      URL url = null; //provider.getArchiveURL("org.apache.aries.jmx");
      assertNotNull("Bundle URL found", url);

      jmxBundle = context.installBundle(url.toExternalForm());
      assertNotNull("Aries JMX installed", jmxBundle);
   }

   private String getArquilianVersion() throws BundleException
   {
      String result = System.getProperty("project.version");
      return result;
   }

   private Bundle getBundle(String symbolicName, Version version) throws BundleException
   {
      if (context == null)
         throw new IllegalArgumentException("Null context");
      if (symbolicName == null)
         throw new IllegalArgumentException("Null symbolicName");

      for (Bundle bundle : context.getBundles())
      {
         boolean artefactMatch = symbolicName.equals(bundle.getSymbolicName());
         boolean versionMatch = version == null || version.equals(bundle.getVersion());
         if (artefactMatch && versionMatch)
            return bundle;
      }
      return null;
   }
}
