/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.registry;

import org.togglz.core.Feature;
import org.togglz.core.annotation.EnabledByDefault;
import org.togglz.core.annotation.Label;
import org.togglz.core.context.FeatureContext;

/**
 * Features in the runtime
 */
public enum RuntimeTogglzFeatures implements Feature {

  @EnabledByDefault
  @Label("Ubi sunt moments with The Chiqui around the Congreso neighborhood")
  THE_CHIQUI_UBI_SUNT_CONGRESO_MOMENT,

  ENABLE_PROFILING_SERVICE;

  public boolean isActive() {
    return FeatureContext.getFeatureManager().isActive(this);
  }
}
