/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core;

import org.mule.runtime.api.config.*;
import org.togglz.core.Feature;
import org.togglz.core.metadata.FeatureGroup;
import org.togglz.core.metadata.FeatureMetaData;
import org.togglz.core.repository.FeatureState;

import java.util.Map;
import java.util.Set;

public class MuleRuntimeTogglzFeature implements Feature {

  private final org.mule.runtime.api.config.Feature feature;
  private final FeatureMetaData metadata;

  public MuleRuntimeTogglzFeature(org.mule.runtime.api.config.Feature feature) {
    this.feature = feature;
    this.metadata = createMetadata(feature);
  }

  private FeatureMetaData createMetadata(org.mule.runtime.api.config.Feature feature) {
    return new FeatureMetaData() {

      @Override
      public String getLabel() {
        return feature.toString();
      }

      @Override
      public FeatureState getDefaultFeatureState() {
        return new FeatureState(MuleRuntimeTogglzFeature.this);
      }

      @Override
      public Set<FeatureGroup> getGroups() {
        return null;
      }

      @Override
      public Map<String, String> getAttributes() {
        return null;
      }
    };
  }

  @Override
  public String name() {
    return feature.toString();
  }

  public FeatureMetaData getMetadata() {
    return metadata;
  }
}
