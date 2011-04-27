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
package org.jboss.arquillian.container.osgi.remote;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.arquillian.container.osgi.embedded.EmbeddedContainerConfiguration;
import org.jboss.arquillian.impl.core.spi.context.Context;
import org.jboss.arquillian.protocol.jmx.JMXMethodExecutor;
import org.jboss.arquillian.spi.ContainerMethodExecutor;
import org.jboss.arquillian.spi.client.container.DeployableContainer;
import org.jboss.arquillian.spi.client.container.LifecycleException;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.testing.internal.ManagementSupport;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.Archive;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;

/**
 * The remote OSGi container.
 *
 * @author thomas.diesler@jboss.com
 */
public class CopyOfRemoteDeployableContainer implements DeployableContainer<RemoteContainerConfiguration>
{
   // Provide logging
   private static final Logger log = Logger.getLogger(CopyOfRemoteDeployableContainer.class.getName());

   private JMXConnector jmxConnector;
   private ManagementSupport jmxSupport;

   @Override
   public void start(Context context) throws LifecycleException
   {
      // Create the JMXConnector that the test client uses to connect to the remote MBeanServer
      MBeanServerConnection connection = getMBeanServerConnection();
      jmxSupport = new ManagementSupport(connection);

      super.start(context);

      installSupportBundles();
   }

   @Override
   public void stop(Context context) throws LifecycleException
   {
      super.stop(context);
   }

   @Override
   public ContainerMethodExecutor getContainerMethodExecutor()
   {
      MBeanServerConnection mbeanServer = getMBeanServerConnection();
      return new JMXMethodExecutor(mbeanServer, ExecutionType.REMOTE);
   }

   @Override
   public BundleHandle installBundle(Archive<?> archive) throws BundleException, IOException
   {
      VirtualFile virtualFile = OSGiTestHelper.toVirtualFile(archive);
      try
      {
         return installBundle(virtualFile);
      }
      finally
      {
         VFSUtils.safeClose(virtualFile);
      }
   }

   @Override
   public BundleHandle installBundle(URL bundleURL) throws BundleException, IOException
   {
      VirtualFile virtualFile = AbstractVFS.toVirtualFile(bundleURL);
      try
      {
         return installBundle(virtualFile);
      }
      finally
      {
         VFSUtils.safeClose(virtualFile);
      }
   }

   private BundleHandle installBundle(VirtualFile virtualFile) throws BundleException, IOException
   {
      BundleInfo info = BundleInfo.createBundleInfo(virtualFile);
      String streamURL = info.getRoot().getStreamURL().toExternalForm();
      FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
      long bundleId = frameworkMBean.installBundleFromURL(info.getLocation(), streamURL);
      return new BundleHandle(bundleId, info.getSymbolicName());
   }

   @Override
   public void resolveBundle(BundleHandle handle) throws BundleException, IOException
   {
      FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
      frameworkMBean.resolveBundle(handle.getBundleId());
   }

   @Override
   public void uninstallBundle(BundleHandle handle) throws BundleException, IOException
   {
      FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
      frameworkMBean.uninstallBundle(handle.getBundleId());
   }

   @Override
   public int getBundleState(BundleHandle handle)
   {
      try
      {
         BundleStateMBean bundleState = jmxSupport.getBundleStateMBean();
         String state = bundleState.getState(handle.getBundleId());
         if ("INSTALLED".equals(state))
            return Bundle.INSTALLED;
         if ("RESOLVED".equals(state))
            return Bundle.RESOLVED;
         if ("STARTING".equals(state))
            return Bundle.STARTING;
         if ("ACTIVE".equals(state))
            return Bundle.ACTIVE;
         if ("STOPPING".equals(state))
            return Bundle.STOPPING;
         if ("UNINSTALLED".equals(state))
            return Bundle.UNINSTALLED;
         else
            throw new IllegalStateException("Unsupported state: " + state);
      }
      catch (Exception ex)
      {
         Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
         if (cause instanceof InstanceNotFoundException == false)
            log.log(Level.WARNING, "Cannot get state for bundle: " + this, cause);

         return Bundle.UNINSTALLED;
      }
   }

   @Override
   public void startBundle(BundleHandle handle) throws BundleException
   {
      try
      {
         FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
         frameworkMBean.startBundle(handle.getBundleId());
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot start bundle: " + handle, ex);
      }
   }

   @Override
   public void stopBundle(BundleHandle handle) throws BundleException
   {
      try
      {
         FrameworkMBean frameworkMBean = jmxSupport.getFrameworkMBean();
         frameworkMBean.stopBundle(handle.getBundleId());
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot start bundle: " + handle, ex);
      }
   }

   @Override
   public boolean isBundleInstalled(String symbolicName)
   {
      try
      {
         BundleStateMBean bundleStateMBean = jmxSupport.getBundleStateMBean();
         TabularData listBundles = bundleStateMBean.listBundles();
         Iterator<?> iterator = listBundles.values().iterator();
         while (iterator.hasNext())
         {
            CompositeData bundleType = (CompositeData)iterator.next();
            String bsn = (String)bundleType.get(BundleStateMBean.SYMBOLIC_NAME);
            if (bsn.equals(symbolicName))
               return true;
         }
         return false;
      }
      catch (RuntimeException rte)
      {
         throw rte;
      }
      catch (Exception ex)
      {
         throw new IllegalStateException("Cannot obtain remote bundles", ex);
      }
   }

   @Override
   public MBeanServerConnection getMBeanServerConnection()
   {
      String urlString = System.getProperty("jmx.service.url", "service:jmx:rmi:///jndi/rmi://localhost:1090/jmxrmi");
      try
      {
         if (jmxConnector == null)
         {
            log.fine("Connecting JMXConnector to: " + urlString);
            JMXServiceURL serviceURL = new JMXServiceURL(urlString);
            jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
         }

         return jmxConnector.getMBeanServerConnection();
      }
      catch (IOException ex)
      {
         throw new IllegalStateException("Cannot obtain MBeanServerConnection to: " + urlString, ex);
      }
   }
}
