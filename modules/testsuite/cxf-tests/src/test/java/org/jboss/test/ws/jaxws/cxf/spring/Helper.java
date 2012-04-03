/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.ws.jaxws.cxf.spring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.Configurer;
import org.jboss.wsf.spi.classloading.ClassLoaderProvider;
import org.jboss.wsf.stack.cxf.client.configuration.JBossWSBusFactory;
import org.jboss.wsf.stack.cxf.client.util.SpringUtils;
import org.jboss.wsf.test.ClientHelper;

/**
 * Helper for performing various checks on Spring effects over the JBossWS-CXF integration when available in user applications. 
 * 
 * @author alessio.soldano@jboss.com
 * @since 02-Apr-2012
 *
 */
public class Helper implements ClientHelper
{
   private String targetEndpoint;
   private final boolean springInAS = SpringUtils.isSpringAvailable(ClassLoaderProvider.getDefaultProvider().getServerIntegrationClassLoader());

   @Override
   public void setTargetEndpoint(String address)
   {
      targetEndpoint = address;
   }

   /**
    * Verify the web app classloader 'sees' Spring (i.e. Spring jars are in the web app)
    * 
    * @return
    */
   public boolean testSpringAvailability()
   {
      return SpringUtils.isSpringAvailable(Thread.currentThread().getContextClassLoader());
   }

   /**
    * Verify the BusFactory.newInstance() still return the JBossWS-CXF version of BusFactory
    * (the web app has a dependency on jbossws-cxf-client) and that still create a plain bus
    * version, without being fooled by the Spring availability in the TCCL when Spring is not
    * installed in the AS.
    * 
    * @return
    */
   public boolean testJBossWSCXFBus()
   {
      BusFactory factory = BusFactory.newInstance();
      if (!(factory instanceof JBossWSBusFactory))
      {
         throw new RuntimeException("Expected JBossWSBusFactory");
      }
      Bus bus = null;
      try
      {
         if (springInAS) {
            bus = factory.createBus();
            //the created bus should not be a SpringBus, as there's no spring descriptor involved
            return !isSpringBus(bus);
         }
         else
         {
            //set Configurer.USER_CFG_FILE_PROPERTY_NAME so that if the SpringBusFactory is
            //internally erroneously used, that won't fallback delegating to the non Spring
            //one, which would shade the issue
            final String prop = System.getProperty(Configurer.USER_CFG_FILE_PROPERTY_NAME);
            try
            {
               System.setProperty(Configurer.USER_CFG_FILE_PROPERTY_NAME, "unexistentfile.xml");
               bus = factory.createBus();
               //the created bus should not be a SpringBus, as the classloader for CXF has no visibility over the deployment spring jars 
               return !isSpringBus(bus);
            }
            finally
            {
               if (prop == null)
               {
                  System.clearProperty(Configurer.USER_CFG_FILE_PROPERTY_NAME);
               }
               else
               {
                  System.setProperty(Configurer.USER_CFG_FILE_PROPERTY_NAME, prop);
               }
            }
         }
      }
      finally
      {
         if (bus != null)
         {
            bus.shutdown(true);
         }
      }
   }

   /**
    * Verify a Spring version of the Bus is created when actually required
    * and Spring is installed on the AS (the creation process is expected
    * to fail if Spring is not installed on the AS)
    * 
    * @return
    */
   public boolean testJBossWSCXFSpringBus()
   {
      File f = copy(Thread.currentThread().getContextClassLoader().getResourceAsStream("my-cxf.xml"));
      BusFactory factory = BusFactory.newInstance();
      if (!(factory instanceof JBossWSBusFactory))
      {
         throw new RuntimeException("Expected JBossWSBusFactory");
      }
      Bus bus = null;
      try
      {
         bus = ((JBossWSBusFactory) factory).createBus(f.getAbsolutePath());
         //check the created Bus is a SpringBus and we have Spring installed on AS (if it's not installed, this *must* fail)
         return springInAS && isSpringBus(bus);
      }
      catch (Throwable t)
      {
         if (!springInAS && (t instanceof NoClassDefFoundError) && t.getMessage().contains("org/springframework"))
         {
            //Spring is not installed on AS, so the SpringBus can't be created - fine
            return true;
         }
         else
         {
            throw new RuntimeException(t);
         }
      }
      finally
      {
         if (bus != null)
         {
            bus.shutdown(true);
         }
         if (f != null) {
            f.delete();
         }
      }
   }
   
   /**
    * Verify a JAXWS client can be properly created and used to invoke a ws endpoint
    * 
    * @return
    * @throws Exception
    */
   public boolean testJAXWSClient() throws Exception
   {
      Bus bus = BusFactory.newInstance().createBus();
      try
      {
         BusFactory.setThreadDefaultBus(bus);

         URL wsdlURL = new URL(targetEndpoint + "?wsdl");
         QName serviceName = new QName("http://org.jboss.ws/spring", "EndpointService");

         Service service = Service.create(wsdlURL, serviceName);
         Endpoint port = (Endpoint) service.getPort(Endpoint.class);
         return "Hello".equals(port.echo("Hello"));
      }
      finally
      {
         bus.shutdown(true);
      }
   }
   
   private static boolean isSpringBus(Bus bus) {
      //avoid compile/runtime Spring dependency for the check only
      return "org.apache.cxf.bus.spring.SpringBus".equals(bus.getClass().getName());
   }

   private static File copy(InputStream inputStream)
   {
      try
      {
         File f = File.createTempFile("jbws-cxf-testsuite", ".xml");
         OutputStream out = new FileOutputStream(f);
         byte buf[] = new byte[1024];
         int len;
         while ((len = inputStream.read(buf)) > 0)
         {
            out.write(buf, 0, len);
         }
         out.close();
         inputStream.close();
         return f;
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }
   
   public boolean testSpringFunctionalities() throws Exception
   {
      //use reflection to avoid compile Spring dependency (this test is to be run within the non-spring testsuite too,
      //the Spring classes are coming from the jars included in the app on server side)
      URL url = Thread.currentThread().getContextClassLoader().getResource("spring-dd.xml");
      Class<?> cpXmlAppCtxClass = Class.forName("org.springframework.context.support.ClassPathXmlApplicationContext");
      Constructor<?> cons = cpXmlAppCtxClass.getConstructor(String.class);
      Object applicationContext = cons.newInstance(url.toString());
      Class<?> appCtxClass = applicationContext.getClass();
      Method m = appCtxClass.getMethod("getBean", String.class, Class.class);
      Foo foo = (Foo)m.invoke(applicationContext, "foo", Foo.class);
      return "Bar".equals(foo.getMessage());
   }
}
