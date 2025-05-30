/*
 * Copyright 2022-2025 Google LLC
 * Copyright 2013-2021 CompilerWorks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.edwmigration.dumper.test;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author shevek */
public class SystemPropertyValue implements BooleanSupplier {

  @SuppressWarnings("UnusedVariable")
  private static final Logger logger = LoggerFactory.getLogger(SystemPropertyValue.class);

  public static boolean get(@Nonnull String name) {
    return AccessController.doPrivileged(
        new PrivilegedAction<Boolean>() {
          @Override
          public Boolean run() {
            boolean out = Boolean.getBoolean(name);
            // logger.debug("SystemPropertyValue {} -> {}", name, out);
            return out;
          }
        });
  }

  private final boolean value;

  public SystemPropertyValue(@Nonnull String name) {
    this.value = get(name);
  }

  @Override
  public boolean getAsBoolean() {
    return value;
  }
}
