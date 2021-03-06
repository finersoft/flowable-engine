/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti5.engine.test.pvm.activities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti5.engine.impl.pvm.PvmActivity;
import org.activiti5.engine.impl.pvm.PvmTransition;
import org.activiti5.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti5.engine.impl.pvm.delegate.CompositeActivityBehavior;
import org.activiti5.engine.impl.pvm.process.ActivityImpl;
import org.activiti5.engine.impl.pvm.runtime.InterpretableExecution;


/**
 * @author Daniel Meyer
 */
public class EventScopeCreatingSubprocess implements CompositeActivityBehavior {

  public void execute(DelegateExecution execution) {
    ActivityExecution activityExecution = (ActivityExecution) execution;
    List<PvmActivity> startActivities = new ArrayList<PvmActivity>();
    for (PvmActivity activity: activityExecution.getActivity().getActivities()) {
      if (activity.getIncomingTransitions().isEmpty()) {
        startActivities.add(activity);
      }
    }
    
    for (PvmActivity startActivity: startActivities) {
      activityExecution.executeActivity(startActivity);
    }
  }

  /*
   * Incoming execution is transformed into an event scope, 
   * new, non-concurrent execution leaves activity 
   */
  @SuppressWarnings("unchecked")
  public void lastExecutionEnded(ActivityExecution execution) {
    
    ActivityExecution outgoingExecution = execution.getParent().createExecution();
    outgoingExecution.setConcurrent(false);
    ((InterpretableExecution)outgoingExecution).setActivity((ActivityImpl) execution.getActivity());
        
    // eventscope execution
    execution.setConcurrent(false);
    execution.setActive(false);
    ((InterpretableExecution)execution).setEventScope(true);
        
    List<PvmTransition> outgoingTransitions = execution.getActivity().getOutgoingTransitions();
    if(outgoingTransitions.isEmpty()) {
      outgoingExecution.end();
    }else {
      outgoingExecution.takeAll(outgoingTransitions, Collections.EMPTY_LIST);
    }
  }


  // used by timers
  @SuppressWarnings("unchecked")
  public void timerFires(ActivityExecution execution, String signalName, Object signalData) throws Exception {
    PvmActivity timerActivity = execution.getActivity();
    boolean isInterrupting = (Boolean) timerActivity.getProperty("isInterrupting");
    List<ActivityExecution> recyclableExecutions;
    if (isInterrupting) {
      recyclableExecutions = removeAllExecutions(execution);
    } else {
      recyclableExecutions = Collections.EMPTY_LIST;
    }
    execution.takeAll(timerActivity.getOutgoingTransitions(), recyclableExecutions);
  }

  private List<ActivityExecution> removeAllExecutions(ActivityExecution execution) {
    return null;
  }

}
