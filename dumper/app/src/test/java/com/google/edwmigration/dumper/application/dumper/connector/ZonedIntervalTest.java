/*
 * Copyright 2022-2023 Google LLC
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
package com.google.edwmigration.dumper.application.dumper.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ZonedIntervalTest {

  private final ZonedDateTime SEVEN_DAYS_AGO = getTimeSubtractingDays(7);
  private final ZonedDateTime FIVE_DAYS_AGO = getTimeSubtractingDays(5);
  private final ZonedDateTime THREE_DAYS_AGO = getTimeSubtractingDays(3);
  private final ZonedDateTime ONE_DAY_AGO = getTimeSubtractingDays(1);

  @Test
  public void testInclusiveEndIsSmallerThanExclusiveEnd() {
    // Arrange
    ZonedInterval interval = new ZonedInterval(SEVEN_DAYS_AGO, FIVE_DAYS_AGO);

    // Assert
    assertTrue(
        "Inclusive time should be smaller",
        interval.getEndInclusive().isBefore(interval.getEndExclusive()));
  }

  @Test
  public void testDisjointInterval() {
    // Arrange
    ZonedInterval earliestInterval = new ZonedInterval(SEVEN_DAYS_AGO, FIVE_DAYS_AGO);
    ZonedInterval latestInterval = new ZonedInterval(THREE_DAYS_AGO, ONE_DAY_AGO);

    // Act
    ZonedInterval obtainedInterval = earliestInterval.span(latestInterval);

    // Assert
    assertEquals("Wrong start time", SEVEN_DAYS_AGO, obtainedInterval.getStart());
    assertEquals("Wrong end time", ONE_DAY_AGO, obtainedInterval.getEndExclusive());
  }

  @Test
  public void testOverlappingInterval() {
    // Arrange
    ZonedInterval earliestInterval = new ZonedInterval(SEVEN_DAYS_AGO, THREE_DAYS_AGO);
    ZonedInterval latestInterval = new ZonedInterval(FIVE_DAYS_AGO, ONE_DAY_AGO);

    // Act
    ZonedInterval obtainedInterval = earliestInterval.span(latestInterval);

    // Assert
    assertEquals("Wrong start time", SEVEN_DAYS_AGO, obtainedInterval.getStart());
    assertEquals("Wrong end time", ONE_DAY_AGO, obtainedInterval.getEndExclusive());
  }

  @Test
  public void testSubsetInterval() {
    // Arrange
    ZonedInterval earliestInterval = new ZonedInterval(SEVEN_DAYS_AGO, ONE_DAY_AGO);
    ZonedInterval latestInterval = new ZonedInterval(FIVE_DAYS_AGO, THREE_DAYS_AGO);

    // Act
    ZonedInterval obtainedInterval = earliestInterval.span(latestInterval);

    // Assert
    assertEquals("Wrong start time", SEVEN_DAYS_AGO, obtainedInterval.getStart());
    assertEquals("Wrong end time", ONE_DAY_AGO, obtainedInterval.getEndExclusive());
  }

  @Test
  public void testIntervalCreationValidation() {
    // Arrange & Act
    IllegalArgumentException exception =
        Assert.assertThrows(
            IllegalArgumentException.class, () -> new ZonedInterval(THREE_DAYS_AGO, FIVE_DAYS_AGO));

    // Assert
    assertEquals("Start date must be before end date", exception.getMessage());
  }

  private static ZonedDateTime getTimeSubtractingDays(int days) {
    ZonedDateTime nowAtUTC = ZonedDateTime.now(ZoneOffset.UTC);
    return nowAtUTC.minusDays(days).truncatedTo(ChronoUnit.HOURS);
  }
}
