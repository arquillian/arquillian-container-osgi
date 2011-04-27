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
package org.jboss.arquillian.container.osgi.arq271;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * [ARQ-271] TestEnricher should be called in @BeforeClass
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Apr-2011
 */
@RunWith(Arquillian.class)
@Ignore("[ARQ-271] TestEnricher should be called in @BeforeClass")
public class ARQ271BeforeClassTestCase
{
   @Inject
   public static BundleContext context;
   
   @Inject
   public static Bundle bundle;
   
   @Deployment
   public static JavaArchive createdeployment()
   {
      return ShrinkWrap.create(JavaArchive.class, "test.jar");
   }

   @BeforeClass
   public static void beforeClass() throws Exception
   {
      assertNotNull("BundleContext injected", context);
      assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
      assertNotNull("Bundle injected", bundle);
      OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
   }

   @AfterClass
   public static void afterClass() throws Exception
   {
      assertNotNull("BundleContext injected", context);
      assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
      assertNotNull("Bundle injected", bundle);
      OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
   }

   @Test
   public void testBundleInjection() throws Exception
   {
      assertNotNull("BundleContext injected", context);
      assertEquals("System Bundle ID", 0, context.getBundle().getBundleId());
      assertNotNull("Bundle injected", bundle);
      OSGiTestHelper.assertBundleState(Bundle.RESOLVED, bundle.getState());
   }
}
