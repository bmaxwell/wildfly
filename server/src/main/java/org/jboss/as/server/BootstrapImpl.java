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

package org.jboss.as.server;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.AsyncFutureTask;
import org.jboss.threads.JBossExecutors;

/**
 * The bootstrap implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class BootstrapImpl implements Bootstrap {
    private static final int MAX_THREADS = ServerEnvironment.getBootstrapMaxThreads();
    private final ServiceContainer container = ServiceContainer.Factory.create("jboss-as", MAX_THREADS, 30, TimeUnit.SECONDS);

    @Override
    public AsyncFuture<ServiceContainer> bootstrap(final Configuration configuration, final List<ServiceActivator> extraServices) {

        assert configuration != null : "configuration is null";

        // AS7-6381 set this property so we can get it out of the launch scripts
        String resolverWarning = SecurityActions.getSystemProperty("org.jboss.resolver.warning");
        if (resolverWarning == null) {
            SecurityActions.setSystemProperty("org.jboss.resolver.warning", "true");
        }

        final ModuleLoader moduleLoader = configuration.getModuleLoader();
        final Bootstrap.ConfigurationPersisterFactory configurationPersisterFactory = configuration.getConfigurationPersisterFactory();
        assert configurationPersisterFactory != null : "configurationPersisterFactory is null";

        try {
            Module.registerURLStreamHandlerFactoryModule(moduleLoader.loadModule(ModuleIdentifier.create("org.jboss.vfs")));
        } catch (ModuleLoadException e) {
            throw ServerMessages.MESSAGES.vfsNotAvailable();
        }
        final FutureServiceContainer future = new FutureServiceContainer(container);
        final ServiceTarget tracker = container.subTarget();
        final ControlledProcessState processState = new ControlledProcessState(configuration.getServerEnvironment().isStandalone());
        ControlledProcessStateService.addService(tracker, processState);
        final Service<?> applicationServerService = new ApplicationServerService(extraServices, configuration, processState);
        tracker.addService(Services.JBOSS_AS, applicationServerService)
            .install();
        final ServiceController<?> rootService = container.getRequiredService(Services.JBOSS_AS);

        final StabilityMonitor monitor = new StabilityMonitor();
        monitor.addController(rootService);
        try {
            monitor.awaitStability();
        } catch (final InterruptedException e) {
            future.failed(e);
        } finally {
            monitor.removeController(rootService);
        }

        switch (rootService.getState()) {
            case UP: {
                final ServiceController<?> controllerServiceController = rootService.getServiceContainer().getRequiredService(Services.JBOSS_SERVER_CONTROLLER);
                monitor.addController(controllerServiceController);
                try {
                    monitor.awaitStability();
                } catch (final InterruptedException e) {
                    future.failed(e);
                } finally {
                    monitor.removeController(controllerServiceController);
                }

                switch (controllerServiceController.getState()) {
                    case UP: {
                        future.done();
                        break;
                    }
                    case START_FAILED: {
                        future.failed(controllerServiceController.getStartException());
                        break;
                    }
                    case REMOVED: {
                        future.failed(ServerMessages.MESSAGES.serverControllerServiceRemoved());
                        break;
                    }
                }

                break;
            }
            case START_FAILED: {
                future.failed(rootService.getStartException());
                break;
            }
            case REMOVED: {
                future.failed(ServerMessages.MESSAGES.rootServiceRemoved());
                break;
            }
        }
        return future;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AsyncFuture<ServiceContainer> startup(Configuration configuration, List<ServiceActivator> extraServices) {
        try {
            ServiceContainer container = bootstrap(configuration, extraServices).get();
            ServiceController<?> controller = container.getRequiredService(Services.JBOSS_AS);
            return (AsyncFuture<ServiceContainer>) controller.getValue();
        } catch (Exception ex) {
            throw ServerMessages.MESSAGES.cannotStartServer(ex);
        }
    }

    static class FutureServiceContainer extends AsyncFutureTask<ServiceContainer> {
        private final ServiceContainer container;

        FutureServiceContainer(final ServiceContainer container) {
            super(JBossExecutors.directExecutor());
            this.container = container;
        }

        @Override
        public void asyncCancel(final boolean interruptionDesired) {
            container.shutdown();
            container.addTerminateListener(new ServiceContainer.TerminateListener() {
                @Override
                public void handleTermination(final Info info) {
                    setCancelled();
                }
            });
        }

        void done() {
            setResult(container);
        }

        void failed(Throwable t) {
            setFailed(t);
        }
    }
}
