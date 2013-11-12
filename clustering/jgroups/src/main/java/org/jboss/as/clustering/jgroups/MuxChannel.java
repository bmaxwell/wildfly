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
package org.jboss.as.clustering.jgroups;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.UpHandler;
import org.jgroups.blocks.mux.MuxUpHandler;
import org.jgroups.blocks.mux.Muxer;
import org.jgroups.conf.ProtocolStackConfigurator;

/**
 * A JGroups channel that uses a MuxUpHandler by default.
 * @author Paul Ferraro
 */
public class MuxChannel extends JChannel {
    public MuxChannel(ProtocolStackConfigurator configurator) throws Exception {
        super(configurator);
        this.setUpHandler(new MuxUpHandler());
    }

    @Override
    public void setReceiver(Receiver receiver) {
        super.setReceiver(receiver);
        // If we're using a receiver, we're not interested in using an up handler
        if (receiver != null) {
            super.setUpHandler(null);
        }
    }

    @Override
    public void setUpHandler(UpHandler handler) {
        UpHandler existingHandler = this.getUpHandler();
        if ((existingHandler != null) && (existingHandler instanceof Muxer)) {
            @SuppressWarnings("unchecked")
            Muxer<UpHandler> muxer = (Muxer<UpHandler>) existingHandler;
            muxer.setDefaultHandler(handler);
        } else {
            super.setUpHandler(handler);
        }
    }

    /**
     * Hack to workaround ISPN-3697.
     * {@link #close} will just remove any registered up_handler
     * Channel will actually be closed via {@link #destroy()} called when the channel service is stopped..
     */
    @Override
    public void close() {
        this.setUpHandler(null);
    }

    /**
     * Hack to workaround ISPN-3697.
     * Introduce new method that effectively closes the channel.
     */
    public void destroy() {
        super.close();
    }
}