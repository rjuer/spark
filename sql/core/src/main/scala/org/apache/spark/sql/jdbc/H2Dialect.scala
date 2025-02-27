/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.jdbc

import java.sql.SQLException
import java.util.Locale

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.analysis.{NoSuchNamespaceException, NoSuchTableException, TableAlreadyExistsException}
import org.apache.spark.sql.connector.expressions.aggregate.{AggregateFunc, GeneralAggregateFunc}

private object H2Dialect extends JdbcDialect {
  override def canHandle(url: String): Boolean =
    url.toLowerCase(Locale.ROOT).startsWith("jdbc:h2")

  override def compileAggregate(aggFunction: AggregateFunc): Option[String] = {
    super.compileAggregate(aggFunction).orElse(
      aggFunction match {
        case f: GeneralAggregateFunc if f.name() == "VAR_POP" =>
          assert(f.inputs().length == 1)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"VAR_POP($distinct${f.inputs().head})")
        case f: GeneralAggregateFunc if f.name() == "VAR_SAMP" =>
          assert(f.inputs().length == 1)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"VAR_SAMP($distinct${f.inputs().head})")
        case f: GeneralAggregateFunc if f.name() == "STDDEV_POP" =>
          assert(f.inputs().length == 1)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"STDDEV_POP($distinct${f.inputs().head})")
        case f: GeneralAggregateFunc if f.name() == "STDDEV_SAMP" =>
          assert(f.inputs().length == 1)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"STDDEV_SAMP($distinct${f.inputs().head})")
        case f: GeneralAggregateFunc if f.name() == "COVAR_POP" =>
          assert(f.inputs().length == 2)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"COVAR_POP($distinct${f.inputs().head}, ${f.inputs().last})")
        case f: GeneralAggregateFunc if f.name() == "COVAR_SAMP" =>
          assert(f.inputs().length == 2)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"COVAR_SAMP($distinct${f.inputs().head}, ${f.inputs().last})")
        case f: GeneralAggregateFunc if f.name() == "CORR" =>
          assert(f.inputs().length == 2)
          val distinct = if (f.isDistinct) "DISTINCT " else ""
          Some(s"CORR($distinct${f.inputs().head}, ${f.inputs().last})")
        case _ => None
      }
    )
  }

  override def classifyException(message: String, e: Throwable): AnalysisException = {
    if (e.isInstanceOf[SQLException]) {
      // Error codes are from https://www.h2database.com/javadoc/org/h2/api/ErrorCode.html
      e.asInstanceOf[SQLException].getErrorCode match {
        // TABLE_OR_VIEW_ALREADY_EXISTS_1
        case 42101 =>
          throw new TableAlreadyExistsException(message, cause = Some(e))
        // TABLE_OR_VIEW_NOT_FOUND_1
        case 42102 =>
          throw new NoSuchTableException(message, cause = Some(e))
        // SCHEMA_NOT_FOUND_1
        case 90079 =>
          throw new NoSuchNamespaceException(message, cause = Some(e))
        case _ =>
      }
    }
    super.classifyException(message, e)
  }
}
