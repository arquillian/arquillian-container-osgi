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
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * [ARQ-194] Support multiple bundle deployments
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Sep-2010
 */
@RunWith(Arquillian.class)
public class ARQ194ServiceTestCase
{
   private static final String BUNDLE = "arq194-bundle";
   
   @ArquillianResource
   public Deployer deployer;

   @Inject
   public BundleContext context;

   @Deployment
   public static JavaArchive createDeployment()
   {
      // Include and export the service interface
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "arq194-main");
      archive.addClass(ARQ194Service.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleManifestVersion(2);
            builder.addExportPackages(ARQ194Service.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   @Deployment(name = BUNDLE, testable = false, managed = false)
   public static JavaArchive getTestArchive()
   {
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE + ".jar");
      archive.addClasses(ARQ194Activator.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleSymbolicName(BUNDLE);
            builder.addBundleManifestVersion(2);
            builder.addBundleActivator(ARQ194Activator.class.getName());
            builder.addImportPackages(ARQ194Service.class);
            builder.addImportPackages(BundleActivator.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   
   @Test
   public void testGeneratedBundle() throws Exception
   {
      InputStream input = deployer.getDeployment(BUNDLE);
      Bundle bundle = context.installBundle(BUNDLE, input);

      assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundle.getState());
      assertEquals(BUNDLE, bundle.getSymbolicName());

      bundle.start();
      assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundle.getState());

      BundleContext context = bundle.getBundleContext();
      ServiceReference sref = context.getServiceReference(ARQ194Service.class.getName());
      ARQ194Service service = (ARQ194Service)context.getService(sref);
      int sum = service.sum(1, 2, 3);
      assertEquals(6, sum);

      bundle.stop();
      assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundle.getState());

      bundle.uninstall();
      assertEquals("Bundle UNINSTALLED", Bundle.UNINSTALLED, bundle.getState());
   }
}
