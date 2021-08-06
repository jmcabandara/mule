/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core;

import org.mule.runtime.api.config.*;
import org.mule.runtime.core.api.config.*;
import org.mule.runtime.core.internal.registry.*;
import org.togglz.core.Feature;
import org.togglz.core.metadata.FeatureMetaData;
import org.togglz.core.spi.FeatureProvider;

import java.util.*;

public class MuleFeatureProvider implements FeatureProvider {

  private static Map<org.mule.runtime.api.config.Feature, Feature> features = new HashMap<>();

  private static boolean initialised = false;

  public static Feature getTogglzFeature(org.mule.runtime.api.config.Feature feature) {
    if (!initialised) {
      initFeatures();
    }
    return features.get(feature);
  }

  @Override
  public Set<Feature> getFeatures() {
    if (!initialised) {
      initFeatures();
    }
    return new HashSet(features.values());
  }

  private static void initFeatures() {
    for (org.mule.runtime.api.config.Feature feature : FeatureFlaggingRegistry.getInstance().getFeatureFlagConfigurations()
        .keySet()) {
      features.put(feature, new MuleRuntimeTogglzFeature(feature));
    }

    Set<Feature> featureSet = new HashSet<>(features.values());
    features
        .put(MuleRuntimeFeature.THE_CHIQUI_UBI_SUNT_CONGRESO_MOMENT, RuntimeTogglzFeatures.THE_CHIQUI_UBI_SUNT_CONGRESO_MOMENT);
    initialised = true;
  }

  @Override
  public FeatureMetaData getMetaData(Feature feature) {
    if (feature instanceof MuleRuntimeTogglzFeature) {
      ((MuleRuntimeTogglzFeature) feature).getMetadata();
    }
    return null;
  }
}
