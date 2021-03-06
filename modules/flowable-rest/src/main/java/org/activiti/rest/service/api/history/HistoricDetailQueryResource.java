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

package org.activiti.rest.service.api.history;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import io.swagger.annotations.*;
import org.activiti.rest.api.DataResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Tijs Rademakers
 */
@RestController
@Api(tags = { "History" }, description = "Manage History")
public class HistoricDetailQueryResource extends HistoricDetailBaseResource {

  @ApiOperation(value = "Query for historic details", tags = { "History" }, notes = "All supported JSON parameter fields allowed are exactly the same as the parameters found for getting a collection of historic process instances, but passed in as JSON-body arguments rather than URL-parameters to allow for more advanced querying and preventing errors with request-uri’s that are too long.")
  @ApiResponses(value = {
          @ApiResponse(code = 200, message = "Indicates request was successful and the historic details are returned"),
          @ApiResponse(code = 400, message = "Indicates an parameter was passed in the wrong format. The status-message contains additional information.") })
  @RequestMapping(value = "/query/historic-detail", method = RequestMethod.POST, produces = "application/json")
  public DataResponse queryHistoricDetail(@RequestBody HistoricDetailQueryRequest queryRequest, @ApiParam(hidden = true) @RequestParam Map<String, String> allRequestParams, HttpServletRequest request) {

    return getQueryResponse(queryRequest, allRequestParams);
  }
}
