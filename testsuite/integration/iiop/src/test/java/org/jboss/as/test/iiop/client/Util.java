/*
* JBoss, Home of Professional Open Source.
* Copyright 2013, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.test.iiop.client;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.as.arquillian.container.NetworkUtils;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.ORBPackage.InvalidName;

import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.ats.jts.OTSManager;
import com.arjuna.orbportability.ORB;
import com.arjuna.orbportability.RootOA;

public class Util {
	public static final String HOST = NetworkUtils.formatPossibleIpv6Address(System.getProperty("node0", "localhost"));
	private static ORB orb = null;
	
	// Recovery manager is needed till the end of orb usage
	private static ExecutorService recoveryManagerPool;
	
	public static void presetOrb() throws InvalidName, SystemException {
		 System.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
		 System.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
	    
		 // Recovery manager has to be started on client when we want recovery 
		 // and we start the transaction on client
		 recoveryManagerPool = Executors.newFixedThreadPool(1);
		 recoveryManagerPool.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                RecoveryManager.main(new String[] {"-test"});
                return "Running recovery manager";
            }
         });
		 
		 orb = com.arjuna.orbportability.ORB.getInstance("ClientSide");
	     RootOA oa = com.arjuna.orbportability.OA.getRootOA(orb);
	     orb.initORB(new String[] {}, null);
	     oa.initOA();
	     ORBManager.setORB(orb);
	     ORBManager.setPOA(oa);
	}
	
	public static void tearDownOrb() {
	    if(orb != null) {
	        orb.shutdown();
	    }
	    recoveryManagerPool.shutdown();
	}
	
    public static void startCorbaTx() throws Throwable {            
        OTSManager.get_current().begin();
    }
    
    public static void commitCorbaTx() throws Throwable {
    	OTSManager.get_current().commit(true);
  }
    
    public static void rollbackCorbaTx() throws Throwable {
    	OTSManager.get_current().rollback();
    }
    
    public static InitialContext getContext() throws NamingException {
        // this is needed to get the iiop call successful
    	System.setProperty("com.sun.CORBA.ORBUseDynamicStub", "true");
    	final Properties prope = new Properties();
    	
    	prope.put(Context.PROVIDER_URL, "corbaloc::" + HOST + ":3528/NameService");
    	// prope.put(Context.PROVIDER_URL, "corbaloc::" + HOST + ":3528/JBoss/Naming/root");
    	
    	prope.setProperty(Context.URL_PKG_PREFIXES, "org.jboss.iiop.naming:org.jboss.naming.client");
        prope.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.cosnaming.CNCtxFactory");
        prope.put(Context.OBJECT_FACTORIES, "org.jboss.tm.iiop.client.IIOPClientUserTransactionObjectFactory");
        
        return new InitialContext(prope);
    }
}