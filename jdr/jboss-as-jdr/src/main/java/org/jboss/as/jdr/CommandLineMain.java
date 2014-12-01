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

package org.jboss.as.jdr;

import static org.jboss.as.jdr.JdrMessages.MESSAGES;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.as.cli.scriptsupport.CLI.Result;

import org.jboss.as.controller.client.helpers.ClientConstants;

import org.jboss.dmr.ModelNode;

import org.jboss.as.controller.OperationFailedException;

/**
 * Provides a main for collecting a JDR report from the command line.
 *
 * @author Mike M. Clark
 * @author Jesse Jaggars
 */
public class CommandLineMain {

    private static CommandLineParser parser = new GnuParser();
    private static Options options = new Options();
    private static HelpFormatter formatter = new HelpFormatter();
    private static final String usage = "jdr.{sh,bat} [options]";

    static {
        options.addOption("h", "help", false, MESSAGES.jdrHelpMessage());
        options.addOption("H", "host", true, MESSAGES.jdrHostnameMessage());
        options.addOption("p", "port", true, MESSAGES.jdrPortMessage());
    }

    /**
     * Creates a JBoss Diagnostic Reporter (JDR) Report. A JDR report response
     * is printed to <code>System.out</code>.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        int port = 9999;
        String host = "localhost";
        String username = null;
        char[] password = null;

        try {
            CommandLine line = parser.parse(options, args, false);

            if (line.hasOption("help")) {
                formatter.printHelp(usage, options);
                return;
            }
            if (line.hasOption("host")) {
                host = line.getOptionValue("host");
            }

            if (line.hasOption("port")) {
                port = Integer.parseInt(line.getOptionValue("port"));
            }

            if (line.hasOption("username")) {
                username = line.getOptionValue("username");
            }

            if (line.hasOption("password")) {
                password = line.getOptionValue("password").toCharArray();
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(usage, options);
            return;
        } catch (NumberFormatException nfe) {
            System.out.println(nfe.getMessage());
            formatter.printHelp(usage, options);
            return;
        }

        System.out.println("Initializing JBoss Diagnostic Reporter...");

        CLI cli = null;
        try {
            cli = CLI.newInstance();
            cli.connect(host, port, username, password);
            Result cmdResult = cli.cmd("/subsystem=jdr:generate-jdr-report()");
            ModelNode response = cmdResult.getResponse();
            reportFailure(response);
            ModelNode result = response.get(ClientConstants.RESULT);
            String startTime = result.get("start-time").asString();
            String endTime = result.get("end-time").asString();
            String reportLocation = result.get("report-location").asString();
            System.out.println("JDR started: " + startTime);
            System.out.println("JDR ended: " + endTime);
            System.out.println("JDR location: " + reportLocation);
        } catch (IllegalStateException ise) {
            //ise.printStackTrace();
            //reportWarning(ise);
            System.out.println(ise.getMessage());
            // Unable to connect to a running server, so proceed without it

            JdrReportService reportService = new JdrReportService();

            JdrReport response = null;
            try {
                response = reportService.standaloneCollect(host, String.valueOf(port));
                System.out.println("JDR started: " + response.getStartTime().toString());
                System.out.println("JDR ended: " + response.getEndTime().toString());
                System.out.println("JDR location: " + response.getLocation());
            } catch (OperationFailedException e) {
                System.out.println("Failed to complete the JDR report: " + e.getMessage());
            }
        } finally {
            if(cli != null) {
                try {
                    cli.disconnect();
                } catch(Exception e) {
                }
            }
        }

       System.exit(0);
    }

    /* private static void reportWarning(final Exception e) {
        Throwable causedBy = e;
        while(causedBy.getCausedBy() != null)
            causedBy = causedBy.getCausedBy();
        System.out.println(causedBy.getMessage());
    }*/

    private static void reportFailure(final ModelNode node) {
        if (!node.get(ClientConstants.OUTCOME).asString().equals(ClientConstants.SUCCESS)) {
            final String msg;
            if (node.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
                if (node.hasDefined(ClientConstants.OP)) {
                    msg = String.format("Operation '%s' at address '%s' failed: %s", node.get(ClientConstants.OP), node.get(ClientConstants.OP_ADDR), node.get(ClientConstants.FAILURE_DESCRIPTION));
                } else {
                    msg = String.format("Operation failed: %s", node.get(ClientConstants.FAILURE_DESCRIPTION));
                }
            } else {
                msg = String.format("Operation failed: %s", node);
            }
            throw new RuntimeException(msg);
        }
    }
}
