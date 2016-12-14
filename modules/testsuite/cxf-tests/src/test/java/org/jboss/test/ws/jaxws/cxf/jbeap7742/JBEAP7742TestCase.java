/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.ws.jaxws.cxf.jbeap7742;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.wsf.test.JBossWSTest;
import org.jboss.wsf.test.JBossWSTestHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import java.io.File;
import java.net.URL;
import java.util.Iterator;

/**
 * @author bspyrkos@redhat.com
 */
@RunWith(Arquillian.class)
public class JBEAP7742TestCase extends JBossWSTest {

    @ArquillianResource
    private URL baseURL;

    @Deployment(name = "jbeap7742", testable = false)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "jbeap7742.war");
        archive.setManifest(new StringAsset("Manifest-Version: 1.0\n"
                + "Dependencies: org.apache.cxf\n"))
                .addClass(org.jboss.test.ws.jaxws.cxf.jbeap7742.v1.Demo.class)
                .addClass(org.jboss.test.ws.jaxws.cxf.jbeap7742.v2.Demo.class)
                .addClass(DemoServiceHandler.class)
                .add(new FileAsset(new File(JBossWSTestHelper.getTestResourcesDir() + "/jaxws/cxf/jbeap7742/handlers.xml")), "WEB-INF/classes/handlers.xml")
                .setWebXML(new File(JBossWSTestHelper.getTestResourcesDir() + "/jaxws/cxf/jbeap7742/WEB-INF/web.xml"));
        return archive;
    }

    @Test
    @RunAsClient
    public void testServiceHandlerInitialization() throws Exception {
        String v1Resp = callVersionService("1");
        String v2Resp = callVersionService("2");

        Assert.assertNotNull("Service version 1 failed", v1Resp);
        Assert.assertNotNull("Service version 2 failed", v2Resp);
    }

    public String callVersionService(String path) throws SOAPException {
        SOAPConnection soapConnection = null;
        try {
            soapConnection = SOAPConnectionFactory.newInstance().createConnection();

            String serviceUrl = baseURL.toString() + "/" + path;
            SOAPMessage soapResponse = soapConnection.call(serviceRequest(), serviceUrl);

            return extractVersion(soapResponse);

        } catch (Exception e) {
            System.err.println("Error occurred while sending SOAP Request to Server");
            e.printStackTrace();
        } finally {
            if (soapConnection != null)
                soapConnection.close();
        }
        return null;
    }

    private SOAPMessage serviceRequest() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();

        envelope.addNamespaceDeclaration("test", "http://org.jboss.ws/jaxws/cxf/jbeap7742");
        envelope.getBody().addChildElement("version", "test");

        soapMessage.saveChanges();

        return soapMessage;
    }

    private static String extractVersion(SOAPMessage soapResponse) throws Exception {
        Iterator fooResponse = soapResponse.getSOAPBody().getChildElements(new QName("http://org.jboss.ws/jaxws/cxf/jbeap7742", "versionResponse"));
        while (fooResponse.hasNext()) {
            return (((SOAPElement)fooResponse.next()).getChildNodes().item(0).getTextContent());
        }
        return null;
    }
}
