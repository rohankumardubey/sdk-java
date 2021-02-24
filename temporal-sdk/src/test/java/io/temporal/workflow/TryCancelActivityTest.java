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

package io.temporal.workflow;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestWorkflowRule;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TryCancelActivityTest {

  private final WorkflowTest.TestActivitiesImpl activitiesImpl =
      new WorkflowTest.TestActivitiesImpl(null);

  @Rule
  public TestWorkflowRule testWorkflowRule =
      TestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestTryCancelActivity.class)
          .setActivityImplementations(activitiesImpl)
          .setUseExternalService(Boolean.parseBoolean(System.getenv("USE_DOCKER_SERVICE")))
          .setTarget(System.getenv("TEMPORAL_SERVICE_ADDRESS"))
          .build();

  @Test
  public void testTryCancelActivity() {
    WorkflowTest.TestWorkflow1 client =
        testWorkflowRule
            .getWorkflowClient()
            .newWorkflowStub(
                WorkflowTest.TestWorkflow1.class,
                WorkflowTest.newWorkflowOptionsBuilder(testWorkflowRule.getTaskQueue()).build());
    WorkflowClient.start(client::execute, testWorkflowRule.getTaskQueue());
    testWorkflowRule
        .getTestEnvironment()
        .sleep(Duration.ofMillis(500)); // To let activityWithDelay start.
    WorkflowStub stub = WorkflowStub.fromTyped(client);
    WorkflowTest.waitForOKQuery(stub);
    stub.cancel();
    long start = testWorkflowRule.getTestEnvironment().currentTimeMillis();
    try {
      stub.getResult(String.class);
      Assert.fail("unreachable");
    } catch (WorkflowFailedException e) {
      Assert.assertTrue(e.getCause() instanceof CanceledFailure);
    }
    long elapsed = testWorkflowRule.getTestEnvironment().currentTimeMillis() - start;
    Assert.assertTrue(String.valueOf(elapsed), elapsed < 500);
    activitiesImpl.assertInvocations("activityWithDelay");
  }

  public static class TestTryCancelActivity implements WorkflowTest.TestWorkflow1 {

    @Override
    public String execute(String taskQueue) {
      WorkflowTest.TestActivities testActivities =
          Workflow.newActivityStub(
              WorkflowTest.TestActivities.class,
              ActivityOptions.newBuilder(WorkflowTest.newActivityOptions1(taskQueue))
                  .setHeartbeatTimeout(Duration.ofSeconds(1))
                  .setCancellationType(ActivityCancellationType.TRY_CANCEL)
                  .build());
      testActivities.activityWithDelay(100000, true);
      return "foo";
    }
  }
}