/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core;

import org.togglz.core.manager.FeatureManager;
import org.togglz.core.manager.FeatureManagerBuilder;
import org.togglz.core.repository.mem.InMemoryStateRepository;
import org.togglz.core.spi.FeatureManagerProvider;
import org.togglz.core.user.NoOpUserProvider;

public class SingletonFeatureManagerProvider implements FeatureManagerProvider {

  private static FeatureManager featureManager;

  private static JettyServer jettyServer;

  public SingletonFeatureManagerProvider() {
    jettyServer = new JettyServer();
    try {
      jettyServer.start();
    } catch (Exception e) {

    }

  }

  @Override
  public int priority() {
    return 30;
  }

  @Override
  public synchronized FeatureManager getFeatureManager() {

    if (featureManager == null) {
      featureManager = FeatureManagerBuilder.begin()
          .featureProvider(new MuleFeatureProvider())
          .stateRepository(new InMemoryStateRepository())
          .userProvider(new NoOpUserProvider())
          .build();
    }

    return featureManager;

  }

}
