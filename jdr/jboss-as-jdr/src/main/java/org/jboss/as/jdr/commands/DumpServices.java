/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.jdr.commands;


import java.lang.management.ManagementFactory;
import javax.management.ObjectName;
import javax.management.MBeanServer;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.jboss.as.jdr.JdrLogger.ROOT_LOGGER;

public class DumpServices extends JdrCommand {

    StringBuilder buffer;
    private static String OUTPUT_FILE = "services.txt";

    @Override
    public void execute() throws Exception {
        System.out.println("DumpServices isServerRunning: " + this.env.isServerRunning());
        if(!this.env.isServerRunning())
            return;

        this.buffer = new StringBuilder();

        //$JBOSS_HOME/bin/jboss-cli.sh -c '/core-service=service-container:dump-services' | grep PROBLEM
        // jboss.as:core-service=service-container String dumpServices

        MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("jboss.as:core-service=service-container");
        this.env.getZip().add( (String) platformMBeanServer.invoke(objectName, "dumpServices", null, null), OUTPUT_FILE);
    }
}
