/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.registry;

import org.mule.runtime.api.config.*;
import org.mule.runtime.api.profiling.*;
import org.mule.runtime.api.profiling.type.*;
import org.mule.runtime.core.*;
import org.mule.runtime.core.internal.profiling.*;
import org.mule.runtime.core.internal.profiling.producer.*;
import org.togglz.core.context.FeatureContext;

import java.util.HashMap;
import java.util.Map;

import static org.mule.runtime.api.profiling.type.RuntimeProfilingEventTypes.*;

public class TogglzFlaggingService implements FeatureFlaggingService {

  @Override
  public boolean isEnabled(Feature feature) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(TransientRegistry.class.getClassLoader());

      return FeatureContext.getFeatureManager().isActive(MuleFeatureProvider.getTogglzFeature(feature));
    } finally {
      Thread.currentThread().setContextClassLoader(cl);

    }
  }
}
