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
package org.jboss.arquillian.testenricher.osgi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.inject.Inject;

import org.jboss.arquillian.spi.TestEnricher;
import org.jboss.arquillian.spi.core.Instance;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

/**
 * The OSGi TestEnricher
 * 
 * The enricher supports the injection of the system BundleContext and the test Bundle.
 * 
 * <pre><code>
 *    @Inject
 *    BundleContext context;
 * 
 *    @Inject
 *    Bundle bundle;
 * </code></pre>
 * 
 * @author thomas.diesler@jboss.com
 */
public class OSGiTestEnricher implements TestEnricher
{
   @org.jboss.arquillian.spi.core.annotation.Inject
   private Instance<BundleContext> bundleContextInst;

   @org.jboss.arquillian.spi.core.annotation.Inject
   private Instance<Bundle> bundleInst;

   public void enrich(Object testCase)
   {
      Class<? extends Object> testClass = testCase.getClass();
      for (Field field : testClass.getDeclaredFields())
      {
         if (field.isAnnotationPresent(Inject.class))
         {
            if (field.getType().isAssignableFrom(BundleContext.class))
            {
               injectBundleContext(testCase, field);
            }
            if (field.getType().isAssignableFrom(Bundle.class))
            {
               injectBundle(testCase, field);
            }
         }
      }
   }

   public Object[] resolve(Method method)
   {
      return null;
   }

   private void injectBundleContext(Object testCase, Field field)
   {
      try
      {
         BundleContext bundleContext = bundleContextInst.get();
         if (bundleContext == null)
         {
            ClassLoader classLoader = OSGiTestEnricher.class.getClassLoader();
            if (classLoader instanceof BundleReference) {
               BundleReference bref = (BundleReference)classLoader;
               bundleContext = bref.getBundle().getBundleContext();
               bundleContext = bundleContext.getBundle(0).getBundleContext();
            }
         }
         field.set(testCase, bundleContext);
      }
      catch (IllegalAccessException ex)
      {
         throw new IllegalStateException("Cannot inject BundleContext", ex);
      }
   }

   private void injectBundle(Object testCase, Field field)
   {
      try
      {
         Bundle bundle = bundleInst.get();
         if (bundle == null)
         {
            ClassLoader classLoader = testCase.getClass().getClassLoader();
            if (classLoader instanceof BundleReference) {
               BundleReference bref = (BundleReference)classLoader;
               bundle = bref.getBundle();
            }
         }
         field.set(testCase, bundle);
      }
      catch (IllegalAccessException ex)
      {
         throw new IllegalStateException("Cannot inject Bundle", ex);
      }
   }
}
