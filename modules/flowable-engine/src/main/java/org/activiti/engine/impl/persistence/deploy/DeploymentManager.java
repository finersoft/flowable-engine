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

package org.activiti.engine.impl.persistence.deploy;

import java.util.List;
import java.util.Map;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.app.AppModel;
import org.activiti.engine.compatibility.Activiti5CompatibilityHandler;
import org.activiti.engine.delegate.event.ActivitiEngineEventType;
import org.activiti.engine.delegate.event.ActivitiEventDispatcher;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.impl.ProcessDefinitionQueryImpl;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.DeploymentEntity;
import org.activiti.engine.impl.persistence.entity.DeploymentEntityManager;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntityManager;
import org.activiti.engine.impl.util.Activiti5Util;
import org.activiti.engine.repository.ProcessDefinition;

/**
 * @author Tom Baeyens
 * @author Falko Menge
 * @author Joram Barrez
 */
public class DeploymentManager {

  protected DeploymentCache<ProcessDefinitionCacheEntry> processDefinitionCache;
  protected ProcessDefinitionInfoCache processDefinitionInfoCache;
  protected DeploymentCache<Object> appResourceCache;
  protected DeploymentCache<Object> knowledgeBaseCache; // Needs to be object to avoid an import to Drools in this core class
  protected List<Deployer> deployers;
  
  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected ProcessDefinitionEntityManager processDefinitionEntityManager;
  protected DeploymentEntityManager deploymentEntityManager;

  public void deploy(DeploymentEntity deployment) {
    deploy(deployment, null);
  }

  public void deploy(DeploymentEntity deployment, Map<String, Object> deploymentSettings) {
    for (Deployer deployer : deployers) {
      deployer.deploy(deployment, deploymentSettings);
    }
  }

  public ProcessDefinition findDeployedProcessDefinitionById(String processDefinitionId) {
    if (processDefinitionId == null) {
      throw new ActivitiIllegalArgumentException("Invalid process definition id : null");
    }

    // first try the cache
    ProcessDefinitionCacheEntry cacheEntry = processDefinitionCache.get(processDefinitionId);
    ProcessDefinition processDefinition = cacheEntry != null ? cacheEntry.getProcessDefinition() : null;

    if (processDefinition == null) {
      processDefinition = processDefinitionEntityManager.findById(processDefinitionId);
      if (processDefinition == null) {
        throw new ActivitiObjectNotFoundException("no deployed process definition found with id '" + processDefinitionId + "'", ProcessDefinition.class);
      }
      processDefinition = resolveProcessDefinition(processDefinition).getProcessDefinition();
    }
    return processDefinition;
  }

  public ProcessDefinition findDeployedLatestProcessDefinitionByKey(String processDefinitionKey) {
    ProcessDefinition processDefinition = processDefinitionEntityManager.findLatestProcessDefinitionByKey(processDefinitionKey);

    if (processDefinition == null) {
      throw new ActivitiObjectNotFoundException("no processes deployed with key '" + processDefinitionKey + "'", ProcessDefinition.class);
    }
    processDefinition = resolveProcessDefinition(processDefinition).getProcessDefinition();
    return processDefinition;
  }

  public ProcessDefinition findDeployedLatestProcessDefinitionByKeyAndTenantId(String processDefinitionKey, String tenantId) {
    ProcessDefinition processDefinition = processDefinitionEntityManager.findLatestProcessDefinitionByKeyAndTenantId(processDefinitionKey, tenantId);
    if (processDefinition == null) {
      throw new ActivitiObjectNotFoundException("no processes deployed with key '" + processDefinitionKey + "' for tenant identifier '" + tenantId + "'", ProcessDefinition.class);
    }
    processDefinition = resolveProcessDefinition(processDefinition).getProcessDefinition();
    return processDefinition;
  }

  public ProcessDefinition findDeployedProcessDefinitionByKeyAndVersionAndTenantId(String processDefinitionKey, Integer processDefinitionVersion, String tenantId) {
    ProcessDefinition processDefinition = (ProcessDefinitionEntity) processDefinitionEntityManager
        .findProcessDefinitionByKeyAndVersionAndTenantId(processDefinitionKey, processDefinitionVersion, tenantId);
    if (processDefinition == null) {
      throw new ActivitiObjectNotFoundException("no processes deployed with key = '" + processDefinitionKey + "' and version = '" + processDefinitionVersion + "'", ProcessDefinition.class);
    }
    processDefinition = resolveProcessDefinition(processDefinition).getProcessDefinition();
    return processDefinition;
  }

  /**
   * Resolving the process definition will fetch the BPMN 2.0, parse it and store the {@link BpmnModel} in memory.
   */
  public ProcessDefinitionCacheEntry resolveProcessDefinition(ProcessDefinition processDefinition) {
    String processDefinitionId = processDefinition.getId();
    String deploymentId = processDefinition.getDeploymentId();

    ProcessDefinitionCacheEntry cachedProcessDefinition = processDefinitionCache.get(processDefinitionId);

    if (cachedProcessDefinition == null) {
      CommandContext commandContext = Context.getCommandContext();
      if (commandContext.getProcessEngineConfiguration().isActiviti5CompatibilityEnabled() && 
          Activiti5Util.isActiviti5ProcessDefinition(Context.getCommandContext(), processDefinition)) {
        
        return Activiti5Util.getActiviti5CompatibilityHandler().resolveProcessDefinition(processDefinition);
      }
      
      DeploymentEntity deployment = deploymentEntityManager.findById(deploymentId);
      deployment.setNew(false);
      deploy(deployment, null);
      cachedProcessDefinition = processDefinitionCache.get(processDefinitionId);

      if (cachedProcessDefinition == null) {
        throw new ActivitiException("deployment '" + deploymentId + "' didn't put process definition '" + processDefinitionId + "' in the cache");
      }
    }
    return cachedProcessDefinition;
  }
  
