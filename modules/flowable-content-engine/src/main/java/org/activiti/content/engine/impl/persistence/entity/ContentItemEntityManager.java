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
package org.activiti.content.engine.impl.persistence.entity;

import java.util.List;

import org.activiti.content.api.ContentItem;
import org.activiti.content.engine.impl.ContentItemQueryImpl;
import org.activiti.engine.impl.Page;
import org.activiti.engine.impl.persistence.entity.EntityManager;

/**
 * @author Joram Barrez
 */
public interface ContentItemEntityManager extends EntityManager<ContentItemEntity> {

  List<ContentItem> findContentItemsByQueryCriteria(ContentItemQueryImpl formInstanceQuery, Page page);

  long findContentItemCountByQueryCriteria(ContentItemQueryImpl contentItemQuery);
}