/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.client

import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.common.converter.KotlinObjectMapperFactory
import io.temporal.testing.TestWorkflowRule
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class WorkflowClientExtTest {

  @Rule
  @JvmField
  val testWorkflowRule = TestWorkflowRule.newBuilder()
    .setWorkflowTypes(TestWorkflowImpl::class.java)
    .setWorkflowClientOptions(
      WorkflowClientOptions {
        setDataConverter(DefaultDataConverter(JacksonJsonPayloadConverter(KotlinObjectMapperFactory.new())))
      }
    )
    .build()

  @Test(timeout = 5000)
  fun `signalWithStart extension should work the same as the original method`() {
    val workflowClient = testWorkflowRule.workflowClient

    val typedStub = workflowClient.newWorkflowStub<TestWorkflow> {
      setWorkflowId("1")
      setTaskQueue(testWorkflowRule.taskQueue)
    }
    workflowClient.signalWithStart {
      add { typedStub.start(Duration.ofHours(10)) }
      add { typedStub.collectString("v1") }
    }

    testWorkflowRule.testEnvironment.sleep(Duration.ofHours(1))

    val typedStub2 = workflowClient.newWorkflowStub<TestWorkflow> {
      setWorkflowId("1")
      setTaskQueue(testWorkflowRule.taskQueue)
    }
    workflowClient.signalWithStart {
      add { typedStub2.start(Duration.ofHours(20)) }
      add { typedStub2.collectString("v2") }
    }

    val untypedStub = WorkflowStub.fromTyped(typedStub)

    Assert.assertEquals(listOf("v1", "v2"), untypedStub.getResult<List<String>>())
  }

  @WorkflowInterface
  interface TestWorkflow {
    @WorkflowMethod
    fun start(waitDuration: Duration): List<String>

    @SignalMethod
    fun collectString(arg: String)
  }

  class TestWorkflowImpl : TestWorkflow {
    private val strings = mutableListOf<String>()

    override fun start(waitDuration: Duration): List<String> {
      Workflow.sleep(waitDuration)
      return strings
    }

    override fun collectString(arg: String) {
      strings += arg
    }
  }
}
