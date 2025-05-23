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
package com.google.edwmigration.dumper.application.dumper.connector.redshift;

import com.google.common.base.Joiner;
import com.google.edwmigration.dumper.application.dumper.ConnectorArguments;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsArgumentDatabaseForConnection;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsArgumentDriver;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsArgumentHostUnlessUrl;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsArgumentJDBCUri;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsArgumentUser;
import com.google.edwmigration.dumper.application.dumper.annotations.RespectsInput;
import com.google.edwmigration.dumper.application.dumper.connector.AbstractJdbcConnector;
import com.google.edwmigration.dumper.application.dumper.handle.Handle;
import com.google.edwmigration.dumper.application.dumper.handle.JdbcHandle;
import java.sql.Driver;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** */
@RespectsArgumentDriver
@RespectsArgumentHostUnlessUrl
@RespectsArgumentDatabaseForConnection
@RespectsArgumentUser
@RespectsInput(
    order = 400,
    arg = ConnectorArguments.OPT_PASSWORD,
    description = "The password for the database connection.",
    required = "If present, forces password authentication")
@RespectsInput(
    order = 450,
    arg = ConnectorArguments.OPT_IAM_PROFILE,
    description = "The IAM Profile to use for authentication.",
    required = "If present, performs profile-based IAM authentication")
@RespectsInput(
    order = 455,
    arg = ConnectorArguments.OPT_IAM_ACCESSKEYID,
    description = "The IAM Access Key ID to use for authentication.",
    required = "If present, performs explicit IAM authentication")
@RespectsInput(
    order = 456,
    arg = ConnectorArguments.OPT_IAM_SECRETACCESSKEY,
    description = "The IAM Secret Access Key to use for authentication.",
    required = "If present, performs explicit IAM authentication")
@RespectsArgumentJDBCUri
public abstract class AbstractRedshiftConnector extends AbstractJdbcConnector {

  private static final Logger logger = LoggerFactory.getLogger(AbstractRedshiftConnector.class);

  protected static final DateTimeFormatter SQL_FORMAT =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

  @Nonnull
  protected static CharSequence newWhereClause(List<String> clauseList, String... clauseArray) {
    StringBuilder buf = new StringBuilder();
    Joiner.on(" AND ").appendTo(buf, clauseList);
    for (String clause : clauseArray) {
      if (buf.length() > 0) buf.append(" AND ");
      buf.append(clause);
    }
    return buf.toString();
  }

  /* pp */ AbstractRedshiftConnector(String name) {
    super(name);
  }

  /* Drivers:
   *    1. Redshift sdk-included jar given on --driver command line
   *    3. Redshift no-sdk jar given on --driver command line
   *    2. Postgres given on --driver command line
   *    3. built in postgres
   *
   *  REDSHIFT DRIVER : USERNAME/PASSWORD AUTH
   *
   *  jdbc:redshift://[Host]:[Port]/[Schema];[Property1]=[Value];[Property2]=[Value];...
   *  Using User Name and Password Only
   *       UID = .. + PWD = ...
   *  Using SSL without Identity Verification
   *        UID + PWD + SSL=TRUE + SSLFactory =com.amazon.redshift.ssl.NonValidatingFactory.
   *  Using One-Way SSL Authentication
   *      -- requires  java trust store .. ignore for now
   *
   *
   *  REDSHIFT DRIVER : IAM Authentication
   *          jdbc:redshift:iam://[host]:[port]/[db]
   *          jdbc:redshift:iam://[cluster-id]:[region]/[db]
   *
   * 1. profile file : ~/.aws/credentials or AWS_CREDENTIAL_PROFILES_FILE
   *        [Profile=<Profile-Name>]
   *
   * 2. AccessKeyID= + SecretAccessKey=
   *
   *
   * REDSHIFT DRIVER: more auth
   *   AD FS  / Azure AD / Okta / PingFederate
   *    -- ignore for now ; astute user can use url
   *
   * MORE HDBC OPTIONS:
   *  DbUser= + AllowDBUserOverride=1 --> force username different SAML ?
   *  AutoCreate=true .. auto create SAML inspiredor DbUser user.
   *  ClusterID -- name of redshift cluster
   *  AuthMech=PREFER .. first use SSL, then without
   *
   * https://s3.amazonaws.com/redshift-downloads/drivers/jdbc/1.2.43.1067/Amazon+Redshift+JDBC+Driver+Install+Guide.pdf
   */

  @Override
  @Nonnull
  public Handle open(@Nonnull ConnectorArguments arguments) throws Exception {

    Driver driver =
        newDriver(
            arguments.getDriverPaths(), "com.amazon.redshift.jdbc.Driver", "org.postgresql.Driver");

    String url = arguments.getUri();
    Optional<String> password = arguments.getPasswordIfFlagProvided();
    if (url == null) {
      // driver can be pg or rs ( including rs-iam )
      // options can be username/passowrd , or iam secrets.

      boolean isDriverRedshift = driver.acceptsURL("jdbc:redshift:iam://host/db");
      // there may be no --iam options, in which case default ~/.aws/credentials to be used.
      // so this is the only reliable check
      boolean isAuthenticationPassword = password.isPresent();

      if (!isDriverRedshift) {
        if (!isAuthenticationPassword)
          throw new IllegalArgumentException(
              "The use of IAM authentication also requires the use of a Redshift-specific JDBC driver. Please use --"
                  + ConnectorArguments.OPT_DRIVER
                  + " to specify the path to the Redshift JDBC JAR, or use password authentication.");
        url = RedshiftUrlUtil.makeJdbcUrlPostgresql(arguments);
      } else if (isAuthenticationPassword) {
        url = RedshiftUrlUtil.makeJdbcUrlRedshiftSimple(arguments);
      } else {
        url = RedshiftUrlUtil.makeJdbcUrlRedshiftIAM(arguments);
      }
    }

    // Debugging:
    //   DSILogLevel=0..6;LogPath=C:\temp
    //   LogLevel 0/1
    logger.trace("URI is " + url);
    DataSource dataSource =
        new SimpleDriverDataSource(driver, url, arguments.getUser(), password.orElse(null));

    return JdbcHandle.newPooledJdbcHandle(dataSource, arguments.getThreadPoolSize());
  }

  private static void logDriverInfo(@Nonnull Driver driver) throws SQLException {
    logger.debug("DRIVER IS " + driver.getClass().getCanonicalName());
    logger.debug("DRIVER CAN RS " + driver.acceptsURL("jdbc:redshift://host/db"));
    logger.debug("DRIVER CAN IAM " + driver.acceptsURL("jdbc:redshift:iam://host/db"));
    logger.debug("DRIVER CAN PG " + driver.acceptsURL("jdbc:postgresql://host/db"));
  }
}