  public Object getAppResourceObject(String deploymentId) {
    Object appResourceObject = appResourceCache.get(deploymentId);
    
    if (appResourceObject == null) {
      boolean appResourcePresent = false;
      List<String> deploymentResourceNames = getDeploymentEntityManager().getDeploymentResourceNames(deploymentId);
      for (String deploymentResourceName : deploymentResourceNames) {
        if (deploymentResourceName.endsWith(".app")) {
          appResourcePresent = true;
          break;
        }
      }
      
      if (appResourcePresent) {
        DeploymentEntity deployment = deploymentEntityManager.findById(deploymentId);
        deployment.setNew(false);
        deploy(deployment, null);
      
      } else {
        throw new ActivitiException("No .app resource found for deployment '" + deploymentId + "'");
      }
      
      appResourceObject = appResourceCache.get(deploymentId);
      if (appResourceObject == null) {
        throw new ActivitiException("deployment '" + deploymentId + "' didn't put an app resource in the cache");
      }
    }
    
    return appResourceObject;
  }
  
  public AppModel getAppResourceModel(String deploymentId) {
    Object appResourceObject = getAppResourceObject(deploymentId);
    if (appResourceObject instanceof AppModel == false) {
      throw new ActivitiException("App resource is not of type AppModel");
    }
    
    return (AppModel) appResourceObject;
  }

  public void removeDeployment(String deploymentId, boolean cascade) {

    DeploymentEntity deployment = deploymentEntityManager.findById(deploymentId);
    if (deployment == null) {
      throw new ActivitiObjectNotFoundException("Could not find a deployment with id '" + deploymentId + "'.", DeploymentEntity.class);
    }

    if (processEngineConfiguration.isActiviti5CompatibilityEnabled() && 
        Activiti5CompatibilityHandler.ACTIVITI_5_ENGINE_TAG.equals(deployment.getEngineVersion())) {
      
      processEngineConfiguration.getActiviti5CompatibilityHandler().deleteDeployment(deploymentId, cascade);
      return;
    }

    // Remove any process definition from the cache
    List<ProcessDefinition> processDefinitions = new ProcessDefinitionQueryImpl().deploymentId(deploymentId).list();
    ActivitiEventDispatcher eventDispatcher = Context.getProcessEngineConfiguration().getEventDispatcher();

    for (ProcessDefinition processDefinition : processDefinitions) {

      // Since all process definitions are deleted by a single query, we should dispatch the events in this loop
      if (eventDispatcher.isEnabled()) {
        eventDispatcher.dispatchEvent(ActivitiEventBuilder.createEntityEvent(ActivitiEngineEventType.ENTITY_DELETED, processDefinition));
      }
    }

    // Delete data
    deploymentEntityManager.deleteDeployment(deploymentId, cascade);

    // Since we use a delete by query, delete-events are not automatically dispatched
    if (eventDispatcher.isEnabled()) {
      eventDispatcher.dispatchEvent(ActivitiEventBuilder.createEntityEvent(ActivitiEngineEventType.ENTITY_DELETED, deployment));
    }

    for (ProcessDefinition processDefinition : processDefinitions) {
      processDefinitionCache.remove(processDefinition.getId());
      processDefinitionInfoCache.remove(processDefinition.getId());
    }
    
    appResourceCache.remove(deploymentId);
    knowledgeBaseCache.remove(deploymentId);
  }

  // getters and setters
  // //////////////////////////////////////////////////////

  public List<Deployer> getDeployers() {
    return deployers;
  }

  public void setDeployers(List<Deployer> deployers) {
    this.deployers = deployers;
  }

  public DeploymentCache<ProcessDefinitionCacheEntry> getProcessDefinitionCache() {
    return processDefinitionCache;
  }

  public void setProcessDefinitionCache(DeploymentCache<ProcessDefinitionCacheEntry> processDefinitionCache) {
    this.processDefinitionCache = processDefinitionCache;
  }
  
  public ProcessDefinitionInfoCache getProcessDefinitionInfoCache() {
    return processDefinitionInfoCache;
  }

  public void setProcessDefinitionInfoCache(ProcessDefinitionInfoCache processDefinitionInfoCache) {
    this.processDefinitionInfoCache = processDefinitionInfoCache;
  }

  public DeploymentCache<Object> getKnowledgeBaseCache() {
    return knowledgeBaseCache;
  }

  public void setKnowledgeBaseCache(DeploymentCache<Object> knowledgeBaseCache) {
    this.knowledgeBaseCache = knowledgeBaseCache;
  }
  
  public DeploymentCache<Object> getAppResourceCache() {
    return appResourceCache;
  }

  public void setAppResourceCache(DeploymentCache<Object> appResourceCache) {
    this.appResourceCache = appResourceCache;
  }
  
  public ProcessEngineConfigurationImpl getProcessEngineConfiguration() {
    return processEngineConfiguration;
  }

  public void setProcessEngineConfiguration(ProcessEngineConfigurationImpl processEngineConfiguration) {
    this.processEngineConfiguration = processEngineConfiguration;
  }

  public ProcessDefinitionEntityManager getProcessDefinitionEntityManager() {
    return processDefinitionEntityManager;
  }

  public void setProcessDefinitionEntityManager(ProcessDefinitionEntityManager processDefinitionEntityManager) {
    this.processDefinitionEntityManager = processDefinitionEntityManager;
  }

  public DeploymentEntityManager getDeploymentEntityManager() {
    return deploymentEntityManager;
  }

  public void setDeploymentEntityManager(DeploymentEntityManager deploymentEntityManager) {
    this.deploymentEntityManager = deploymentEntityManager;
  }
  
}
