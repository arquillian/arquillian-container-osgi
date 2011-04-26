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
package org.jboss.arquillian.container.osgi.arq194;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import javax.inject.Inject;

import org.jboss.arquillian.container.osgi.arq194.bundle.ARQ194Activator;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * [ARQ-194] Support multiple bundle deployments
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Sep-2010
 */
@RunWith(Arquillian.class)
@Ignore("[ARQ-194] Support multiple bundle deployments")
public class ARQ194TestCase
{
   @Inject
   public BundleContext context;

   //@Inject
   //public DeploymentProvider provider;

   @Test
   public void testInstallBundleFromArchive() throws Exception
   {
      InputStream input = null; //provider.getClientDeploymentAsStream("arq194-bundle");
      Bundle bundle = context.installBundle("arq194-bundle", input);

      assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
      assertEquals("arq194-bundle", bundle.getSymbolicName());

      bundle.start();
      assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

      bundle.stop();
      assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

      bundle.uninstall();
      assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
   }

   @Test
   public void testInstallBundleFromStream() throws Exception
   {
      InputStream input = null; //provider.getClientDeploymentAsStream("arq194-bundle");
      Bundle bundle = context.installBundle("arq194-bundle", input);

      assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
      assertEquals("arq194-bundle", bundle.getSymbolicName());

      bundle.start();
      assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

      bundle.stop();
      assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

      bundle.uninstall();
      assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
   }

   //@ArchiveProvider
   public static JavaArchive getTestArchive(String name)
   {
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleManifestVersion(2);
            builder.addBundleActivator(ARQ194Activator.class.getName());
            builder.addExportPackages(ARQ194Service.class);
            builder.addImportPackages(BundleActivator.class);
            return builder.openStream();
         }
      });
      archive.addClasses(ARQ194Activator.class, ARQ194Service.class);
      return archive;
   }
}
