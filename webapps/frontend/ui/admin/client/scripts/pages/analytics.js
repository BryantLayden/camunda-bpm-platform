/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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

'use strict';

var template = require('./analytics.html')();

var Controller = [
  '$scope',
  '$translate',
  'camAPI',
  'Notifications',
  function($scope, $translate, camAPI, Notifications) {
    $scope.form = {enableUsage: false};
    var telemetryResource = camAPI.resource('telemetry');

    telemetryResource.get(function(err, res) {
      if (err) {
        const msg = err.message;
        Notifications.addError({
          status: $translate.instant('ERROR'),
          message: $translate.instant(
            'TELEMETRY_FETCH_CONFIGURATION_ERROR_MESSAGE',
            {
              msg
            }
          )
        });
      } else {
        $scope.authorized = true;
        $scope.form.enableUsage = res.enableTelemetry;
      }
    });

    $scope.submit = function() {
      telemetryResource.configure(!!$scope.form.enableUsage, function(err) {
        if (!err) {
          Notifications.addMessage({
            status: $translate.instant('TELEMETRY_SUCCESS_HEADING'),
            message: $translate.instant('TELEMETRY_SUCCESS')
          });
        }
      });
    };
  }
];

module.exports = [
  'ViewsProvider',
  function PluginConfiguration(ViewsProvider) {
    ViewsProvider.registerDefaultView('admin.system', {
      id: 'analytics-settings-general',
      label: 'TELEMETRY_SETTINGS',
      template: template,
      controller: Controller,
      priority: 950
    });
  }
];
