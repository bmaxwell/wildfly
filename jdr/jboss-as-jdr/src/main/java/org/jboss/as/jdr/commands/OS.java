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

import java.io.File;
import java.io.FileFilter;
//import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Scanner;

public class OS extends JdrCommand {

    private static String OUTPUT_FILE = "os.txt";
    private static final String NEWLINE = System.getProperty("line.separator");

/*
    private static final FilenameFilter releaseFilesFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String fileName) {
            if(fileName.endsWith("-release") || fileName.endsWith("version"))
                return true;
            return false;
        }
    };
*/

    private static final FileFilter releaseFilesFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            if(file.isFile() && (file.getName().endsWith("-release") || file.getName().endsWith("version")))
                return true;
            return false;
        }
    };


    @Override
    public void execute() throws Exception {
        StringBuilder buffer = new StringBuilder();

        // look for /etc/*-release files
        System.out.println("=================================================");
        File etc = new File("/etc");
        if(!etc.exists()) {
            buffer.append("/etc does not exist" + NEWLINE);
        } else if(!etc.isDirectory()) {
            buffer.append("/etc is not a directory" + NEWLINE);
        } else {
            File[] releaseFiles = etc.listFiles(releaseFilesFilter);
            if(releaseFiles.length == 0) {
                buffer.append("/etc does not contain any *-release files" + NEWLINE);
            } else {
                for(File releaseFile : releaseFiles) {
                    if(!releaseFile.canRead()) {
                        buffer.append(releaseFile.getAbsolutePath() + " exists but is not readable" + NEWLINE);
                    } else {
                        try {
                            buffer.append("Found release file: " + releaseFile.getAbsolutePath() + NEWLINE);
                            buffer.append(readFile(releaseFile));
                        } catch(IOException e) {
                            buffer.append("Failed to read: " + releaseFile.getAbsolutePath());
                            StringWriter sw = new StringWriter();
                            e.printStackTrace(new PrintWriter(sw));
                            buffer.append(sw.toString());
                        }
                    }
                }
            }
        }
        this.env.getZip().add(buffer.toString(), OUTPUT_FILE);
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder((int)file.length());
        Scanner scanner = new Scanner(file);
        try {
            while(scanner.hasNextLine()) {
                sb.append(scanner.nextLine() + NEWLINE);
            }
            return sb.toString();
        } finally {
            scanner.close();
        }
    }
}
